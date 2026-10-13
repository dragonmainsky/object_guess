import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * GameServer: authoritative LAN server for "Guess What It Looks Like?".
 *
 * The server owns the room: it holds the player list, the game state and every score.
 * Clients only send requests; nothing is trusted from a client. All shared state is
 * guarded by a single monitor so the accept thread, the per-client reader threads and
 * the round timer can never corrupt it.
 *
 * Wire format: every message is a {@link Message} written with DataOutputStream as
 *   writeUTF(type), writeInt(argCount), writeUTF(arg) * argCount
 * and read back the exact same way with DataInputStream. Because each field carries
 * its own length prefix, message boundaries can never bleed into each other on the TCP
 * stream. Server and client share this class, so there is a single source of truth.
 *
 * Threading: the accept loop runs on its own thread, every accepted socket gets its own
 * reader thread, and each round owns one {@link RoundTimerThread}. A round timer is
 * always stopped before a new one starts, so timers can never stack up.
 *
 * Two flags describe the game:
 *   tournamentActive - a whole tournament (totalRounds = players * 2) is under way.
 *                      Nobody may join for its entire duration.
 *   roundRunning     - one round inside that tournament is being played right now.
 */
public class GameServer {

    // ===== Configuration =====
    public static final int DEFAULT_PORT = 5000;
    public static final int MIN_PLAYERS = 2;      // host + 1 other keeps LAN testing possible
    public static final int MIN_SHAPES = 1;       // each round draws a secret shape count...
    public static final int MAX_SHAPES = 5;       // ...randomly between these two values, inclusive
    public static final int ROUND_SECONDS = 30;         // guessing window, opens when the secret is locked
    public static final int BONUS_WINDOW_SECONDS = 10;  // "+2 when every shape is right within 10 sec"
    public static final int INTERMISSION_SECONDS = 10;  // round-result countdown before the next round
    public static final int MAX_NAME_LENGTH = 16;
    /** Rounds in one tournament: every player is the questioner exactly twice. */
    public static final int ROUNDS_PER_PLAYER = 2;

    /** The eight shapes the game knows about. Names must match the files in shape/. */
    public static final String[] SHAPE_NAMES = {
        "Circle", "Triangle", "Rectangle", "Oval", "Hexagon", "Diamond", "Star", "Square"
    };

    // ===== Protocol message types (shared by server and client) =====
    public static final String JOIN = "JOIN";
    public static final String JOIN_SUCCESS = "JOIN_SUCCESS";
    public static final String JOIN_FAILED = "JOIN_FAILED";
    public static final String PLAYER_LIST = "PLAYER_LIST";
    public static final String READY = "READY";
    public static final String START_GAME = "START_GAME";
    public static final String ROUND_INFO = "ROUND_INFO";
    public static final String TICK = "TICK";
    public static final String SECRET = "SECRET";
    public static final String SECRET_LOCKED = "SECRET_LOCKED";
    public static final String GUESS = "GUESS";
    public static final String GUESS_ACCEPTED = "GUESS_ACCEPTED";
    public static final String GUESS_SUBMITTED = "GUESS_SUBMITTED";
    public static final String ROUND_RESULT = "ROUND_RESULT";
    public static final String SCORE_UPDATE = "SCORE_UPDATE";
    public static final String NEW_GAME = "NEW_GAME";
    public static final String LEAVE_ROOM = "LEAVE";
    public static final String TO_LOBBY = "TO_LOBBY";
    public static final String SHOW_SCOREBOARD = "SHOW_SCOREBOARD";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String CHAT = "CHAT";
    public static final String SYSTEM = "SYSTEM";
    public static final String SERVER_CLOSED = "SERVER_CLOSED";
    public static final String ERROR = "ERROR";

    /**
     * One protocol message: a type plus zero or more plain text fields.
     * Serialization is length-prefixed so no delimiter can ever clash with player data.
     */
    public static class Message {
        public final String type;
        public final String[] args;

        public Message(String type, String... args) {
            this.type = type;
            this.args = (args == null) ? new String[0] : args;
        }

        public String arg(int index) {
            if (index < 0 || index >= args.length) {
                return "";
            }
            return args[index];
        }

