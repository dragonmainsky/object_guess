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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
 * reader thread, and the round timeout runs on a single-thread scheduler. No Swing work
 * is done here, and no Thread.sleep() is used to coordinate turns - the server simply
 * broadcasts state changes.
 */
public class GameServer {

    // ===== Configuration =====
    public static final int DEFAULT_PORT = 5000;
    public static final int MIN_PLAYERS = 2;      // host + 1 other keeps LAN testing possible
    public static final int MAX_PLAYERS = 8;
    public static final int TOTAL_ROUNDS = 4;
    public static final int ROUND_SECONDS = 30;
    public static final int TARGET_SHAPES = 3;
    public static final int BONUS_WINDOW_SECONDS = 10;  // "+2 when all correct under 10 sec"
    public static final int MAX_NAME_LENGTH = 16;

    // ===== Protocol message types (shared by server and client) =====
    public static final String JOIN = "JOIN";
    public static final String JOIN_SUCCESS = "JOIN_SUCCESS";
    public static final String JOIN_FAILED = "JOIN_FAILED";
    public static final String PLAYER_LIST = "PLAYER_LIST";
    public static final String READY = "READY";
    public static final String START_GAME = "START_GAME";
    public static final String ROUND_INFO = "ROUND_INFO";
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
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> roundTimer;

    /** Single monitor guarding every mutable field below. */
    private final Object lock = new Object();
    private final List<ClientHandler> clients = new ArrayList<>();   // join order; index 0 is the host
    private final Map<String, Boolean> ready = new LinkedHashMap<>();
    private final Map<String, Integer> scores = new LinkedHashMap<>();
    private final Map<String, Integer> deltas = new LinkedHashMap<>();
    private final Map<String, String[]> guesses = new LinkedHashMap<>();
    private final Map<String, Long> guessTimes = new LinkedHashMap<>();

    private int round;                    // last started round, 0 = fresh game
    private String questioner;
    private String[] secret;
    private boolean secretLocked;
    private boolean gameRunning;
    private boolean roundEnded;
    private long roundStartMillis;

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
        scheduler = Executors.newSingleThreadScheduledExecutor();
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

