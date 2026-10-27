import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * MainApp: หน้าต่างหลักของแอปพลิเคชัน (Master window)
 * ทำหน้าที่เป็นศูนย์กลางถือครองหน้าจอทั้ง 6 หน้าจอ (screens), วัตถุด้านเครือข่าย (GameClient, GameServer)
 * และเป็นจุดเดียวที่จัดการแปลงข้อความที่ได้รับจากเซิร์ฟเวอร์เพื่อสลับหรืออัปเดตหน้าจอ UI
 */
public class MainApp extends MainApp_UI implements JoinScreen.JoinListener {

    // วัตถุหน้าจอทั้ง 6 หน้าจอ
    private final JoinScreen joinScreen;
    private final LobbyScreen lobbyScreen;
    private final QuestionerScreen questionerScreen;
    private final GuesserScreen guesserScreen;
    private final ScoreboardScreen scoreboardScreen;
    private final RoundResultScreen roundResultScreen;

    /** เธรดพูลสำหรับจัดการงานด้านเครือข่าย เพื่อไม่ให้บล็อกการทำงานของ UI Thread */
    private final ExecutorService netExecutor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "MainApp-Net");
        thread.setDaemon(true);
        return thread;
    });

    private GameClient client;          // ไคลเอนต์สำหรับเชื่อมต่อกับเซิร์ฟเวอร์
    private GameServer server;          // เซิร์ฟเวอร์ (สร้างเฉพาะเมื่อเครื่องนี้เป็นโฮสต์)
    private String myName = "";         // ชื่อของผู้เล่นเครื่องนี้
    private boolean isHost;             // สถานะว่าเป็นโฮสต์ของห้องหรือไม่
    private String connectAddress = ""; // ที่อยู่ IP:Port ที่ใช้เชื่อมต่อ
    private boolean intentionalLeave;   // สถานะว่าเป็นการกดออกจากห้องเองโดยเจตนาหรือไม่

    /** ระยะเวลาที่รอการตอบรับ JOIN_SUCCESS / JOIN_FAILED จากโฮสต์ ก่อนจะหมดเวลา (8 วินาที) */
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

    /**
     * คอนสตรักเตอร์เริ่มต้น MainApp: กำหนดขนาดหน้าต่าง สร้างหน้าจอทั้ง 6 และแสดงหน้าจอแรก (JoinScreen)
     */
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
        // หน้าจอที่ 5: หน้าจอตารางคะแนนรวมสุดท้าย (Scoreboard)
        scoreboardScreen = new ScoreboardScreen(event -> requestNewGame(), event -> leaveRoom());
        // หน้าจอที่ 6: หน้าจอสรุปผลของแต่ละรอบ (Round Result)
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

    /** ตรวจสอบว่าหน้าจอปัจจุบันคือหน้าจอ JoinScreen แล้วหรือไม่ */
    private boolean alreadyAtJoinScreen() {
        return SCREEN_JOIN.equals(getCurrentScreen());
    }

    // =====================================================================
    //  อินเตอร์เฟส JoinScreen.JoinListener (ทำงานบน UI Thread)
    // =====================================================================

    /**
     * ดำเนินการเมื่อผู้เล่นกดสร้างห้อง (Host) จากหน้า JoinScreen
     */
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
        netExecutor.submit(() -> startHost(playerName, port));
    }

    /**
     * ดำเนินการเมื่อผู้เล่นกดเข้าร่วมห้อง (Join) จากหน้า JoinScreen
     */
    @Override
    public void onJoinGame(String playerName, String serverIp, int port) {
        if (client != null && client.isConnected()) {
            joinScreen.setBusy(false);
            joinScreen.showStatus("Already connected. Leave the room before joining another.", true);
            return;
        }
        joinScreen.setBusy(true);
        joinScreen.showStatus("Connecting to " + serverIp + ":" + port + "...");
        netExecutor.submit(() -> connectAndJoin(playerName, serverIp, port, false));
    }

    // =====================================================================
    //  การจัดการวงจรชีวิตระบบเครือข่าย (ทำงานบน Network Thread)
    // =====================================================================

    /**
     * เริ่มการทำงานของ GameServer บนเครื่องนี้ จากนั้นเชื่อมต่อ GameClient ในฐานะโฮสต์ผ่าน 127.0.0.1
     */
    private void startHost(String playerName, int port) {
        var newServer = new GameServer(port);
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

        // โฮสต์จะเชื่อมต่อไปยังเซิร์ฟเวอร์ผ่าน Loopback (127.0.0.1) เหมือนกับผู้เล่นคนอื่นๆ
        connectAndJoin(playerName, "127.0.0.1", port, true);
    }

    /**
     * เริ่มการเชื่อมต่อ GameClient ไปยังเซิร์ฟเวอร์ปลายทาง
     */
    private void connectAndJoin(String playerName, String ip, int port, boolean host) {
        var newClient = new GameClient(ip, port, new NetworkListener());
        try {
            newClient.connect();
        } catch (IOException e) {
            String message = describeConnectionFailure(e, ip, port);
            if (host && server != null) {
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
            connectAddress = (lan.isEmpty() ? "127.0.0.1" : lan) + ":" + port;
        } else {
            connectAddress = ip + ":" + port;
        }
        newClient.join(playerName);
        joinScreen.showStatus("Connected. Asking the host to let you in...");
        startJoinTimeout();
    }

    /**
     * เริ่มเวลานับถอยหลังรอการตอบรับการเข้าห้อง หากโฮสต์ไม่ตอบรับภายใน 8 วินาทีจะยกเลิก
     */
    private void startJoinTimeout() {
        cancelJoinTimeout();
        joinTimeout = new Thread(() -> {
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
        }, "MainApp-JoinTimeout");
        joinTimeout.setDaemon(true);
        joinTimeout.start();
    }

    private void cancelJoinTimeout() {
        if (joinTimeout != null) {
            joinTimeout.interrupt();
            joinTimeout = null;
        }
    }

    /**
     * แปลง Exception ของการเชื่อมต่อเป็นข้อความภาษาที่เข้าใจง่าย
     */
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

    /**
     * ออกจากห้อง: ปิดการเชื่อมต่อไคลเอนต์และเซิร์ฟเวอร์ แล้วกลับไปยังหน้า JoinScreen
     */
    private void leaveRoom() {
        intentionalLeave = true;
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

    // ขอเริ่มทัวร์นาเมนต์ใหม่ไปยังเซิร์ฟเวอร์ (สำหรับโฮสต์)
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
    //  การประมวลผลข้อความจากเซิร์ฟเวอร์ (Server message handling)
    // =====================================================================

    /**
     * แยกแยะและจัดการข้อความตามประเภทที่ได้รับจากเซิร์ฟเวอร์
     */
    private void handleMessage(String type, String[] args) {
        if (GameServer.JOIN_SUCCESS.equals(type)) { handleJoinSuccess(args); }
        else if (GameServer.JOIN_FAILED.equals(type)) { handleJoinFailed(args); }
        else if (GameServer.PLAYER_LIST.equals(type)) { handlePlayerList(args); }
        else if (GameServer.ROUND_INFO.equals(type)) { handleRoundInfo(args); }
        else if (GameServer.SECRET_LOCKED.equals(type)) { handleSecretLocked(args); }
        else if (GameServer.GUESS_ACCEPTED.equals(type)) { guesserScreen.markSubmitted(); }
        else if (GameServer.GUESS_SUBMITTED.equals(type)) { handleGuessSubmitted(args); }
        else if (GameServer.ROUND_RESULT.equals(type)) { handleRoundResult(args); }
        else if (GameServer.TO_LOBBY.equals(type)) { handleToLobby(); }
        else if (GameServer.SHOW_SCOREBOARD.equals(type)) { handleShowScoreboard(); }
        else if (GameServer.TICK.equals(type)) { handleTick(args); }
        else if (GameServer.CHAT.equals(type)) { lobbyScreen.addChatMessage(args.length > 0 ? args[0] : "?", arg(args, 1)); }
        else if (GameServer.SYSTEM.equals(type)) { lobbyScreen.addChatMessage("System", arg(args, 0)); }
        else if (GameServer.SERVER_CLOSED.equals(type)) { handleServerClosed(); }
        else if (GameServer.ERROR.equals(type)) { showError("Server message", arg(args, 0).isEmpty() ? "Unknown error" : arg(args, 0)); }
    }

    /** จัดการเมื่อเข้าห้องสำเร็จ (JOIN_SUCCESS) สลับไปหน้าจอ Lobby */
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
        joinScreen.showSuccess("Joined the room as " + myName + (isHost ? " (host)" : "") + ".");
        showScreen(SCREEN_LOBBY);
    }

    /** จัดการเมื่อเข้าห้องไม่สำเร็จ (JOIN_FAILED) */
    private void handleJoinFailed(String[] args) {
        String reason = arg(args, 0).isEmpty() ? "The host refused the connection." : arg(args, 0);
        teardownSession();
        joinScreen.setBusy(false);
        joinScreen.showStatus("Could not join: " + reason, true);
        showError("Could not join the room", reason);
        showScreen(SCREEN_JOIN);
    }

    /** จัดการข้อมูลรายชื่อและสถานะความพร้อมของผู้เล่น (PLAYER_LIST) */
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

    /**
     * จัดการข้อมูลรอบใหม่ (ROUND_INFO) สลับไปยังหน้า Questioner หรือ Guesser ตามบทบาท
     */
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

    /** จัดการเมื่อ Questioner ล็อกรูปทรงเรียบร้อยแล้ว (SECRET_LOCKED) */
    private void handleSecretLocked(String[] args) {
        String questioner = arg(args, 0);
        int count = parseInt(arg(args, 1), 1);
        if (myName.equals(questioner)) {
            questionerScreen.lockSelection();
        } else {
            guesserScreen.onSecretLocked(questioner, count);
        }
    }

    /** จัดการเวลานับถอยหลัง (TICK) จากเซิร์ฟเวอร์ */
    private void handleTick(String[] args) {
        int tickRound = parseInt(arg(args, 0), -1);
        int remaining = parseInt(arg(args, 1), -1);
        if (!roundInProgress || tickRound != currentRound || remaining < 0) {
            return;
        }
        questionerScreen.setTimeLeft(remaining);
        guesserScreen.setTimeLeft(remaining);
    }

    /** จัดการเมื่อมีผู้เล่นส่งคำตอบทาย (GUESS_SUBMITTED) */
    private void handleGuessSubmitted(String[] args) {
        String who = arg(args, 0);
        String submitted = arg(args, 1);
        String total = arg(args, 2);
        questionerScreen.setStatus("Guesses in: " + submitted + " / " + total
                + (who.isEmpty() ? "" : " (last: " + who + ")"));
        if (!who.equals(myName)) {
            guesserScreen.setStatus("Waiting for the other players... " + submitted + " / " + total);
        }
    }

    /**
     * จัดการผลสรุปของรอบ (ROUND_RESULT) และสลับไปยังหน้าจอ RoundResultScreen
     */
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
        var names = new String[playerCount];
        var deltas = new int[playerCount];
        var totals = new int[playerCount];
        var guesses = new String[playerCount][];
        var correctCounts = new int[playerCount];
        var bonuses = new boolean[playerCount];
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

        // อัปเดตข้อมูลตารางคะแนนสุดท้ายเตรียมไว้
        scoreboardScreen.setResults(names, totals, round, total);
        scoreboardScreen.setNextEnabled(isHost);

        // แสดงผลสรุปของรอบนี้
        roundResultScreen.showResult(round, total, isFinal, splitCsv(secretCsv), reason, questioner, names,
                deltas, totals, guesses, correctCounts, bonuses);
        showScreen(SCREEN_ROUND_RESULT);
    }

    /** แยกชุดชื่อรูปทรงที่คั่นด้วยจุลภาค */
    private static String[] splitCsv(String csv) {
        return (csv == null || csv.isEmpty()) ? new String[0] : csv.split(",");
    }

    /** กลับไปยังหน้าห้องพักคอย (TO_LOBBY) */
    private void handleToLobby() {
        roundInProgress = false;
        pushScoreboards();
        showScreen(SCREEN_LOBBY);
    }

    /**
     * เซิร์ฟเวอร์แจ้งว่าจบทัวร์นาเมนต์แล้ว ให้เปิดหน้าตารางคะแนนรวมสุดท้าย (SHOW_SCOREBOARD)
     */
    private void handleShowScoreboard() {
        roundResultScreen.stopCountdown();
        roundInProgress = false;
        pushScoreboards();
        showScreen(SCREEN_SCOREBOARD);
    }

    private void handleServerClosed() {
        endSession("Room closed", "The host closed the room. You have been returned to the join screen.");
    }

    private void handleDisconnected(String reason) {
        endSession("Disconnected", reason);
    }

    /**
     * ปิดเซสชันเมื่อการเชื่อมต่อสิ้นสุดลง แล้วกลับไปหน้า JoinScreen
     */
    private void endSession(String title, String message) {
        if (intentionalLeave || alreadyAtJoinScreen()) {
            return;
        }
        teardownSession();
        joinScreen.reset();
        showScreen(SCREEN_JOIN);
        showError(title, message);
    }

    /** ปิดไคลเอนต์และเซิร์ฟเวอร์ พร้อมล้างสถานะห้อง */
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

    /** ซิงค์คะแนนและรายชื่อผู้เล่นไปยังหน้าจอในเกมทั้งสองหน้า (Questioner และ Guesser) */
    private void pushScoreboards() {
        String[] names = playerNames.toArray(new String[0]);
        var scores = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            Integer value = lastScores.get(names[i]);
            scores[i] = (value == null) ? 0 : value.intValue();
        }
        questionerScreen.setScoreboard(names, scores);
        guesserScreen.setScoreboard(names, scores);
    }

    // =====================================================================
    //  ฟังก์ชันช่วยและการแสดงผลกล่องข้อความ (Small Helpers)
    // =====================================================================

    private static String arg(String[] args, int index) {
        return (index < 0 || index >= args.length) ? "" : args[index];
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void showError(String title, String message) {
        AppDialog.showError(this, title, message);
    }

    private void showInfo(String message, String title) {
        AppDialog.showInfo(this, title, message);
    }

    /** คลาสรับคอลแบ็กข้อความจาก GameClient */
    private class NetworkListener implements GameClient.Listener {
        @Override
        public void onMessage(String type, String[] args) {
            handleMessage(type, args);
        }

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