        public int intArg(int index, int fallback) {
            try {
                return Integer.parseInt(arg(index));
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        public void writeTo(DataOutputStream out) throws IOException {
            out.writeUTF(type);
            out.writeInt(args.length);
            for (int i = 0; i < args.length; i++) {
                String value = args[i];
                out.writeUTF(value == null ? "" : value);
            }
            out.flush();
        }

        public static Message readFrom(DataInputStream in) throws IOException {
            String type = in.readUTF();
            int count = in.readInt();
            if (count < 0 || count > 1024) {
                throw new IOException("Malformed message: bad field count " + count);
            }
            String[] args = new String[count];
            for (int i = 0; i < count; i++) {
                args[i] = in.readUTF();
            }
            return new Message(type, args);
        }
    }

    // ===== Server wiring =====
    private final int port;
    private ServerSocket serverSocket;
    private volatile boolean running;
    private Thread acceptThread;

    /** Single monitor guarding every mutable field below. */
    private final Object lock = new Object();
    private final List<ClientHandler> clients = new ArrayList<>();   // join order; index 0 is the host
    private final Map<String, Boolean> ready = new LinkedHashMap<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Map<String, Integer> deltas = new LinkedHashMap<>();
    private final Map<String, String[]> guesses = new LinkedHashMap<>();
    private final Map<String, Long> guessTimes = new LinkedHashMap<>();
    private final Map<String, Integer> correctCounts = new LinkedHashMap<>();
    private final Map<String, Boolean> bonusFlags = new LinkedHashMap<>();

    private int round;                    // last started round, 0 = no round yet
    private int totalRounds;              // players * 2, locked in when the tournament starts
    private String questioner;
    private String[] secret;
    private boolean secretLocked;
    private int expectedShapes;           // random 1..MAX_SHAPES, drawn at the start of every round
    private boolean tournamentActive;     // whole tournament in progress (blocks new joins)
    private boolean roundRunning;         // one round in progress
    private boolean roundEnded;           // guards against ending the same round twice
    private long roundStartMillis;
    private RoundTimerThread roundTimerThread;
    private Thread intermissionThread;    // sleeps INTERMISSION_SECONDS, then starts the next round
    private final Random random = new Random();

    public GameServer() {
        this(DEFAULT_PORT);
    }

    public GameServer(int port) {
        this.port = port;
    }

    // =====================================================================
    //  Lifecycle
    // =====================================================================

    /** Binds the port and starts the accept loop. Throws IOException when the port is busy. */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        }, "GameServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    /** Closes the room: tells every client, then releases the port and all threads. */
    public void shutdown() {
        if (!running) {
            return;
        }
        running = false;

        stopRoundTimer();
        stopIntermission();

        broadcast(SERVER_CLOSED);

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // port already gone
        }

