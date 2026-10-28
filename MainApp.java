import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** หน้าต่างหลักของแอปพลิเคชัน */
public class MainApp extends MainApp_UI implements JoinScreen.JoinListener {

    // วัตถุหน้าจอทั้ง 6 หน้าจอ
    private final JoinScreen joinScreen;
    private final LobbyScreen lobbyScreen;
    private final QuestionerScreen questionerScreen;
    private final GuesserScreen guesserScreen;
    private final ScoreboardScreen scoreboardScreen;
    private final RoundResultScreen roundResultScreen;

    /** เธรดสำหรับงานเครือข่าย */
    private final ExecutorService netExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        /** สร้างเธรด daemon */
        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "MainApp-Net");
            thread.setDaemon(true);
            return thread;
        }
    });

    private GameClient client;          // ไคลเอนต์สำหรับเชื่อมต่อกับเซิร์ฟเวอร์
    private GameServer server;          // เซิร์ฟเวอร์ (สร้างเฉพาะเมื่อเครื่องนี้เป็นโฮสต์)
    private String myName = "";         // ชื่อของผู้เล่นเครื่องนี้
    private boolean isHost;             // สถานะว่าเป็นโฮสต์ของห้องหรือไม่
    private String connectAddress = ""; // ที่อยู่ IP:Port ที่ใช้เชื่อมต่อ
    private boolean intentionalLeave;   // สถานะว่าเป็นการกดออกจากห้องเองโดยเจตนาหรือไม่

    /** เวลารอผล JOIN (8 วินาที) */
    private static final int JOIN_REPLY_TIMEOUT_MS = 8000;
    private Thread joinTimeout;

    // ข้อมูลสถานะของห้องที่บันทึกไว้ใน MainApp
    private final List<String> playerNames = new ArrayList<>();
    private final Map<String, Boolean> readyStates = new LinkedHashMap<>();
    private final Map<String, Integer> lastScores = new LinkedHashMap<>();
    private String hostName = "";
    private int currentRound;          // หมายเลขรอบปัจจุบันที่กำลังแสดงบน UI
    private int knownTotalRounds;      // จำนวนรอบทั้งหมดตามที่เซิร์ฟเวอร์แจ้ง
    private boolean roundInProgress;   // มีรอบที่กำลังเล่นอยู่หรือไม่

    /** สร้างหน้าต่างหลักและหน้าจอทั้งหมด */
    public MainApp() {
        super();

        // หน้าจอที่ 1: หน้าจอสร้าง/เข้าร่วมห้อง
        joinScreen = new JoinScreen(this);

        // หน้าจอที่ 2: หน้าจอห้องพักคอย (Lobby)
        lobbyScreen = new LobbyScreen(event -> requestStartGame(), event -> leaveRoom());
        lobbyScreen.setReadyAction(event -> toggleReady());
        lobbyScreen.setChatAction(text -> {
            if (client != null) {
                client.sendChat(text);
            }
        });

        // หน้าจอที่ 3: หน้าจอผู้ตั้งโจทย์ (Questioner)
        questionerScreen = new QuestionerScreen(event -> submitSecret());
        // หน้าจอที่ 4: หน้าจอผู้ทาย (Guesser)
        guesserScreen = new GuesserScreen(event -> submitGuess());
        // หน้าจอที่ 5: Scoreboard
        scoreboardScreen = new ScoreboardScreen(event -> requestNewGame(), event -> leaveRoom());
        // หน้าจอที่ 6: Round Result
        roundResultScreen = new RoundResultScreen();

        // บันทึกหน้าจอทั้งหมดลงใน Map
        registerScreen(SCREEN_JOIN, joinScreen);
        registerScreen(SCREEN_LOBBY, lobbyScreen);
        registerScreen(SCREEN_QUESTIONER, questionerScreen);
        registerScreen(SCREEN_GUESSER, guesserScreen);
        registerScreen(SCREEN_ROUND_RESULT, roundResultScreen);
        registerScreen(SCREEN_SCOREBOARD, scoreboardScreen);

        showScreen(SCREEN_JOIN);
        setVisible(true);
    }

    /** หน้าจอปัจจุบันเป็น JoinScreen */
    private boolean alreadyAtJoinScreen() {
        return SCREEN_JOIN.equals(getCurrentScreen());
    }

    // =====================================================================
    // JoinScreen.JoinListener
    // =====================================================================

    /** จัดการการสร้างห้อง */
    @Override
    public void onCreateGame(String playerName, int port) {
        if (server != null && server.isRunning()) {
            joinScreen.setBusy(false);
            joinScreen.showStatus("This machine is already hosting a room. Leave it first.", true);
            return;
        }
        joinScreen.setBusy(true);
        joinScreen.setHostInfo(port);
        joinScreen.showStatus("Starting the room on port " + port + "...");
        netExecutor.submit(new Runnable() {
            /** เริ่มเซิร์ฟเวอร์บนเธรดเครือข่าย */
            @Override
            public void run() {
                startHost(playerName, port);
            }
        });
    }

    /** จัดการการเข้าร่วมห้อง */
    @Override
    public void onJoinGame(String playerName, String serverIp, int port) {
        if (client != null && client.isConnected()) {
            joinScreen.setBusy(false);
            joinScreen.showStatus("Already connected. Leave the room before joining another.", true);
            return;
        }
        joinScreen.setBusy(true);
        joinScreen.showStatus("Connecting to " + serverIp + ":" + port + "...");
        netExecutor.submit(new Runnable() {
            /** เชื่อมต่อและส่งคำขอ JOIN */
            @Override
            public void run() {
                connectAndJoin(playerName, serverIp, port, false);
            }
        });
    }

    // =====================================================================
    // วงจรชีวิตระบบเครือข่าย
    // =====================================================================

    /** เริ่ม GameServer และเชื่อมต่อในฐานะโฮสต์ */
    private void startHost(String playerName, int port) {
        GameServer newServer = new GameServer(port);
        if (!newServer.start()) {
            String reason = newServer.getStartError();
            joinScreen.setBusy(false);
            joinScreen.showStatus("Could not host on port " + port + ": " + reason + ". "
                    + "Another program may be using it, or the firewall blocked it.", true);
            showError("Could not start the room", "Port " + port + " could not be opened on this machine.\n"
                    + reason + "\n\nTry a different port, or allow Java through your firewall.");
            return;
        }
        server = newServer;
        joinScreen.showStatus("Room is up on port " + port + ". Joining as host...");

        // โฮสต์เชื่อมต่อผ่าน 127.0.0.1
        connectAndJoin(playerName, "127.0.0.1", port, true);
    }

    /** เชื่อมต่อ GameClient ไปยังเซิร์ฟเวอร์ */
    private void connectAndJoin(String playerName, String ip, int port, boolean host) {
        GameClient newClient = new GameClient(ip, port, new NetworkListener());
        try {
            newClient.connect();
        } catch (IOException e) {
            String message = describeConnectionFailure(e, ip, port);
            if (host) {
                server.shutdown();
                server = null;
            }
            joinScreen.setBusy(false);
            joinScreen.showStatus(message, true);
            showError("Connection failed", message);
            return;
        }

        client = newClient;
        intentionalLeave = false;
        if (host) {
            String lan = LanAddresses.getPreferredLanAddress();
            String hostAddress;
            if (lan.isEmpty()) {
                hostAddress = "127.0.0.1";
            } else {
                hostAddress = lan;
            }
            connectAddress = hostAddress + ":" + port;
        } else {
            connectAddress = ip + ":" + port;
        }
        newClient.join(playerName);
        joinScreen.showStatus("Connected. Asking the host to let you in...");
        startJoinTimeout();
    }

    /** เริ่มเวลารอผล JOIN */
    private void startJoinTimeout() {
        cancelJoinTimeout();
        joinTimeout = new Thread(new Runnable() {
            /** เธรดรอผล JOIN */
            @Override
            public void run() {
                try {
                    Thread.sleep(JOIN_REPLY_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    return;   // ได้รับการตอบรับทันเวลา เธรดถูกยกเลิก
                }

                if (joinTimeout != Thread.currentThread() || !alreadyAtJoinScreen()) {
                    return;   // มีการพยายามเชื่อมต่อใหม่ หรือเข้าห้องสำเร็จแล้ว
                }

                joinTimeout = null;
                teardownSession();
                joinScreen.setBusy(false);

                String message = "The host did not answer the join request. "
                        + "Check that the IP and port are right and that the host's firewall allows Java.";
                joinScreen.showStatus(message, true);
                showError("No reply from the host", message);
            }
        }, "MainApp-JoinTimeout");
        joinTimeout.setDaemon(true);
        joinTimeout.start();
    }

    /** ยกเลิกการรอผล JOIN */
    private void cancelJoinTimeout() {
        if (joinTimeout != null) {
            joinTimeout.interrupt();
            joinTimeout = null;
        }
    }

    /** แปลง Exception เป็นข้อความ */
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

    /** ออกจากห้องและกลับไปหน้า JoinScreen */
    private void leaveRoom() {
        intentionalLeave = true;
        if (client != null) {
            client.leave();
        }
        teardownSession();
        joinScreen.reset();
        showScreen(SCREEN_JOIN);
    }

    // ล้างข้อมูลสถานะห้องทั้งหมด
    private void resetRoomState() {
        myName = "";
        isHost = false;
        hostName = "";
        connectAddress = "";
        currentRound = 0;
        knownTotalRounds = 0;
        roundInProgress = false;
        playerNames.clear();
        readyStates.clear();
        lastScores.clear();
        lobbyScreen.clearChat();
    }

    // ขอเริ่มเกมไปยังเซิร์ฟเวอร์
    private void requestStartGame() {
        if (client == null) {
            showError("Not connected", "You are not in a room right now.");
            return;
        }
        client.requestStartGame();
    }

    // สลับสถานะพร้อมของผู้เล่น
    private void toggleReady() {
        if (client != null) {
            client.setReady(!lobbyScreen.isReady());
        }
    }

    // ส่งชุดรูปทรงความลับของผู้ตั้งโจทย์
    private void submitSecret() {
        if (client == null) {
            return;
        }
        List<String> shapes = questionerScreen.getSelectedShapes();
        if (shapes.size() != questionerScreen.getRequiredPicks()) {
            return;
        }
        client.submitSecret(shapes);
        questionerScreen.lockSelection();
    }

    // ส่งคำตอบทายของผู้ทาย
    private void submitGuess() {
        if (client == null) {
            return;
        }
        List<String> shapes = guesserScreen.getGuessedShapes();
        if (shapes.isEmpty() || shapes.size() != guesserScreen.getRequiredPicks()) {
            return;
        }
        client.submitGuess(shapes);
        guesserScreen.markSubmitted();
    }

    // ขอเริ่มทัวร์นาเมนต์ใหม่
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
    // การประมวลผลข้อความจากเซิร์ฟเวอร์
    // =====================================================================

    /** แยกข้อความตามประเภท */
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
            handleToLobby();
        } else if (GameServer.SHOW_SCOREBOARD.equals(type)) {
            handleShowScoreboard();
        } else if (GameServer.TICK.equals(type)) {
            handleTick(args);
        } else if (GameServer.CHAT.equals(type)) {
            String sender = "?";
            if (args.length > 0) {
                sender = args[0];
            }
            lobbyScreen.addChatMessage(sender, arg(args, 1));
        } else if (GameServer.SYSTEM.equals(type)) {
            lobbyScreen.addChatMessage("System", arg(args, 0));
        } else if (GameServer.SERVER_CLOSED.equals(type)) {
            handleServerClosed();
        } else if (GameServer.ERROR.equals(type)) {
            String message = arg(args, 0);
            if (message.isEmpty()) {
                message = "Unknown error";
            }
            showError("Server message", message);
        }
    }

    /** จัดการ JOIN_SUCCESS */
    private void handleJoinSuccess(String[] args) {
        cancelJoinTimeout();
        myName = arg(args, 0);
        isHost = "1".equals(arg(args, 1));
        hostName = myName;

        lobbyScreen.setMyName(myName);
        lobbyScreen.setHost(isHost);
        lobbyScreen.clearChat();
        questionerScreen.setMyName(myName);
        guesserScreen.setMyName(myName);
        scoreboardScreen.setNextEnabled(isHost);

        if (!connectAddress.isEmpty()) {
            lobbyScreen.setAddress(connectAddress);
        }

        joinScreen.setBusy(false);
        String hostTag;
        if (isHost) {
            hostTag = " (host)";
        } else {
            hostTag = "";
        }
        joinScreen.showSuccess("Joined the room as " + myName + hostTag + ".");
        showScreen(SCREEN_LOBBY);
    }

    /** จัดการ JOIN_FAILED */
    private void handleJoinFailed(String[] args) {
        String rawReason = arg(args, 0);
        String reason;
        if (rawReason.isEmpty()) {
            reason = "The host refused the connection.";
        } else {
            reason = rawReason;
        }
        teardownSession();
        joinScreen.setBusy(false);
        joinScreen.showStatus("Could not join: " + reason, true);
        showError("Could not join the room", reason);
        showScreen(SCREEN_JOIN);
    }

    /** จัดการ PLAYER_LIST */
    private void handlePlayerList(String[] args) {
        int count = parseInt(arg(args, 0), 0);
        playerNames.clear();
        readyStates.clear();
        hostName = "";
        for (int i = 0; i < count; i++) {
            int base = 1 + i * 3;
            if (base + 2 >= args.length) {
                break;
            }
            String name = arg(args, base);
            if (name.isEmpty()) {
                continue;
            }
            playerNames.add(name);
            readyStates.put(name, "1".equals(arg(args, base + 1)));
            if ("1".equals(arg(args, base + 2))) {
                hostName = name;
            }
        }
        lobbyScreen.setPlayers(playerNames, readyStates, hostName);
        pushScoreboards();
    }

    /** จัดการ ROUND_INFO */
    private void handleRoundInfo(String[] args) {
        int round = parseInt(arg(args, 0), 1);
        int total = parseInt(arg(args, 1), 0);
        String questioner = arg(args, 2);
        int requiredShapes = parseInt(arg(args, 3), GameServer.MIN_SHAPES);

        roundResultScreen.stopCountdown();
        currentRound = round;
        knownTotalRounds = total;
        roundInProgress = true;

        pushScoreboards();
        if (myName.equals(questioner)) {
            questionerScreen.startRound(round, total, requiredShapes);
            showScreen(SCREEN_QUESTIONER);
        } else {
            guesserScreen.startRound(round, total, questioner, requiredShapes);
            showScreen(SCREEN_GUESSER);
        }
    }

    /** จัดการ SECRET_LOCKED */
    private void handleSecretLocked(String[] args) {
        String questioner = arg(args, 0);
        int count = parseInt(arg(args, 1), 1);
        if (myName.equals(questioner)) {
            questionerScreen.lockSelection();
        } else {
            guesserScreen.onSecretLocked(questioner, count);
        }
    }

    /** จัดการ TICK */
    private void handleTick(String[] args) {
        int tickRound = parseInt(arg(args, 0), -1);
        int remaining = parseInt(arg(args, 1), -1);
        if (!roundInProgress || tickRound != currentRound || remaining < 0) {
            return;
        }
        questionerScreen.setTimeLeft(remaining);
        guesserScreen.setTimeLeft(remaining);
    }

    /** จัดการ GUESS_SUBMITTED */
    private void handleGuessSubmitted(String[] args) {
        String who = arg(args, 0);
        String submitted = arg(args, 1);
        String total = arg(args, 2);
        String lastGuess;
        if (who.isEmpty()) {
            lastGuess = "";
        } else {
            lastGuess = " (last: " + who + ")";
        }
        questionerScreen.setStatus("Guesses in: " + submitted + " / " + total + lastGuess);
        if (!who.equals(myName)) {
            guesserScreen.setStatus("Waiting for the other players... " + submitted + " / " + total);
        }
    }

    /** จัดการ ROUND_RESULT */
    private void handleRoundResult(String[] args) {
        int round = parseInt(arg(args, 0), 1);
        int total = parseInt(arg(args, 1), knownTotalRounds);
        boolean isFinal = "1".equals(arg(args, 2));
        String secretCsv = arg(args, 3);
        String reason = arg(args, 4);
        String questioner = arg(args, 5);
        int playerCount = parseInt(arg(args, 6), 0);

        roundInProgress = false;
        currentRound = round;
        knownTotalRounds = total;

        lastScores.clear();
        String[] names = new String[playerCount];
        int[] deltas = new int[playerCount];
        int[] totals = new int[playerCount];
        String[][] guesses = new String[playerCount][];
        int[] correctCounts = new int[playerCount];
        boolean[] bonuses = new boolean[playerCount];
        for (int i = 0; i < playerCount; i++) {
            int base = 7 + i * 6;
            names[i] = arg(args, base);
            deltas[i] = parseInt(arg(args, base + 1), 0);
            totals[i] = parseInt(arg(args, base + 2), 0);
            guesses[i] = splitCsv(arg(args, base + 3));
            correctCounts[i] = parseInt(arg(args, base + 4), 0);
            bonuses[i] = "1".equals(arg(args, base + 5));
            if (!names[i].isEmpty()) {
                lastScores.put(names[i], totals[i]);
            }
        }

        pushScoreboards();

        // ข้อมูลสกอร์บอร์ดสุดท้าย
        scoreboardScreen.setResults(names, totals, round, total);
        scoreboardScreen.setNextEnabled(isHost);

        // แสดงผลสรุปของรอบนี้
        roundResultScreen.showResult(round, total, isFinal, splitCsv(secretCsv), reason, questioner, names,
                deltas, totals, guesses, correctCounts, bonuses);
        showScreen(SCREEN_ROUND_RESULT);
    }

    /** แยก CSV เป็นอาเรย์ */
    private static String[] splitCsv(String csv) {
        // arg() คืนสตริงว่างแทน null
        if (csv.isEmpty()) {
            return new String[0];
        }
        return csv.split(",");
    }

    /** จัดการ TO_LOBBY */
    private void handleToLobby() {
        roundInProgress = false;
        pushScoreboards();
        showScreen(SCREEN_LOBBY);
    }

    /** จัดการ SHOW_SCOREBOARD */
    private void handleShowScoreboard() {
        roundResultScreen.stopCountdown();
        roundInProgress = false;
        pushScoreboards();
        showScreen(SCREEN_SCOREBOARD);
    }

    /** จัดการเมื่อห้องถูกปิด */
    private void handleServerClosed() {
        endSession("Room closed", "The host closed the room. You have been returned to the join screen.");
    }

    /** จัดการเมื่อการเชื่อมต่อหลุด */
    private void handleDisconnected(String reason) {
        endSession("Disconnected", reason);
    }

    /** จบเซสชันและกลับไปหน้า JoinScreen */
    private void endSession(String title, String message) {
        if (intentionalLeave || alreadyAtJoinScreen()) {
            return;
        }
        teardownSession();
        joinScreen.reset();
        showScreen(SCREEN_JOIN);
        showError(title, message);
    }

    /** ปิดไคลเอนต์และเซิร์ฟเวอร์ */
    private void teardownSession() {
        cancelJoinTimeout();
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

    /** ซิงค์คะแนนและรายชื่อ */
    private void pushScoreboards() {
        String[] names = playerNames.toArray(new String[0]);
        int[] scores = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            Integer value = lastScores.get(names[i]);
            if (value == null) {
                scores[i] = 0;
            } else {
                scores[i] = value.intValue();
            }
        }
        questionerScreen.setScoreboard(names, scores);
        guesserScreen.setScoreboard(names, scores);
    }

    // =====================================================================
    // ฟังก์ชันช่วย
    // =====================================================================

    /** อาร์กิวเมนต์ตามตำแหน่ง (ค่าว่างถ้าไม่มี) */
    private static String arg(String[] args, int index) {
        if (index < 0 || index >= args.length) {
            return "";
        }
        return args[index];
    }

    /** แปลงข้อความเป็นตัวเลข (fallback ถ้าผิดรูปแบบ) */
    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** แสดงข้อผิดพลาด */
    private void showError(String title, String message) {
        AppDialog.showError(this, title, message);
    }

    /** แสดงข้อมูล */
    private void showInfo(String message, String title) {
        AppDialog.showInfo(this, title, message);
    }

    /** Listener ของ GameClient */
    private class NetworkListener implements GameClient.Listener {
        /** ส่งข้อความไปยังตัวจัดการหน้าจอ */
        @Override
        public void onMessage(String type, String[] args) {
            handleMessage(type, args);
        }

        /** แจ้งเมื่อการเชื่อมต่อหลุด */
        @Override
        public void onDisconnected(String reason) {
            handleDisconnected(reason);
        }
    }

    // จุดเริ่มต้นโปรแกรม
    public static void main(String[] args) {
        new MainApp();
    }
}