        if (roundTimer != null) {
            roundTimer.cancel(false);
            roundTimer = null;
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
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
            if (clients.size() >= MAX_PLAYERS) {
                handler.send(JOIN_FAILED, "Room is full (maximum " + MAX_PLAYERS + " players).");
                handler.closeQuietly();
                return;
            }
            if (findByNameLocked(name) != null) {
                handler.send(JOIN_FAILED, "That name is already taken in this room.");
                handler.closeQuietly();
                return;
            }
            if (gameRunning) {
                handler.send(JOIN_FAILED, "A game is already in progress. Try again later.");
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

        boolean abort = false;
        boolean finishRound = false;
        synchronized (lock) {
            if (gameRunning) {
                if (name != null && name.equals(questioner)) {
                    abort = true;
                } else if (countGuessersLocked() > 0 && countSubmittedLocked() >= countGuessersLocked()) {
                    finishRound = true;
                }
            }
        }

        broadcast(PLAYER_LEFT, name);
        broadcast(SYSTEM, name + " left the room.");
        broadcastPlayerList();

        if (abort) {
            abortRound("The questioner left the room.");
        } else if (finishRound) {
            endRound("all");
        }
    }

    // =====================================================================
    //  Game flow
    // =====================================================================

    private void handleStartGame(ClientHandler requester) {
        synchronized (lock) {
            if (gameRunning) {
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
            if (round >= TOTAL_ROUNDS) {
                resetScoresLocked();
            }
            gameRunning = true;
        }
        broadcast(SYSTEM, "The game is starting!");
        nextRound();
    }

    private void handleNewGame(ClientHandler requester) {
        synchronized (lock) {
            if (clients.isEmpty() || clients.get(0) != requester) {
                requester.send(ERROR, "Only the host can start a new round.");
                return;
            }
            if (gameRunning) {
                requester.send(ERROR, "A round is in progress.");
                return;
            }
            if (round >= TOTAL_ROUNDS) {
                resetScoresLocked();
            }
            for (ClientHandler client : clients) {
                ready.put(client.playerName, Boolean.FALSE);
            }
        }
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
        guesses.clear();
        guessTimes.clear();
    }

    private void nextRound() {
        String selectedQuestioner;
        int currentRound;
        synchronized (lock) {
            if (clients.isEmpty()) {
                gameRunning = false;
                return;
            }
            round++;
            roundEnded = false;
            secret = null;
            secretLocked = false;
            guesses.clear();
            guessTimes.clear();
            deltas.clear();
            roundStartMillis = System.currentTimeMillis();

            int index = (round - 1) % clients.size();
            questioner = clients.get(index).playerName;
            selectedQuestioner = questioner;
            currentRound = round;

            for (ClientHandler client : clients) {
                ready.put(client.playerName, Boolean.FALSE);
            }
        }
        broadcast(ROUND_INFO,
                String.valueOf(currentRound),
                String.valueOf(TOTAL_ROUNDS),
                selectedQuestioner,
                String.valueOf(ROUND_SECONDS));
        startRoundTimer();
    }

    private void startRoundTimer() {
        synchronized (lock) {
            if (roundTimer != null) {
                roundTimer.cancel(false);
            }
            if (scheduler == null || scheduler.isShutdown()) {
                return;
            }
            roundTimer = scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    endRound("time");
                }
            }, ROUND_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void handleSecret(ClientHandler sender, String[] shapes) {
        synchronized (lock) {
            if (!gameRunning) {
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
            if (shapes.length < TARGET_SHAPES) {
                sender.send(ERROR, "Pick exactly " + TARGET_SHAPES + " shapes.");
                return;
            }
            secret = new String[] {shapes[0], shapes[1], shapes[2]};
            secretLocked = true;
        }
        broadcast(SECRET_LOCKED, questioner);
        broadcast(SYSTEM, "The questioner locked in " + TARGET_SHAPES + " shapes. Start guessing!");
    }

    private void handleGuess(ClientHandler sender, String[] shapes) {
        int submitted;
        int totalGuessers;
        synchronized (lock) {
            if (!gameRunning) {
                sender.send(ERROR, "No round is in progress.");
                return;
            }
            if (!secretLocked) {
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
            if (shapes.length < TARGET_SHAPES) {
                sender.send(ERROR, "Pick exactly " + TARGET_SHAPES + " shapes.");
                return;
            }
            guesses.put(sender.playerName, new String[] {shapes[0], shapes[1], shapes[2]});
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
        synchronized (lock) {
            if (!gameRunning || roundEnded) {
                return;
            }
            roundEnded = true;
            if (roundTimer != null) {
                roundTimer.cancel(false);
                roundTimer = null;
            }
            computeScoresLocked();
            boolean isFinal = round >= TOTAL_ROUNDS;
            payload = buildRoundResultPayload(round, isFinal, reason);
            gameRunning = false;
        }
        broadcast(ROUND_RESULT, payload);
    }

    private void abortRound(String reason) {
        boolean wasRunning;
        synchronized (lock) {
            wasRunning = gameRunning;
            gameRunning = false;
            roundEnded = true;
            if (roundTimer != null) {
                roundTimer.cancel(false);
                roundTimer = null;
            }
        }
        if (!wasRunning) {
            return;
        }
        broadcast(TO_LOBBY, hostName(), reason);
    }

    /** Server-side scoring: the only place a score is ever produced. Caller must hold the lock. */
    private void computeScoresLocked() {
        deltas.clear();
        if (secret == null) {
            for (ClientHandler client : clients) {
                deltas.put(client.playerName, Integer.valueOf(0));
            }
            return;
        }

        int perfectGuessers = 0;
        for (ClientHandler client : clients) {
            String name = client.playerName;
            if (name.equals(questioner)) {
                continue;
            }
            String[] guess = guesses.get(name);
            if (guess == null) {
                deltas.put(name, Integer.valueOf(0));
                continue;
            }
            int correct = countMatching(secret, guess);
            int delta = correct;
            Long submittedAt = guessTimes.get(name);
            long elapsedSeconds = 0;
            if (submittedAt != null) {
                elapsedSeconds = (submittedAt.longValue() - roundStartMillis) / 1000;
            }
            if (correct == TARGET_SHAPES && elapsedSeconds <= BONUS_WINDOW_SECONDS) {
                delta += 2;
            }
            if (correct == TARGET_SHAPES) {
                perfectGuessers++;
            }
            deltas.put(name, Integer.valueOf(delta));
        }

        // The questioner scores only when nobody found all shapes.
        if (questioner != null) {
            deltas.put(questioner, Integer.valueOf(perfectGuessers == 0 ? 2 : 0));
        }

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

    /** [round, totalRounds, isFinal, secret, reason, playerCount, (name, delta, total)*]. Caller holds lock. */
    private String[] buildRoundResultPayload(int roundNumber, boolean isFinal, String reason) {
        List<String> out = new ArrayList<>();
        out.add(String.valueOf(roundNumber));
        out.add(String.valueOf(TOTAL_ROUNDS));
        out.add(isFinal ? "1" : "0");
        out.add(secret == null ? "" : String.join(",", secret));
        out.add(reason == null ? "" : reason);

        List<ClientHandler> ordered = new ArrayList<>(clients);
        out.add(String.valueOf(ordered.size()));
        for (ClientHandler client : ordered) {
            int delta = deltas.containsKey(client.playerName) ? deltas.get(client.playerName).intValue() : 0;
            int total = scores.containsKey(client.playerName) ? scores.get(client.playerName).intValue() : 0;
            out.add(client.playerName);
            out.add(String.valueOf(delta));
            out.add(String.valueOf(total));
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
        List<String> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
                        continue;
                    }
                    if (address instanceof Inet4Address) {
                        result.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // no interfaces available
        }
        Collections.sort(result);
        return result;
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