        List<ClientHandler> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(clients);
            clients.clear();
        }
        for (ClientHandler client : snapshot) {
            client.closeQuietly();
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            } catch (IOException e) {
                if (running) {
                    System.out.println("[server] accept failed: " + e.getMessage());
                }
            }
        }
    }

    // =====================================================================
    //  Broadcasting (snapshot under lock, send outside it)
    // =====================================================================

    private void broadcast(String type, String... args) {
        broadcast(new Message(type, args));
    }

    private void broadcast(Message message) {
        List<ClientHandler> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(clients);
        }
        for (ClientHandler client : snapshot) {
            client.send(message);
        }
    }

    private void broadcastPlayerList() {
        String[] payload;
        List<ClientHandler> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(clients);
            payload = buildPlayerListPayload();
        }
        Message message = new Message(PLAYER_LIST, payload);
        for (ClientHandler client : snapshot) {
            client.send(message);
        }
    }

    /** [count, name, ready, host] repeated per player. Caller must hold the lock. */
    private String[] buildPlayerListPayload() {
        List<String> out = new ArrayList<>();
        out.add(String.valueOf(clients.size()));
        String hostName = hostNameLocked();
        for (ClientHandler client : clients) {
            out.add(client.playerName);
            out.add(Boolean.TRUE.equals(ready.get(client.playerName)) ? "1" : "0");
            out.add(client.playerName.equals(hostName) ? "1" : "0");
        }
        return out.toArray(new String[0]);
    }

    /** Caller must hold the lock. */
    private String hostNameLocked() {
        if (clients.isEmpty()) {
            return "";
        }
        return clients.get(0).playerName;
    }

    private String hostName() {
        synchronized (lock) {
            return hostNameLocked();
        }
    }

    // =====================================================================
    //  Join / leave
    // =====================================================================

    private ClientHandler findByNameLocked(String name) {
        for (ClientHandler client : clients) {
            if (client.playerName != null && client.playerName.equalsIgnoreCase(name)) {
                return client;
            }
        }
        return null;
    }

    /** True when the name is one of the eight known shapes. */
    public static boolean isValidShapeName(String name) {
        if (name == null) {
            return false;
        }
        for (String shape : SHAPE_NAMES) {
            if (shape.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Returns a canonical-cased copy of the shape names, or null when any name is unknown or repeated. */
    private static String[] normalizeShapes(String[] raw, int expectedCount) {
        if (raw == null || raw.length != expectedCount) {
            return null;
        }
        String[] canonical = new String[raw.length];
        for (int i = 0; i < raw.length; i++) {
            String match = null;
            for (String shape : SHAPE_NAMES) {
                if (shape.equalsIgnoreCase(raw[i])) {
                    match = shape;
                    break;
                }
            }
            if (match == null) {
                return null; // unknown shape name
            }
            for (int j = 0; j < i; j++) {
                if (canonical[j].equals(match)) {
                    return null; // duplicate shape
                }
            }
            canonical[i] = match;
        }
        return canonical;
    }

    private void handleJoin(ClientHandler handler, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            handler.send(JOIN_FAILED, "Player name cannot be empty.");
            handler.closeQuietly();
            return;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }

        synchronized (lock) {
            if (findByNameLocked(name) != null) {
                handler.send(JOIN_FAILED, "That name is already taken in this room.");
                handler.closeQuietly();
                return;
            }
            if (tournamentActive) {
                // Rounds are only paused between games, so the whole tournament blocks new players.
                handler.send(JOIN_FAILED, "A tournament is in progress. Please wait until it finishes.");
                handler.closeQuietly();
                return;
            }
            handler.playerName = name;
            handler.joined = true;
            clients.add(handler);
            ready.put(name, Boolean.FALSE);
            scores.put(name, Integer.valueOf(scores.containsKey(name) ? scores.get(name).intValue() : 0));
        }

        boolean isHost;
        synchronized (lock) {
            isHost = !clients.isEmpty() && clients.get(0) == handler;
        }
        handler.send(JOIN_SUCCESS, name, isHost ? "1" : "0");
        broadcastPlayerList();
        broadcast(SYSTEM, name + " joined the room.");
    }

    private void handleDisconnect(ClientHandler handler) {
        boolean wasJoined;
        boolean wasHost;
        String name;
        synchronized (lock) {
            wasJoined = handler.joined && handler.playerName != null;
            name = handler.playerName;
            wasHost = wasJoined && !clients.isEmpty() && clients.get(0) == handler;
            if (wasJoined) {
                clients.remove(handler);
                ready.remove(name);
                guesses.remove(name);
                guessTimes.remove(name);
                correctCounts.remove(name);
                bonusFlags.remove(name);
            }
            handler.joined = false;
        }
        handler.closeQuietly();

        if (!wasJoined) {
            return;
        }

        if (wasHost) {
            broadcast(SYSTEM, name + " (host) closed the room.");
            shutdown();
            return;
        }

        boolean finishTournament = false;
        boolean restartRound = false;
        boolean endRoundNow = false;
        synchronized (lock) {
            if (tournamentActive) {
                if (clients.size() < MIN_PLAYERS) {
                    finishTournament = true;
                } else if (roundRunning) {
                    if (name != null && name.equals(questioner)) {
                        // Skip the questioner who left: replay this round with the next player.
                        restartRound = true;
                    } else if (countGuessersLocked() == 0) {
                        finishTournament = true;
                    } else if (countSubmittedLocked() >= countGuessersLocked()) {
                        endRoundNow = true;
                    }
                }
            }
        }

        broadcast(PLAYER_LEFT, name);
        broadcast(SYSTEM, name + " left the room.");
        broadcastPlayerList();

        if (finishTournament) {
            finishTournament("A player left the room.");
        } else if (restartRound) {
            restartRoundAfterQuestionerLeft();
        } else if (endRoundNow) {
            endRound("all");
        }
    }

    // =====================================================================
    //  Game flow
    // =====================================================================

    private void handleStartGame(ClientHandler requester) {
        boolean newTournament;
        synchronized (lock) {
            if (roundRunning) {
                requester.send(ERROR, "A round is already in progress.");
                return;
            }
            if (clients.isEmpty() || clients.get(0) != requester) {
                requester.send(ERROR, "Only the host can start the game.");
                return;
            }
            if (clients.size() < MIN_PLAYERS) {
                requester.send(ERROR, "Need at least " + MIN_PLAYERS + " players to start.");
                return;
            }
            if (!othersReadyLocked(requester)) {
                requester.send(ERROR, "All other players must press READY before you can start.");
                return;
            }
            // Inside a running tournament this just starts the next round; otherwise it is a new game.
            newTournament = !tournamentActive;
            if (newTournament) {
                totalRounds = clients.size() * ROUNDS_PER_PLAYER;
                resetScoresLocked();
                tournamentActive = true;
                round = 0;
            }
        }
        if (newTournament) {
            broadcast(SYSTEM, "The tournament is starting! " + totalRounds + " rounds.");
        }
        nextRound();
    }

    /**
     * NEW_GAME is now only the "start a fresh tournament" request from the final scoreboard.
     * Between rounds nothing asks for the lobby - the server advances by itself.
     */
    private void handleNewGame(ClientHandler requester) {
        synchronized (lock) {
            if (clients.isEmpty() || clients.get(0) != requester) {
                requester.send(ERROR, "Only the host can start a new game.");
                return;
            }
            if (tournamentActive) {
                requester.send(ERROR, "A tournament is in progress.");
                return;
            }
            if (roundRunning) {
                requester.send(ERROR, "A round is in progress.");
                return;
            }
            resetScoresLocked();
            for (ClientHandler client : clients) {
                ready.put(client.playerName, Boolean.FALSE);
            }
        }
        stopIntermission();
        stopRoundTimer();
        broadcast(TO_LOBBY, hostName(), "");
        broadcastPlayerList();
    }

    /** Every non-host player must be ready. The host starts the round, so the host need not be ready. */
    private boolean othersReadyLocked(ClientHandler host) {
        for (ClientHandler client : clients) {
            if (client == host) {
                continue;
            }
            if (!Boolean.TRUE.equals(ready.get(client.playerName))) {
                return false;
            }
        }
        return true;
    }

    private void resetScoresLocked() {
        round = 0;
        for (ClientHandler client : clients) {
            scores.put(client.playerName, Integer.valueOf(0));
            deltas.put(client.playerName, Integer.valueOf(0));
        }
        questioner = null;
        secret = null;
        secretLocked = false;
        expectedShapes = 0;
        guesses.clear();
        guessTimes.clear();
        correctCounts.clear();
        bonusFlags.clear();
    }

    private void nextRound() {
        stopIntermission();
        stopRoundTimer();
        String selectedQuestioner;
        int currentRound;
        int lockedTotal;
        int shapesThisRound;
        synchronized (lock) {
            if (clients.isEmpty()) {
                tournamentActive = false;
                roundRunning = false;
                return;
            }
            round++;
            roundEnded = false;
            roundRunning = true;
            secret = null;
            secretLocked = false;
            // The secret count is random every round. It is fixed for the whole round so the
            // questioner and every guesser must end up with exactly this many shapes.
            expectedShapes = MIN_SHAPES + random.nextInt(MAX_SHAPES - MIN_SHAPES + 1);
            guesses.clear();
            guessTimes.clear();
            deltas.clear();
            correctCounts.clear();
            bonusFlags.clear();
            // roundStartMillis is stamped when the secret is locked: that is when guessing begins.

            // With totalRounds = players * 2, index (round-1) % players hands every player
            // exactly two turns as the questioner before the tournament ends.
            int index = (round - 1) % clients.size();
            questioner = clients.get(index).playerName;
            selectedQuestioner = questioner;
            currentRound = round;
            lockedTotal = totalRounds;
            shapesThisRound = expectedShapes;

            for (ClientHandler client : clients) {
                ready.put(client.playerName, Boolean.FALSE);
            }
        }
        // The shape count is announced now, but no timer runs until the questioner locks in the
        // secret, so the guessers only see "waiting for the questioner" at this point.
        broadcast(ROUND_INFO,
                String.valueOf(currentRound),
                String.valueOf(lockedTotal),
                selectedQuestioner,
                String.valueOf(shapesThisRound));
    }

    /** The questioner left with enough players still in: replay the round with the next questioner. */
    private void restartRoundAfterQuestionerLeft() {
        boolean restart;
        synchronized (lock) {
            restart = tournamentActive && !clients.isEmpty() && clients.size() >= MIN_PLAYERS;
            if (restart) {
                round--; // the abandoned round is not consumed
                roundRunning = false;
            }
        }
        if (!restart) {
            return;
        }
        broadcast(SYSTEM, "The questioner left. Restarting the round with a new questioner.");
        nextRound();
    }

    // =====================================================================
    //  Round timer (one daemon thread per round)
    // =====================================================================

    /**
     * Counts a round down to zero and broadcasts TICK(round, remaining) every second,
     * immediately at the start and again on every whole second. The deadline comes from
     * System.nanoTime() so the interval cannot drift, and stop() makes the thread exit
     * deterministically when the round ends, is aborted, or the server shuts down.
     */
    private class RoundTimerThread extends Thread {
        private final int roundNumber;
        private final long deadlineNanos;
        private volatile boolean stopped;

        RoundTimerThread(int roundNumber, int seconds) {
            super("GameServer-RoundTimer");
            setDaemon(true);
            this.roundNumber = roundNumber;
            this.deadlineNanos = System.nanoTime() + seconds * 1_000_000_000L;
        }

        void requestStop() {
            stopped = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!stopped) {
                int remaining = remainingSeconds();
                broadcast(TICK, String.valueOf(roundNumber), String.valueOf(remaining));
                if (remaining <= 0) {
                    endRound("time");
                    return;
                }
                long sleepMillis = millisToNextWholeSecond();
                if (sleepMillis <= 0) {
                    sleepMillis = 50;
                }
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // stopped by a new round, endRound or shutdown
                }
            }
        }

        private int remainingSeconds() {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0) {
                return 0;
            }
            return (int) ((nanos + 999_999_999L) / 1_000_000_000L);
        }

        /** Milliseconds until the displayed second changes, so ticks land on whole seconds. */
        private long millisToNextWholeSecond() {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0) {
                return 0;
            }
            long remainder = nanos % 1_000_000_000L;
            if (remainder == 0) {
                return 1000;
            }
            return remainder / 1_000_000L;
        }
    }

    private void startRoundTimer(int roundNumber, int seconds) {
        stopRoundTimer();
        RoundTimerThread timer = new RoundTimerThread(roundNumber, seconds);
        synchronized (lock) {
            roundTimerThread = timer;
        }
        timer.start();
    }

    /** Stops the running timer and waits briefly for it to die, so timers never stack up. */
    private void stopRoundTimer() {
        RoundTimerThread timer;
        synchronized (lock) {
            timer = roundTimerThread;
            roundTimerThread = null;
        }
        if (timer == null) {
            return;
        }
        timer.requestStop();
        if (timer == Thread.currentThread()) {
            return; // called from the timer itself (endRound): nothing to join
        }
        try {
            timer.join(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =====================================================================
    //  Round actions
    // =====================================================================

    private void handleSecret(ClientHandler sender, String[] shapes) {
        String[] canonical;
        int count;
        int currentRound;
        synchronized (lock) {
            if (!roundRunning) {
                sender.send(ERROR, "No round is in progress.");
                return;
            }
            if (!sender.playerName.equals(questioner)) {
                sender.send(ERROR, "Only the questioner can set the secret shapes.");
                return;
            }
            if (secretLocked) {
                sender.send(ERROR, "The secret shapes are already locked for this round.");
                return;
            }
            if (shapes.length != expectedShapes) {
                sender.send(ERROR, "Pick exactly " + expectedShapes + " shape"
                        + (expectedShapes == 1 ? "" : "s") + " for this round.");
                return;
            }
            canonical = normalizeShapes(shapes, shapes.length);
            if (canonical == null) {
                sender.send(ERROR, "Those shapes are not valid (unknown or repeated).");
                return;
            }
            secret = canonical;
            secretLocked = true;
            count = canonical.length;
            currentRound = round;
            // The guessers' clock opens here, not at ROUND_INFO: submitting the secret is what starts it.
            roundStartMillis = System.currentTimeMillis();
        }
        broadcast(SECRET_LOCKED, questioner, String.valueOf(count));
        broadcast(SYSTEM, "The questioner locked in " + count + " shape" + (count == 1 ? "" : "s")
                + ". Start guessing!");
        // Now the server starts the shared countdown and sends the first TICK to everyone.
        startRoundTimer(currentRound, ROUND_SECONDS);
    }

    private void handleGuess(ClientHandler sender, String[] shapes) {
        int submitted;
        int totalGuessers;
        String[] canonical;
        synchronized (lock) {
            if (!roundRunning) {
                sender.send(ERROR, "No round is in progress.");
                return;
            }
            if (!secretLocked || secret == null) {
                sender.send(ERROR, "Wait for the questioner to lock the secret shapes first.");
                return;
            }
            if (sender.playerName.equals(questioner)) {
                sender.send(ERROR, "The questioner cannot submit a guess.");
                return;
            }
            if (guesses.containsKey(sender.playerName)) {
                sender.send(ERROR, "You already submitted a guess this round.");
                return;
            }
            if (shapes.length != secret.length) {
                sender.send(ERROR, "Pick exactly " + secret.length + " shape"
                        + (secret.length == 1 ? "" : "s") + " for this round.");
                return;
            }
            canonical = normalizeShapes(shapes, secret.length);
            if (canonical == null) {
                sender.send(ERROR, "Those shapes are not valid (unknown or repeated).");
                return;
            }
            guesses.put(sender.playerName, canonical);
            guessTimes.put(sender.playerName, Long.valueOf(System.currentTimeMillis()));
            submitted = countSubmittedLocked();
            totalGuessers = countGuessersLocked();
        }
        sender.send(GUESS_ACCEPTED);
        broadcast(GUESS_SUBMITTED, sender.playerName, String.valueOf(submitted), String.valueOf(totalGuessers));
        if (submitted >= totalGuessers) {
            endRound("all");
        }
    }

    private int countGuessersLocked() {
        int count = 0;
        for (ClientHandler client : clients) {
            if (!client.playerName.equals(questioner)) {
                count++;
            }
        }
        return count;
    }

    private int countSubmittedLocked() {
        int count = 0;
        for (String name : guesses.keySet()) {
            if (!name.equals(questioner)) {
                count++;
            }
        }
        return count;
    }

    private void endRound(String reason) {
        String[] payload;
        boolean isFinal;
        synchronized (lock) {
            if (!roundRunning || roundEnded) {
                return;
            }
            roundEnded = true;
            roundRunning = false;
            computeScoresLocked();
            isFinal = round >= totalRounds;
            if (isFinal) {
                tournamentActive = false;
            }
            payload = buildRoundResultPayload(round, isFinal, reason);
        }
        stopRoundTimer();
        broadcast(ROUND_RESULT, payload);
        if (isFinal) {
            broadcast(SYSTEM, "That was the last round. Final results are ready.");
        }
        // The server - not the clients - owns what happens next: after a short countdown it
        // either starts the next round or opens the final scoreboard, so everyone stays in step.
        startIntermission(isFinal);
    }

    /** Ends the tournament early (too few players left) and shows the final scoreboard. */
    private void finishTournament(String reason) {
        String[] payload;
        synchronized (lock) {
            if (!tournamentActive) {
                return;
            }
            roundEnded = true;
            roundRunning = false;
            tournamentActive = false;
            computeScoresLocked();
            payload = buildRoundResultPayload(round, true, reason);
        }
        stopRoundTimer();
        broadcast(ROUND_RESULT, payload);
        startIntermission(true);
    }

    // =====================================================================
    //  Intermission (the gap between rounds)
    // =====================================================================

    /**
     * Waits INTERMISSION_SECONDS so every client's round-result countdown runs together, then
     * either starts the next round or tells everyone to open the final scoreboard. The lobby
     * is never re-entered in the middle of a tournament.
     */
    private void startIntermission(final boolean isFinal) {
        stopIntermission();
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(INTERMISSION_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return; // a new round or a shutdown replaced this intermission
                }
                if (!running) {
                    return;
                }
                if (isFinal) {
                    broadcast(SHOW_SCOREBOARD);
                } else {
                    nextRound();
                }
            }
        }, "GameServer-Intermission");
        thread.setDaemon(true);
        synchronized (lock) {
            intermissionThread = thread;
        }
        thread.start();
    }

    /** Stops a pending intermission so it cannot start a round that is no longer wanted. */
    private void stopIntermission() {
        Thread thread;
        synchronized (lock) {
            thread = intermissionThread;
            intermissionThread = null;
        }
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        thread.interrupt();
    }

    /** Server-side scoring: the only place a score is ever produced. Caller must hold the lock. */
    private void computeScoresLocked() {
        deltas.clear();
        correctCounts.clear();
        bonusFlags.clear();

        int expected = (secret == null) ? 0 : secret.length;
        if (expected == 0) {
            for (ClientHandler client : clients) {
                deltas.put(client.playerName, Integer.valueOf(0));
                correctCounts.put(client.playerName, Integer.valueOf(0));
                bonusFlags.put(client.playerName, Boolean.FALSE);
            }
            return;
        }

        for (ClientHandler client : clients) {
            String name = client.playerName;
            if (name.equals(questioner)) {
                continue;
            }
            String[] guess = guesses.get(name);
            if (guess == null) {
                deltas.put(name, Integer.valueOf(0));
                correctCounts.put(name, Integer.valueOf(0));
                bonusFlags.put(name, Boolean.FALSE);
                continue;
            }
            int correct = countMatching(secret, guess);
            int delta = correct;
            Long submittedAt = guessTimes.get(name);
            long elapsedSeconds = 0;
            if (submittedAt != null) {
                elapsedSeconds = (submittedAt.longValue() - roundStartMillis) / 1000;
            }
            boolean bonus = (correct == expected && elapsedSeconds <= BONUS_WINDOW_SECONDS);
            if (bonus) {
                delta += 2;
            }
            deltas.put(name, Integer.valueOf(delta));
            correctCounts.put(name, Integer.valueOf(correct));
            bonusFlags.put(name, Boolean.valueOf(bonus));
        }

        // The questioner scores nothing at all: a wrong guess or a timeout must not pay the
        // questioner, and the questioner has no other way to earn points. Their delta stays
        // absent, which buildRoundResultPayload() reports as 0.

        for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
            int current = scores.containsKey(entry.getKey()) ? scores.get(entry.getKey()).intValue() : 0;
            scores.put(entry.getKey(), Integer.valueOf(current + entry.getValue().intValue()));
        }
    }

    private int countMatching(String[] target, String[] guess) {
        int matches = 0;
        for (String wanted : target) {
            for (String picked : guess) {
                if (wanted != null && wanted.equalsIgnoreCase(picked)) {
                    matches++;
                    break;
                }
            }
        }
        return matches;
    }

    /**
     * [round, totalRounds, isFinal, secretCsv, reason, questioner, playerCount,
     *  (name, delta, total, guessCsv, correctCount, bonus)*]. Caller holds the lock.
     * guessCsv is "" when the player did not submit; bonus is 0 or 1.
     */
    private String[] buildRoundResultPayload(int roundNumber, boolean isFinal, String reason) {
        List<String> out = new ArrayList<>();
        out.add(String.valueOf(roundNumber));
        out.add(String.valueOf(totalRounds));
        out.add(isFinal ? "1" : "0");
        out.add(secret == null ? "" : String.join(",", secret));
        out.add(reason == null ? "" : reason);
        out.add(questioner == null ? "" : questioner);

        List<ClientHandler> ordered = new ArrayList<>(clients);
        out.add(String.valueOf(ordered.size()));
        for (ClientHandler client : ordered) {
            String name = client.playerName;
            int delta = deltas.containsKey(name) ? deltas.get(name).intValue() : 0;
            int total = scores.containsKey(name) ? scores.get(name).intValue() : 0;
            String[] guess = guesses.get(name);
            int correct = correctCounts.containsKey(name) ? correctCounts.get(name).intValue() : 0;
            boolean bonus = Boolean.TRUE.equals(bonusFlags.get(name));
            out.add(name);
            out.add(String.valueOf(delta));
            out.add(String.valueOf(total));
            out.add(guess == null ? "" : String.join(",", guess));
            out.add(String.valueOf(correct));
            out.add(bonus ? "1" : "0");
        }
        return out.toArray(new String[0]);
    }

    // =====================================================================
    //  Chat
    // =====================================================================

    private void handleChat(ClientHandler sender, String text) {
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) {
            return;
        }
        if (clean.length() > 200) {
            clean = clean.substring(0, 200);
        }
        broadcast(CHAT, sender.playerName, clean);
    }

    // =====================================================================
    //  Per-client handler
    // =====================================================================

    private class ClientHandler extends Thread {
        private final Socket socket;
        private final Object writeLock = new Object();
        private DataInputStream in;
        private DataOutputStream out;
        private String playerName;
        private boolean joined;

        ClientHandler(Socket socket) {
            super("GameServer-Client");
            this.socket = socket;
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                while (running && !socket.isClosed()) {
                    Message message = Message.readFrom(in);
                    dispatch(message);
                }
            } catch (EOFException e) {
                // client closed the stream cleanly
            } catch (SocketException e) {
                // connection reset or closed by the server
            } catch (IOException e) {
                if (running) {
                    System.out.println("[server] connection error: " + e.getMessage());
                }
            } finally {
                handleDisconnect(this);
            }
        }

        private void dispatch(Message message) {
            if (!joined) {
                if (JOIN.equals(message.type)) {
                    handleJoin(this, message.arg(0));
                } else {
                    send(ERROR, "You must join the room before sending anything else.");
                }
                return;
            }

            if (READY.equals(message.type)) {
                boolean value = "1".equals(message.arg(0)) || "true".equalsIgnoreCase(message.arg(0));
                synchronized (lock) {
                    ready.put(playerName, Boolean.valueOf(value));
                }
                broadcastPlayerList();
            } else if (START_GAME.equals(message.type)) {
                handleStartGame(this);
            } else if (SECRET.equals(message.type)) {
                handleSecret(this, message.args);
            } else if (GUESS.equals(message.type)) {
                handleGuess(this, message.args);
            } else if (NEW_GAME.equals(message.type)) {
                handleNewGame(this);
            } else if (CHAT.equals(message.type)) {
                handleChat(this, message.arg(0));
            } else if (LEAVE_ROOM.equals(message.type)) {
                closeQuietly(); // the finally block in run() performs the clean removal
            } else if (JOIN.equals(message.type)) {
                send(ERROR, "You already joined the room.");
            } else {
                send(ERROR, "Unknown message type: " + message.type);
            }
        }

        void send(String type, String... args) {
            send(new Message(type, args));
        }

        void send(Message message) {
            synchronized (writeLock) {
                if (out == null) {
                    return;
                }
                try {
                    message.writeTo(out);
                } catch (IOException e) {
                    closeQuietly();
                }
            }
        }

        void closeQuietly() {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    // =====================================================================
    //  Network helpers (used by JoinScreen to show a reachable address)
    // =====================================================================

    /** Every non-loopback IPv4 address this machine can be reached on over LAN/Wi-Fi. */
    public static List<String> getLanAddresses() {
        final Map<String, Integer> ranks = new LinkedHashMap<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                String label = (networkInterface.getDisplayName() + " " + networkInterface.getName()).toLowerCase();
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address) {
                        String text = address.getHostAddress();
                        ranks.put(text, Integer.valueOf(addressRank(text, label)));
                    }
                }
            }
        } catch (SocketException e) {
            // no interfaces available
        }
        List<String> result = new ArrayList<>(ranks.keySet());
        // Best address first; equal ranks fall back to plain text order so the result is stable.
        Collections.sort(result, new java.util.Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                int byRank = ranks.get(a).compareTo(ranks.get(b));
                return byRank != 0 ? byRank : a.compareTo(b);
            }
        });
        return result;
    }

    /**
     * Lower is better. Home/office LAN ranges come first (192.168, then 10, then 172.16-31); adapters that
     * belong to VirtualBox, VMware, WSL, Docker, VPNs and the like are pushed to the back, because other
     * machines on the Wi-Fi cannot reach those addresses.
     */
    private static int addressRank(String ip, String interfaceLabel) {
        int rank;
        if (ip.startsWith("192.168.")) {
            rank = 0;
        } else if (ip.startsWith("10.")) {
            rank = 1;
        } else if (ip.startsWith("172.")) {
            int second = 0;
            try {
                second = Integer.parseInt(ip.split("\\.")[1]);
            } catch (NumberFormatException e) {
                second = 0;
            }
            rank = (second >= 16 && second <= 31) ? 2 : 3;
        } else {
            rank = 3;
        }

        String[] virtualMarkers = {"virtualbox", "vmware", "vmnet", "vbox", "hyper-v", "vethernet", "wsl",
            "docker", "tap-", "tun", "vpn", "tailscale", "zerotier", "loopback pseudo", "bluetooth"};
        for (String marker : virtualMarkers) {
            if (interfaceLabel.contains(marker)) {
                rank += 10;
                break;
            }
        }
        // Default host-only ranges used by VirtualBox (192.168.56.x) and similar tools.
        if (ip.startsWith("192.168.56.") || ip.startsWith("192.168.99.")) {
            rank += 10;
        }
        return rank;
    }

    /** Best-guess LAN address for the "others connect here" hint, or "" when none is available. */
    public static String getPreferredLanAddress() {
        List<String> addresses = getLanAddresses();
        if (addresses.isEmpty()) {
            return "";
        }
        return addresses.get(0);
    }

    // =====================================================================
    //  Standalone server entry point (optional; the host normally starts it from the UI)
    // =====================================================================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid port, using default " + DEFAULT_PORT);
            }
        }
        GameServer server = new GameServer(port);
        try {
            server.start();
        } catch (IOException e) {
            System.out.println("Could not start server on port " + port + ": " + e.getMessage());
            return;
        }
        System.out.println("Server listening on port " + port);
        for (String address : getLanAddresses()) {
            System.out.println("  Others can join at " + address + ":" + port);
        }
    }
}
