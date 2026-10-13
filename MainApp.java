import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * MainApp: master window that owns the CardLayout and, in multiplayer mode, the two
 * network objects. It is the only place that knows how a server event maps onto a screen.
 *
 * Responsibilities:
 *   - CREATE GAME: start a GameServer, then connect the host's own GameClient to it so
 *     the host is a normal player rather than only a listener.
 *   - JOIN GAME: connect a GameClient to the address the player typed.
 *   - Route every server message to the right screen (lobby list, round start, secret
 *     lock, guess submissions, round results, chat, disconnects).
 *
 * All socket work runs on a background executor; every screen update is pushed back onto
 * the Event Dispatch Thread with SwingUtilities.invokeLater(). Nothing in this class
 * blocks the EDT.
 */
public class MainApp extends JFrame implements JoinScreen.JoinListener {

    public static final String SCREEN_JOIN = "JOIN";
    public static final String SCREEN_LOBBY = "LOBBY";
    public static final String SCREEN_QUESTIONER = "QUESTIONER";
    public static final String SCREEN_GUESSER = "GUESSER";
    public static final String SCREEN_SCOREBOARD = "SCOREBOARD";

    // Preview bar colors
    private static final Color NAV_BAR_BG = new Color(15, 23, 42);
    private static final Color NAV_LABEL_FG = new Color(148, 163, 184);
    private static final Color NAV_BTN_NORMAL = Color.BLACK;
    private static final Color NAV_BTN_HOVER = new Color(30, 41, 59);
    private static final Color NAV_BTN_PRESSED = new Color(51, 65, 85);
    private static final Color NAV_BTN_BORDER = new Color(51, 65, 85);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    // ===== Screens =====
    private final JoinScreen joinScreen;
    private final LobbyScreen lobbyScreen;
    private final QuestionerScreen questionerScreen;
    private final GuesserScreen guesserScreen;
    private final ScoreboardScreen scoreboardScreen;

    // ===== Network state =====
    private final ExecutorService netExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MainApp-Net");
        thread.setDaemon(true);
        return thread;
    });

    /** How long to wait for JOIN_SUCCESS / JOIN_FAILED before giving up on an attempt. */
    private static final int JOIN_TIMEOUT_MS = 8000;

    private GameClient client;
    private GameServer server;
    private String myName = "";
    private boolean isHost = false;
    // Volatile: written on the network thread, read later on the Event Dispatch Thread.
    private volatile String connectAddress = "";
    private volatile String pendingAddress = "";
    private boolean intentionalLeave = false;
    private String currentScreen = SCREEN_JOIN;

    // Guards a single join attempt: bumped whenever an attempt ends, so a stale timeout is ignored.
    private int joinAttemptId = 0;
    private javax.swing.Timer joinTimeoutTimer;

    private final List<String> playerNames = new ArrayList<>();
    private final Map<String, Boolean> readyStates = new LinkedHashMap<>();
    private final Map<String, Integer> lastScores = new LinkedHashMap<>();
    private String hostName = "";

    public MainApp() {
        setTitle("Guess What It Looks Like? - Multiplayer Party Game (LAN)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Size to the screen so the bottom action buttons never fall off a small or scaled display.
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int frameWidth = Math.min(1360, Math.max(900, screen.width - 40));
        int frameHeight = Math.min(800, Math.max(560, screen.height - 60));
        setSize(frameWidth, frameHeight);
        setMinimumSize(new Dimension(Math.min(1000, frameWidth), Math.min(620, frameHeight)));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Screen Instances with Flow Interactivity ---
        // JoinScreen reports host/join requests back to this frame.
        joinScreen = new JoinScreen(this);

        // Lobby: START GAME asks the server, LEAVE ROOM tears the session down.
        lobbyScreen = new LobbyScreen(
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    requestStartGame();
                }
            },
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    leaveRoom();
                }
            }
        );
        lobbyScreen.setReadyAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                toggleReady();
            }
        });
        lobbyScreen.setChatAction(text -> {
            if (client != null) {
                client.sendChat(text);
            }
        });

        // Questioner CONFIRM sends the secret; the server decides what happens next.
        questionerScreen = new QuestionerScreen(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                submitSecret();
            }
        });

        // Guesser SUBMIT sends the guess; the server scores it.
        guesserScreen = new GuesserScreen(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                submitGuess();
            }
        });

        // Scoreboard: CONTINUE / NEW GAME returns everyone to the lobby, QUIT leaves the room.
        scoreboardScreen = new ScoreboardScreen(
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    requestNewGame();
                }
            },
            new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    leaveRoom();
                }
            }
        );

        // Add to CardPanel
        cardPanel.add(joinScreen, SCREEN_JOIN);
        cardPanel.add(lobbyScreen, SCREEN_LOBBY);
        cardPanel.add(questionerScreen, SCREEN_QUESTIONER);
        cardPanel.add(guesserScreen, SCREEN_GUESSER);
        cardPanel.add(scoreboardScreen, SCREEN_SCOREBOARD);

        // --- Top Preview/Switcher Control Bar ---
        add(createQuickSwitcherBar(), BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);

        // Show initial screen (Join)
        showScreen(SCREEN_JOIN);

        setVisible(true);
    }

    public void showScreen(String name) {
        currentScreen = name;
        cardLayout.show(cardPanel, name);
    }

    /** True when the terminal handlers have already returned the user to the join screen. */
    private boolean alreadyAtJoinScreen() {
        return SCREEN_JOIN.equals(currentScreen);
    }

    // =====================================================================
    //  JoinScreen.JoinListener  (called on the EDT by JoinScreen)
    // =====================================================================

    @Override
    public void onCreateGame(final String playerName, final int port) {
        if (server != null && server.isRunning()) {
            joinScreen.setBusy(false);
            joinScreen.showStatus("This machine is already hosting a room. Leave it first.", true);
            return;
        }
        final int attempt = ++joinAttemptId;
        joinScreen.setBusy(true);
        joinScreen.setHostInfo(port);
        joinScreen.showStatus("Starting the room on port " + port + "...");
        netExecutor.submit(() -> startHost(playerName, port, attempt));
    }

    @Override
    public void onJoinGame(final String playerName, final String serverIp, final int port) {
        if (client != null && client.isConnected()) {
            joinScreen.setBusy(false);
            joinScreen.showStatus("Already connected. Leave the room before joining another.", true);
            return;
        }
        final int attempt = ++joinAttemptId;
        pendingAddress = serverIp + ":" + port;
        joinScreen.setBusy(true);
        joinScreen.showStatus("Connecting to " + serverIp + ":" + port + "...");
        netExecutor.submit(() -> connectAndJoin(playerName, serverIp, port, false, attempt));
    }

    // =====================================================================
    //  Network lifecycle (all methods below run off the EDT)
    // =====================================================================

    private void startHost(final String playerName, final int port, final int attempt) {
        GameServer newServer = new GameServer(port);
        try {
            newServer.start();
        } catch (IOException e) {
            onEdt(() -> {
                joinScreen.setBusy(false);
                joinScreen.showStatus("Could not host on port " + port + ": " + e.getMessage() + ". "
                        + "Another program may be using it, or the firewall blocked it. Try port 5050.", true);
                showError("Could not start the room",
                        "Port " + port + " could not be opened on this machine.\n"
                        + e.getMessage() + "\n\nTry a different port (for example 5050), "
                        + "or allow Java through your firewall.");
            });
            return;
        }
        server = newServer;
        onEdt(() -> joinScreen.showStatus("Room is up on port " + port + ". Joining as host..."));

        // The host joins its own room through the normal loopback path, so it behaves exactly
        // like any other player. Only the address shown to other machines must be the LAN IP.
        connectAndJoin(playerName, "127.0.0.1", port, true, attempt);
    }

    private void connectAndJoin(final String playerName, final String ip, final int port,
                                final boolean host, final int attempt) {
        GameClient newClient = new GameClient(ip, port, new NetworkListener());
        try {
            newClient.connect();
        } catch (IOException e) {
            final String message = describeConnectionFailure(e, ip, port);
            onEdt(() -> {
                if (host && server != null) {
                    server.shutdown();
                    server = null;
                }
                joinScreen.setBusy(false);
                joinScreen.showStatus(message, true);
                showError("Connection failed", message);
            });
            return;
        }

        client = newClient;
        intentionalLeave = false;
        final String displayAddress;
        if (host) {
            String lan = GameServer.getPreferredLanAddress();
            displayAddress = (lan.isEmpty() ? "127.0.0.1" : lan) + ":" + port;
        } else {
            displayAddress = ip + ":" + port;
        }
        connectAddress = displayAddress;
        pendingAddress = displayAddress;
        newClient.join(playerName);
        onEdt(() -> {
            joinScreen.showStatus("Connected. Asking the host to let you in...");
            startJoinTimeout(attempt);
        });
    }

    /**
     * Starts the deadline for one join attempt. If no JOIN_SUCCESS / JOIN_FAILED arrives the
     * attempt is torn down, so the buttons can never stay disabled forever. Runs on the EDT;
     * the attempt id makes a stale timer a no-op if the reply already arrived.
     */
    private void startJoinTimeout(final int attempt) {
        stopJoinTimeout();
        joinTimeoutTimer = new javax.swing.Timer(JOIN_TIMEOUT_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (attempt != joinAttemptId || client == null || !myName.isEmpty()) {
                    return;
                }
                teardownSession();
                joinScreen.setBusy(false);
                String address = pendingAddress.isEmpty() ? "the host" : pendingAddress;
                joinScreen.showStatus("No reply from " + address + " after "
                        + (JOIN_TIMEOUT_MS / 1000) + " seconds.", true);
                showError("No reply from the host",
                        "Connected to " + address + " but the host never answered within "
                        + (JOIN_TIMEOUT_MS / 1000) + " seconds.\n\n"
                        + "- Check both machines are on the same Wi-Fi/LAN.\n"
                        + "- Check the host's firewall allows Java on that port.\n"
                        + "- Check the IP and port are exactly what the host shows.");
                showScreen(SCREEN_JOIN);
            }
        });
        joinTimeoutTimer.setRepeats(false);
        joinTimeoutTimer.start();
    }

    private void stopJoinTimeout() {
        if (joinTimeoutTimer != null) {
            joinTimeoutTimer.stop();
            joinTimeoutTimer = null;
        }
    }

    private String describeConnectionFailure(IOException e, String ip, int port) {
        if (e instanceof UnknownHostException) {
            return "Server address not found: \"" + ip + "\". Check the IP the host showed you.";
        }
        if (e instanceof ConnectException) {
            return "Connection refused by " + ip + ":" + port + ". Is the host's room still open? "
                    + "Are both machines on the same Wi-Fi?";
        }
        if (e instanceof SocketTimeoutException) {
            return "Connection to " + ip + ":" + port + " timed out. "
                    + "Check the IP, the port, and that the firewall allows Java.";
        }
        return "Could not connect to " + ip + ":" + port + ": " + e.getMessage();
    }

    /** Closes everything and returns to the join screen. Safe to call when already offline. */
    private void leaveRoom() {
        intentionalLeave = true;
        joinAttemptId++;      // cancel any pending join timeout
        stopJoinTimeout();
        if (client != null) {
            client.leave();
            client = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
        resetRoomState();
        joinScreen.reset();
        showScreen(SCREEN_JOIN);
    }

    private void resetRoomState() {
        myName = "";
        isHost = false;
        hostName = "";
        connectAddress = "";
        playerNames.clear();
        readyStates.clear();
        lastScores.clear();
        lobbyScreen.clearChat();
    }

    private void requestStartGame() {
        if (client == null) {
            showError("Not connected", "You are not in a room right now.");
            return;
        }
        client.requestStartGame();
    }

    private void toggleReady() {
        if (client == null) {
            return;
        }
        client.setReady(!lobbyScreen.isReady());
    }

    private void submitSecret() {
        if (client == null) {
            return;
        }
        List<String> shapes = questionerScreen.getSelectedShapes();
        if (shapes.size() < GameServer.TARGET_SHAPES) {
            return;
        }
        client.submitSecret(shapes);
        questionerScreen.lockSelection();
    }

    private void submitGuess() {
        if (client == null) {
            return;
        }
        List<String> shapes = guesserScreen.getGuessedShapes();
        if (shapes.size() < GameServer.TARGET_SHAPES) {
            return;
        }
        client.submitGuess(shapes);
        guesserScreen.markSubmitted();
    }

    private void requestNewGame() {
        if (client == null) {
            return;
        }
        if (!isHost) {
            showInfo("Only the host can start the next round.", "Waiting for the host");
            return;
        }
        client.requestNewGame();
    }

    // =====================================================================
    //  Server message handling (always executed on the EDT)
    // =====================================================================

    private void handleMessage(String type, String[] args) {
        if (GameServer.JOIN_SUCCESS.equals(type)) {
            handleJoinSuccess(args);
        } else if (GameServer.JOIN_FAILED.equals(type)) {
            handleJoinFailed(args);
        } else if (GameServer.PLAYER_LIST.equals(type)) {
            handlePlayerList(args);
        } else if (GameServer.ROUND_INFO.equals(type)) {
            handleRoundInfo(args);
        } else if (GameServer.SECRET_LOCKED.equals(type)) {
            handleSecretLocked(args);
        } else if (GameServer.GUESS_ACCEPTED.equals(type)) {
            guesserScreen.markSubmitted();
        } else if (GameServer.GUESS_SUBMITTED.equals(type)) {
            handleGuessSubmitted(args);
        } else if (GameServer.ROUND_RESULT.equals(type)) {
            handleRoundResult(args);
        } else if (GameServer.TO_LOBBY.equals(type)) {
            handleToLobby(args);
        } else if (GameServer.CHAT.equals(type)) {
            lobbyScreen.addChatMessage(args.length > 0 ? args[0] : "?", args.length > 1 ? args[1] : "");
        } else if (GameServer.SYSTEM.equals(type)) {
            lobbyScreen.addChatMessage("System", args.length > 0 ? args[0] : "");
        } else if (GameServer.SERVER_CLOSED.equals(type)) {
            handleServerClosed();
        } else if (GameServer.ERROR.equals(type)) {
            showError("Server message", args.length > 0 ? args[0] : "Unknown error");
        } else if (GameServer.PLAYER_LEFT.equals(type)) {
            // The refreshed PLAYER_LIST right after this already updates the lobby rows.
        }
    }

    private void handleJoinSuccess(String[] args) {
        joinAttemptId++;      // invalidate the pending timeout for this attempt
        stopJoinTimeout();
        myName = args.length > 0 ? args[0] : "";
        isHost = args.length > 1 && "1".equals(args[1]);
        hostName = myName; // corrected by the first PLAYER_LIST

        lobbyScreen.setMyName(myName);
        lobbyScreen.setHost(isHost);
        lobbyScreen.clearChat();
        questionerScreen.setMyName(myName);
        guesserScreen.setMyName(myName);
        scoreboardScreen.setNextEnabled(isHost);

        if (!connectAddress.isEmpty()) {
            lobbyScreen.setAddress(connectAddress);
            int colon = connectAddress.lastIndexOf(':');
            if (colon > 0) {
                try {
                    lobbyScreen.setPort(Integer.parseInt(connectAddress.substring(colon + 1)));
                } catch (NumberFormatException ignored) {
                    // keep the previous port label
                }
            }
        }

        joinScreen.setBusy(false);
        joinScreen.showSuccess("Joined the room as " + myName + (isHost ? " (host)" : "") + ".");
        showScreen(SCREEN_LOBBY);
    }

    private void handleJoinFailed(String[] args) {
        joinAttemptId++;      // the attempt is over; a pending timeout must not fire
        stopJoinTimeout();
        String reason = args.length > 0 ? args[0] : "The host refused the connection.";
        teardownSession();
        joinScreen.setBusy(false);
        joinScreen.showStatus("Could not join: " + reason, true);
        showError("Could not join the room", reason);
        showScreen(SCREEN_JOIN);
    }

    private void handlePlayerList(String[] args) {
        int count;
        try {
            count = Integer.parseInt(args.length > 0 ? args[0] : "0");
        } catch (NumberFormatException e) {
            return;
        }
        playerNames.clear();
        readyStates.clear();
        hostName = "";
        for (int i = 0; i < count; i++) {
            int base = 1 + i * 3;
            if (base + 2 >= args.length) {
                break;
            }
            String name = arg(args, base);
            boolean ready = "1".equals(arg(args, base + 1));
            boolean host = "1".equals(arg(args, base + 2));
            if (name.isEmpty()) {
                continue;
            }
            playerNames.add(name);
            readyStates.put(name, Boolean.valueOf(ready));
            if (host) {
                hostName = name;
            }
        }
        lobbyScreen.setPlayers(playerNames, readyStates, hostName);
        pushScoreboards();
    }

    private void handleRoundInfo(String[] args) {
        int round = parseInt(arg(args, 0), 1);
        int total = parseInt(arg(args, 1), GameServer.TOTAL_ROUNDS);
        String questioner = arg(args, 2);

        pushScoreboards();
        if (myName.equals(questioner)) {
            questionerScreen.startRound(round, total);
            showScreen(SCREEN_QUESTIONER);
        } else {
            guesserScreen.startRound(round, total, questioner);
            showScreen(SCREEN_GUESSER);
        }
    }

    private void handleSecretLocked(String[] args) {
        String questioner = arg(args, 0);
        if (myName.equals(questioner)) {
            questionerScreen.lockSelection();
        } else {
            guesserScreen.onSecretLocked(questioner);
        }
    }

    private void handleGuessSubmitted(String[] args) {
        String who = arg(args, 0);
        String submitted = arg(args, 1);
        String total = arg(args, 2);
        String line = "Guesses in: " + submitted + " / " + total
                + (who.isEmpty() ? "" : " (last: " + who + ")");
        questionerScreen.setStatus(line);
        if (!who.equals(myName)) {
            guesserScreen.setStatus("Waiting for the other players... " + submitted + " / " + total);
        }
    }

    private void handleRoundResult(String[] args) {
        int round = parseInt(arg(args, 0), 1);
        boolean isFinal = "1".equals(arg(args, 2));
        String reason = arg(args, 4);
        int playerCount = parseInt(arg(args, 5), 0);

        lastScores.clear();
        for (int i = 0; i < playerCount; i++) {
            int base = 6 + i * 3;
            String name = arg(args, base);
            int total = parseInt(arg(args, base + 2), 0);
            if (!name.isEmpty()) {
                lastScores.put(name, Integer.valueOf(total));
            }
        }

        pushScoreboards();

        String[] names = playerNames.toArray(new String[0]);
        int[] scores = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            Integer value = lastScores.get(names[i]);
            scores[i] = (value == null) ? 0 : value.intValue();
        }
        if (names.length == 0) {
            // Very unlikely, but fall back to the payload order so the screen is never empty.
            names = new String[playerCount];
            scores = new int[playerCount];
            for (int i = 0; i < playerCount; i++) {
                names[i] = arg(args, 6 + i * 3);
                scores[i] = parseInt(arg(args, 8 + i * 3), 0);
            }
        }

        scoreboardScreen.setResults(names, scores, round, isFinal);
        scoreboardScreen.setNextEnabled(isHost);
        if ("time".equals(reason)) {
            scoreboardScreen.setToolTipText("The round timer ran out.");
        }
        questionerScreen.setStatus("Round finished.");
        guesserScreen.setStatus("Round finished. See the scoreboard.");
        showScreen(SCREEN_SCOREBOARD);
    }

    private void handleToLobby(String[] args) {
        String reason = args.length > 1 ? args[1] : "";
        if (reason != null && !reason.isEmpty()) {
            lobbyScreen.setStatusText(reason);
            showInfo(reason, "Back to the lobby");
        }
        pushScoreboards();
        showScreen(SCREEN_LOBBY);
    }

    private void handleServerClosed() {
        if (intentionalLeave || alreadyAtJoinScreen()) {
            return;
        }
        teardownSession();
        joinScreen.reset();
        showScreen(SCREEN_JOIN);
        showError("Room closed", "The host closed the room. You have been returned to the join screen.");
    }

    private void handleDisconnected(String reason) {
        if (intentionalLeave || alreadyAtJoinScreen()) {
            // Either the player left on purpose, or a terminal handler (server closed / join
            // failed) already cleaned up. Either way there is nothing left to report.
            return;
        }
        teardownSession();
        joinScreen.reset();
        showScreen(SCREEN_JOIN);
        showError("Disconnected", reason);
    }

    /** Closes the client and the server (when this machine was hosting) and forgets the room. */
    private void teardownSession() {
        stopJoinTimeout();
        if (client != null) {
            client.close();
            client = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
        resetRoomState();
    }

    /** Rebuilds both in-game side scoreboards from the latest known player list and scores. */
    private void pushScoreboards() {
        String[] names = playerNames.toArray(new String[0]);
        int[] scores = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            Integer value = lastScores.get(names[i]);
            scores[i] = (value == null) ? 0 : value.intValue();
        }
        questionerScreen.setScoreboard(names, scores);
        guesserScreen.setScoreboard(names, scores);
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    private static String arg(String[] args, int index) {
        if (index < 0 || index >= args.length) {
            return "";
        }
        return args[index];
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void onEdt(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    private void showError(String title, String message) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Reader-thread callbacks: hand everything to the EDT and never touch Swing here. */
    private class NetworkListener implements GameClient.Listener {
        @Override
        public void onMessage(final String type, final String[] args) {
            onEdt(() -> handleMessage(type, args));
        }

        @Override
        public void onDisconnected(final String reason) {
            onEdt(() -> handleDisconnected(reason));
        }
    }

    // =====================================================================
    //  Top preview / switcher bar
    // =====================================================================

    /**
     * Design/preview bar: it only flips between the five screens so the UI can be reviewed
     * without a room. It never fakes a connection, and the label makes that explicit.
     */
    private JPanel createQuickSwitcherBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(NAV_BAR_BG);
        bar.setBorder(new EmptyBorder(6, 16, 6, 16));

        // Label on left
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel navLabel = new JLabel("UI PREVIEW SWITCHER (design preview only - no connection is made):");
        navLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        navLabel.setForeground(NAV_LABEL_FG);
        left.add(navLabel);
        bar.add(left, BorderLayout.WEST);

        // Switcher Buttons
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        navButtons.setOpaque(false);

        navButtons.add(createNavButton("1. Join / Host", SCREEN_JOIN));
        navButtons.add(createNavButton("2. Game Lobby", SCREEN_LOBBY));
        navButtons.add(createNavButton("3. Questioner Mode", SCREEN_QUESTIONER));
        navButtons.add(createNavButton("4. Guesser Mode", SCREEN_GUESSER));
        navButtons.add(createNavButton("5. Scoreboard / Results", SCREEN_SCOREBOARD));

        bar.add(navButtons, BorderLayout.EAST);
        return bar;
    }

    /**
     * Plain JButton: black fill, thin slate border, white bold text.
     * Fill color changes with hover/press through NavButtonStateListener.
     */
    private JButton createNavButton(String title, String screenName) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(Color.WHITE);
        btn.setBackground(NAV_BTN_NORMAL);
        btn.setContentAreaFilled(false); // call this first: some look and feels reset opaque when it changes
        btn.setOpaque(true);             // the opaque background is what shows the black fill
        btn.setBorderPainted(true);
        btn.setBorder(new CompoundBorder(
            new LineBorder(NAV_BTN_BORDER, 1),
            new EmptyBorder(4, 11, 4, 11)));
        btn.setFocusPainted(false);
        btn.setFocusable(false); // keep keyboard focus on the visible screen
        btn.setRolloverEnabled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.getModel().addChangeListener(new NavButtonStateListener(btn));
        btn.addActionListener(new ScreenSwitcher(screenName));
        return btn;
    }

    /**
     * Switches the CardLayout to a fixed screen. One small class replaces
     * every inline listener, so each screen gets its own instance with its target name.
     */
    private class ScreenSwitcher implements ActionListener {
        private final String screenName;

        ScreenSwitcher(String screenName) {
            this.screenName = screenName;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            showScreen(screenName);
        }
    }

    /**
     * Repaints a preview bar button in the right color whenever its model changes:
     * pressed/armed is lightest, rollover is in between, normal is black.
     */
    private static class NavButtonStateListener implements ChangeListener {
        private final JButton button;

        NavButtonStateListener(JButton button) {
            this.button = button;
        }

        @Override
        public void stateChanged(ChangeEvent event) {
            ButtonModel model = button.getModel();
            if (model.isPressed() || model.isArmed()) {
                button.setBackground(NAV_BTN_PRESSED);
            } else if (model.isRollover()) {
                button.setBackground(NAV_BTN_HOVER);
            } else {
                button.setBackground(NAV_BTN_NORMAL);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new MainApp();
            }
        });
    }
}
