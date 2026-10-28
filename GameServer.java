import java.io.*;
import java.net.*;
import java.util.*;

/** เซิร์ฟเวอร์หลักของเกมสำหรับเล่นผ่าน LAN */
public class GameServer {

    // ===== การกำหนดค่าพื้นฐานของเกม (Configuration) =====
    public static final int DEFAULT_PORT = 5000;         // พอร์ตเริ่มต้น
    public static final int MIN_PLAYERS = 3;             // จำนวนผู้เล่นขั้นต่ำ (โฮสต์ + ผู้เล่นอื่นอีก 1 คน)
    public static final int MIN_SHAPES = 1;              // จำนวนรูปทรงขั้นต่ำในแต่ละรอบ
    public static final int MAX_SHAPES = 5;              // จำนวนรูปทรงสูงสุดในแต่ละรอบ
    public static final int ROUND_SECONDS = 30;          // เวลาในการทายแต่ละรอบ (เริ่มนับเมื่อ Questioner ล็อกรูป)
    public static final int BONUS_WINDOW_SECONDS = 10;   // ช่วงเวลาได้โบนัส +2 (ทายถูกหมดภายใน 10 วินาทีแรก)
    public static final int INTERMISSION_SECONDS = 10;   // เวลานับถอยหลังช่วงพักรอบก่อนเริ่มรอบถัดไป
    public static final int MAX_NAME_LENGTH = 16;        // ความยาวสูงสุดของชื่อผู้เล่น
    /** จำนวนรอบต่อคนในบทบาท Questioner */
    public static final int ROUNDS_PER_PLAYER = 2;

    /** ชื่อรูปทรงทั้ง 8 แบบ */
    public static final String[] SHAPE_NAMES = {
        "Circle", "Triangle", "Rectangle", "Oval", "Hexagon", "Diamond", "Star", "Square"
    };

    // ===== ประเภทข้อความตามโปรโตคอล (Protocol Message Types) ใช้ร่วมกันระหว่าง Server และ Client =====
    public static final String JOIN = "JOIN", JOIN_SUCCESS = "JOIN_SUCCESS", JOIN_FAILED = "JOIN_FAILED";
    public static final String PLAYER_LIST = "PLAYER_LIST", READY = "READY", START_GAME = "START_GAME";
    public static final String ROUND_INFO = "ROUND_INFO", TICK = "TICK", SECRET = "SECRET";
    public static final String SECRET_LOCKED = "SECRET_LOCKED", GUESS = "GUESS", GUESS_ACCEPTED = "GUESS_ACCEPTED";
    public static final String GUESS_SUBMITTED = "GUESS_SUBMITTED", ROUND_RESULT = "ROUND_RESULT";
    public static final String NEW_GAME = "NEW_GAME", LEAVE_ROOM = "LEAVE", TO_LOBBY = "TO_LOBBY";
    public static final String SHOW_SCOREBOARD = "SHOW_SCOREBOARD";
    public static final String CHAT = "CHAT", SYSTEM = "SYSTEM";
    public static final String SERVER_CLOSED = "SERVER_CLOSED", ERROR = "ERROR";

    // ===== ตัวแปรการทำงานของเซิร์ฟเวอร์ =====
    private final int port;                      // หมายเลขพอร์ตที่เซิร์ฟเวอร์เปิดใช้งาน
    private String startError = "";              // ข้อความผิดพลาดล่าสุดจากการเปิดพอร์ต
    private ServerSocket serverSocket;           // ซ็อกเก็ตฝั่งเซิร์ฟเวอร์
    private boolean running;            // สถานะว่าเซิร์ฟเวอร์กำลังทำงานอยู่หรือไม่
    private Thread acceptThread;                 // เธรดสำหรับรอรับการเชื่อมต่อจาก Client ใหม่

    /** ล็อกของสถานะเซิร์ฟเวอร์ */
    private final Object lock = new Object();
    private final List<ClientHandler> clients = new ArrayList<>();   // รายการ Client เรียงตามลำดับเข้าห้อง (ดัชนี 0 คือโฮสต์)
    private final Map<String, Boolean> ready = new LinkedHashMap<>(); // สถานะความพร้อมของผู้เล่นแต่ละคน
    private final Map<String, Integer> scores = new LinkedHashMap<>(); // คะแนนรวมสะสมของผู้เล่น
    private final Map<String, Integer> deltas = new LinkedHashMap<>(); // คะแนนที่ได้เพิ่มในรอบปัจจุบัน
    private final Map<String, String[]> guesses = new LinkedHashMap<>(); // คำตอบรูปทรงที่แต่ละคนทายในรอบนี้
    private final Map<String, Long> guessTimes = new LinkedHashMap<>(); // เวลาที่แต่ละคนส่งคำตอบ (มิลลิวินาที)
    private final Map<String, Integer> correctCounts = new LinkedHashMap<>(); // จำนวนรูปที่ทายถูกในรอบนี้
    private final Map<String, Boolean> bonusFlags = new LinkedHashMap<>(); // สถานะว่าได้คะแนนโบนัสความเร็วหรือไม่

    private int round;                    // รอบที่กำลังเล่นอยู่ (0 = ยังไม่เริ่มรอบใด)
    private int totalRounds;              // จำนวนรอบทั้งหมด (จำนวนผู้เล่น * 2) ล็อกเมื่อเริ่มทัวร์นาเมนต์
    private String questioner;            // ชื่อผู้เล่นที่เป็น Questioner ในรอบปัจจุบัน
    private String[] secret;              // ชุดรูปทรงความลับในรอบปัจจุบัน
    private boolean secretLocked;         // สถานะว่า Questioner ยืนยันรูปทรงแล้วหรือไม่
    private int expectedShapes;           // จำนวนรูปทรงเป้าหมายของรอบนี้ (1..MAX_SHAPES)
    private boolean tournamentActive;     // มีการแข่งทัวร์นาเมนต์ดำเนินอยู่หรือไม่ (บล็อกไม่ให้คนใหม่เข้าห้อง)
    private boolean roundRunning;         // มีรอบการเล่นกำลังดำเนินอยู่หรือไม่
    private boolean roundEnded;           // ตัวป้องกันไม่ให้จบรอบเดียวกันซ้ำสองครั้ง
    private long roundStartMillis;        // เวลาที่เริ่มนับรอบ (มิลลิวินาที เมื่อ Questioner กดยืนยัน)
    private RoundTimerThread roundTimer;  // เธรดนับถอยหลังของรอบ
    private Thread intermission;          // เธรดนับถอยหลังช่วงพักรอบ (INTERMISSION_SECONDS) แล้วเริ่มรอบใหม่
    private final Random random = new Random(); // ออบเจกต์สำหรับสุ่ม

    // ===== การสลับบทบาท Questioner (หนึ่งรอบใหญ่คือทุกคนได้เป็น Questioner ครบคนละครั้ง) =====
    private int cycleSize;                // จำนวนผู้เล่นต่อหนึ่งรอบใหญ่ (Cycle)
    private int cycleIndex = -1;          // ดัชนีรอบใหญ่ปัจจุบัน (-1 = ยังไม่เริ่ม)
    private int lastCycleShapes;          // จำนวนรูปทรงที่ใช้ในรอบใหญ่ก่อนหน้า
    private final List<String> cycleOrder = new ArrayList<>(); // ลำดับผู้เล่นที่รอเป็น Questioner ในรอบใหญ่นี้

    /** สร้างเซิร์ฟเวอร์สำหรับพอร์ตที่ระบุ */
    public GameServer(int port) {
        this.port = port;
    }

    // =====================================================================
    //  วงจรการทำงานของเซิร์ฟเวอร์ (Lifecycle)
    // =====================================================================

    /** เปิดพอร์ตและเริ่มรับการเชื่อมต่อ (คืนค่า true เมื่อสำเร็จ) */
    public boolean start() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            startError = e.getMessage();
            System.out.println("[server] could not open port " + port + ": " + startError);
            return false;
        }
        running = true;
        acceptThread = new Thread(new Runnable() {
            /** เธรดรับผู้เล่นใหม่ */
            @Override
            public void run() {
                acceptLoop();
            }
        }, "GameServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return true;
    }

    /** ข้อความผิดพลาดจากการเรียก start() ครั้งล่าสุด */
    public String getStartError() {
        return startError;
    }

    // สถานะการทำงานของเซิร์ฟเวอร์
    public boolean isRunning() {
        return running;
    }

    /** ปิดเซิร์ฟเวอร์และหยุดทุกเธรด */
    public void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        stopRoundTimer();
        stopIntermission();
        broadcast(SERVER_CLOSED);

        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }

        List<ClientHandler> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(clients);
            clients.clear();
        }
        for (ClientHandler client : snapshot) {
            client.closeQuietly();
        }
        acceptThread.interrupt();
    }

    /** ลูปหลักของเซิร์ฟเวอร์ */
    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                new ClientHandler(socket).start();
            } catch (IOException e) {
                if (running) {
                    System.out.println("[server] accept failed: " + e.getMessage());
                }
            }
        }
    }

    // =====================================================================
    //  การกระจายข้อความ (Broadcasting)
    // =====================================================================

    /** ข้อความประเภทเดียว */
    private void broadcast(String type) {
        broadcast(type, new String[0]);
    }

    /** ข้อความหนึ่งค่า */
    private void broadcast(String type, String arg) {
        broadcast(type, new String[] {arg});
    }

    /** ข้อความสองค่า */
    private void broadcast(String type, String arg1, String arg2) {
        broadcast(type, new String[] {arg1, arg2});
    }

    /** ส่ง GameMessage ให้ทุกคน */
    private void broadcast(String type, String[] args) {
        broadcast(new GameMessage(type, args));
    }

    /** ส่งข้อความให้ทุกคนในห้อง */
    private void broadcast(GameMessage message) {
        for (ClientHandler client : clientsSnapshot()) {
            client.send(message);
        }
    }

    /** กระจายรายชื่อและสถานะพร้อม */
    private void broadcastPlayerList() {
        String[] payload;
        synchronized (lock) {
            payload = buildPlayerListPayload();
        }
        broadcast(new GameMessage(PLAYER_LIST, payload));
    }

    // Snapshot รายชื่อ Client
    private List<ClientHandler> clientsSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(clients);
        }
    }

    /** ข้อมูล PLAYER_LIST (ผู้เรียกต้องถือ lock) */
    private String[] buildPlayerListPayload() {
        ArrayList<String> out = new ArrayList<String>();
        out.add(String.valueOf(clients.size()));
        for (ClientHandler client : clients) {
            out.add(client.playerName);

            if (ready.get(client.playerName).booleanValue()) {
                out.add("1");
            } else {
                out.add("0");
            }

            String hostFlag;
            if (client == clients.get(0)) {
                hostFlag = "1";
            } else {
                hostFlag = "0";
            }
            out.add(hostFlag);
        }
        return out.toArray(new String[0]);
    }

    // =====================================================================
    //  การจัดการผู้เล่นเข้าและออกจากห้อง (Join and Leave)
    // =====================================================================

    /** ClientHandler ตามชื่อ (ผู้เรียกต้องถือ lock) */
    private ClientHandler findByNameLocked(String name) {
        for (ClientHandler client : clients) {
            if (client.playerName.equalsIgnoreCase(name)) {
                return client;
            }
        }
        return null;
    }

    /** แปลงชื่อรูปทรงเป็นรูปแบบมาตรฐาน (null = ไม่ถูกต้อง/ซ้ำ) */
    private static String[] normalizeShapes(String[] raw, int expectedCount) {
        if (raw.length != expectedCount) {
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
                return null;   // ไม่พบชื่อรูปทรงที่ถูกต้อง
            }
            for (int j = 0; j < i; j++) {
                if (canonical[j].equals(match)) {
                    return null;   // มีรูปทรงซ้ำกัน
                }
            }
            canonical[i] = match;
        }
        return canonical;
    }

    /** จัดการคำขอ JOIN */
    private void handleJoin(ClientHandler handler, String rawName) {
        String name = rawName.trim();

        if (name.isEmpty()) {
            handler.send(JOIN_FAILED, "Player name cannot be empty.");
            handler.closeQuietly();
            return;
        }

        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }

        boolean isHost;
        synchronized (lock) {
            // ตรวจสอบชื่อซ้ำ
            if (findByNameLocked(name) != null) {
                handler.send(JOIN_FAILED, "That name is already taken in this room.");
                handler.closeQuietly();
                return;
            }

            // สถานะทัวร์นาเมนต์
            if (tournamentActive) {
                handler.send(JOIN_FAILED, "A tournament is in progress. Please wait until it finishes.");
                handler.closeQuietly();
                return;
            }

            handler.playerName = name;
            handler.joined = true;
            clients.add(handler);
            ready.put(name, false);
            // คะแนนเริ่มต้นของผู้เล่นใหม่
            scores.put(name, 0);
            isHost = clients.get(0) == handler;
        }

        String hostFlag;
        if (isHost) {
            hostFlag = "1";
        } else {
            hostFlag = "0";
        }
        handler.send(JOIN_SUCCESS, name, hostFlag);
        broadcastPlayerList();
        broadcast(SYSTEM, name + " joined the room.");
    }

    /** จัดการผู้เล่นออกจากห้อง */
    private void handleDisconnect(ClientHandler handler) {
        boolean wasJoined;
        boolean wasHost;
        String name;
        synchronized (lock) {
            wasJoined = handler.joined;
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

        // โฮสต์ออกจากห้อง
        if (wasHost) {
            broadcast(SYSTEM, name + " (host) closed the room.");
            shutdown();
            return;
        }

        // สถานะการแข่งขันหลังมีคนออก
        boolean finishTournament = false, restartRound = false, endRoundNow = false;
        synchronized (lock) {
            if (tournamentActive) {
                if (clients.size() < MIN_PLAYERS) {
                    finishTournament = true; // ผู้เล่นเหลือน้อยกว่าขั้นต่ำ ให้จบทัวร์นาเมนต์
                } else if (roundRunning) {
                    if (name.equals(questioner)) {
                        restartRound = true;   // ถ้าคนตั้งโจทย์ออก ให้เริ่มรอบนี้ใหม่ด้วย Questioner คนใหม่
                    } else if (countGuessersLocked() == 0) {
                        finishTournament = true;
                    } else if (countSubmittedLocked() >= countGuessersLocked()) {
                        endRoundNow = true;    // ถ้าทุกคนที่เหลือส่งคำตอบครบแล้ว ให้จบรอบทันที
                    }
                }
            }
        }

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
    //  ขั้นตอนการดำเนินเกม (Game flow)
    // =====================================================================

    /** จัดการคำขอ START_GAME */
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

            newTournament = !tournamentActive;
            if (newTournament) {
                totalRounds = clients.size() * ROUNDS_PER_PLAYER;
                resetScoresLocked();
                cycleSize = clients.size();
                tournamentActive = true;
                round = 0;
            }
        }
        if (newTournament) {
            broadcast(SYSTEM, "The tournament is starting! " + totalRounds + " rounds.");
        }
        nextRound();
    }

    /** จัดการคำขอ NEW_GAME */
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
                ready.put(client.playerName, false);
            }
        }
        stopIntermission();
        stopRoundTimer();
        broadcast(TO_LOBBY);
        broadcastPlayerList();
    }

    /** สถานะพร้อมของผู้เล่นคนอื่น */
    private boolean othersReadyLocked(ClientHandler host) {
        for (ClientHandler client : clients) {
            if (client != host) {
                if (!ready.get(client.playerName).booleanValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    // รีเซ็ตคะแนนและสถานะรอบ
    private void resetScoresLocked() {
        round = 0;
        for (ClientHandler client : clients) {
            scores.put(client.playerName, 0);
            deltas.put(client.playerName, 0);
        }
        questioner = null;
        secret = null;
        secretLocked = false;
        expectedShapes = 0;
        cycleSize = 0;
        cycleIndex = -1;
        lastCycleShapes = 0;
        cycleOrder.clear();
        guesses.clear();
        guessTimes.clear();
        correctCounts.clear();
        bonusFlags.clear();
    }

    /** เริ่มรอบถัดไป */
    private void nextRound() {
        stopIntermission();
        stopRoundTimer();
        String selectedQuestioner;
        int currentRound, lockedTotal, shapesThisRound;
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
            guesses.clear();
            guessTimes.clear();
            deltas.clear();
            correctCounts.clear();
            bonusFlags.clear();

            // รอบใหญ่ (Cycle) ใหม่
            int cycle;
            if (cycleSize > 0) {
                cycle = (round - 1) / cycleSize;
            } else {
                cycle = 0;
            }
            if (cycle != cycleIndex) {
                startNewCycleLocked(cycle);
            }
            expectedShapes = lastCycleShapes;

            // Questioner คนถัดไป (ไม่เป็น null)
            selectedQuestioner = takeNextQuestionerLocked();
            questioner = selectedQuestioner;
            currentRound = round;
            lockedTotal = totalRounds;
            shapesThisRound = expectedShapes;
            for (ClientHandler client : clients) {
                ready.put(client.playerName, false);
            }
        }
        // ประกาศข้อมูลรอบใหม่
        broadcast(ROUND_INFO, new String[] {
            String.valueOf(currentRound),
            String.valueOf(lockedTotal),
            selectedQuestioner,
            String.valueOf(shapesThisRound)
        });
    }

    /** เริ่มรอบใหญ่ใหม่ */
    private void startNewCycleLocked(int cycle) {
        cycleIndex = cycle;
        lastCycleShapes = drawCycleShapeCountLocked();
        cycleOrder.clear();
        ArrayList<String> names = new ArrayList<String>();
        for (ClientHandler client : clients) {
            names.add(client.playerName);
        }
        Collections.shuffle(names, random);
        cycleOrder.addAll(names);
    }

    /** จำนวนรูปทรงของรอบใหญ่ */
    private int drawCycleShapeCountLocked() {
        int range = MAX_SHAPES - MIN_SHAPES + 1;
        int count = MIN_SHAPES + random.nextInt(range);
        if (count == lastCycleShapes) {
            int offset = count - MIN_SHAPES;
            offset = (offset + 1 + random.nextInt(range - 1)) % range;
            count = MIN_SHAPES + offset;
        }
        return count;
    }

    /** Questioner คนถัดไป */
    private String takeNextQuestionerLocked() {
        while (!cycleOrder.isEmpty()) {
            String candidate = cycleOrder.remove(0);
            if (findByNameLocked(candidate) != null) {
                return candidate;
            }
        }
        if (clients.isEmpty()) {
            return null;
        }
        return clients.get(0).playerName;
    }

    /** เริ่มรอบใหม่หลัง Questioner ออก */
    private void restartRoundAfterQuestionerLeft() {
        boolean restart;
        synchronized (lock) {
            restart = tournamentActive && clients.size() >= MIN_PLAYERS;
            if (restart) {
                round--;   // คืนรอบเดิม
                roundRunning = false;
            }
        }
        if (restart) {
            broadcast(SYSTEM, "The questioner left. Restarting the round with a new questioner.");
            nextRound();
        }
    }

    // =====================================================================
    //  เธรดนับถอยหลังของรอบ (Round timer thread)
    // =====================================================================

    /** เธรดนับถอยหลังของรอบ */
    private class RoundTimerThread extends Thread {
        private final int roundNumber;
        private final long deadlineNanos;
        private volatile boolean stopped;

        /** ข้อมูลเธรดนับถอยหลัง */
        RoundTimerThread(int roundNumber, int seconds) {
            super("GameServer-RoundTimer");
            setDaemon(true);
            this.roundNumber = roundNumber;
            this.deadlineNanos = System.nanoTime() + seconds * 1000000000L;
        }

        /** หยุดเธรดนับถอยหลัง */
        void requestStop() {
            stopped = true;
            interrupt();
        }

        /** ลูปส่ง TICK */
        @Override
        public void run() {
            while (!stopped) {
                int remaining = remainingSeconds();
                broadcast(TICK, String.valueOf(roundNumber), String.valueOf(remaining));
                if (remaining <= 0) {
                    endRound("time"); // หมดเวลา
                    return;
                }
                long sleepyMillis = millisToNextWholeSecond();
                long sleepMillis;
                if (sleepyMillis <= 0) {
                    sleepMillis = 50;
                } else {
                    sleepMillis = sleepyMillis;
                }
                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        /** วินาทีที่เหลือ (ปัดขึ้น) */
        private int remainingSeconds() {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0) {
                return 0;
            }

            long seconds = nanos / 1000000000L;
            long remainder = nanos % 1000000000L;
            if (remainder > 0) {
                seconds = seconds + 1;
            }
            return (int) seconds;
        }

        // เวลาถึงวินาทีถัดไป
        private long millisToNextWholeSecond() {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0) {
                return 0;
            }

            long remainder = nanos % 1000000000L;
            if (remainder == 0) {
                return 1000;
            }
            return remainder / 1000000L;
        }
    }

    /** เริ่มตัวนับรอบใหม่ */
    private void startRoundTimer(int roundNumber, int seconds) {
        stopRoundTimer();
        RoundTimerThread timer = new RoundTimerThread(roundNumber, seconds);
        synchronized (lock) {
            roundTimer = timer;
        }
        timer.start();
    }

    /** หยุดตัวนับรอบปัจจุบัน */
    private void stopRoundTimer() {
        RoundTimerThread timer;
        synchronized (lock) {
            timer = roundTimer;
            roundTimer = null;
        }

        if (timer == null) {
            return;
        }

        timer.requestStop();

        if (timer != Thread.currentThread()) {
            try {
                timer.join(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // =====================================================================
    //  การกระทำในรอบการเล่น (Round actions)
    // =====================================================================

    /** จัดการ SECRET จาก Questioner */
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
                String shapeWord;
                if (expectedShapes == 1) {
                    shapeWord = "";
                } else {
                    shapeWord = "s";
                }
                sender.send(ERROR, "Pick exactly " + expectedShapes + " shape"
                        + shapeWord + " for this round.");
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

            // เริ่มนับเวลาเมื่อล็อกรูปทรง
            roundStartMillis = System.currentTimeMillis();
        }
        broadcast(SECRET_LOCKED, questioner, String.valueOf(count));
        String shapeWord;
        if (count == 1) {
            shapeWord = "";
        } else {
            shapeWord = "s";
        }
        broadcast(SYSTEM, "The questioner locked in " + count + " shape" + shapeWord
                + ". Start guessing!");
        startRoundTimer(currentRound, ROUND_SECONDS);
    }

    /** จัดการ GUESS จากผู้ทาย */
    private void handleGuess(ClientHandler sender, String[] shapes) {
        int submitted;
        int totalGuessers;
        String[] canonical;
        synchronized (lock) {
            if (!roundRunning) {
                sender.send(ERROR, "No round is in progress.");
                return;
            }

            // secretLocked = true แปลว่า secret ไม่เป็น null
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

            if (shapes.length != secret.length) {
                String shapeWord;
                if (secret.length == 1) {
                    shapeWord = "";
                } else {
                    shapeWord = "s";
                }
                sender.send(ERROR, "Pick exactly " + secret.length + " shape"
                        + shapeWord + " for this round.");
                return;
            }

            canonical = normalizeShapes(shapes, secret.length);
            if (canonical == null) {
                sender.send(ERROR, "Those shapes are not valid (unknown or repeated).");
                return;
            }

            guesses.put(sender.playerName, canonical);
            guessTimes.put(sender.playerName, System.currentTimeMillis());
            submitted = countSubmittedLocked();
            totalGuessers = countGuessersLocked();
        }
        sender.send(GUESS_ACCEPTED);
        broadcast(GUESS_SUBMITTED, new String[] {
            sender.playerName,
            String.valueOf(submitted),
            String.valueOf(totalGuessers)
        });
        // ผู้ทายส่งครบทุกคน
        if (submitted >= totalGuessers) {
            endRound("all");
        }
    }

    /** จำนวนผู้ทายทั้งหมด */
    private int countGuessersLocked() {
        int count = 0;

        for (ClientHandler client : clients) {
            if (!client.playerName.equals(questioner)) {
                count++;
            }
        }

        return count;
    }

    /** จำนวนผู้ทายที่ส่งแล้ว */
    private int countSubmittedLocked() {
        int count = 0;

        for (String name : guesses.keySet()) {
            if (!name.equals(questioner)) {
                count++;
            }
        }

        return count;
    }

    /** จบรอบและส่งผล ROUND_RESULT */
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
        // เริ่มพักระหว่างรอบ
        startIntermission(isFinal);
    }

    /** จบทัวร์นาเมนต์ก่อนกำหนด */
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
    //  การพักระหว่างรอบ (Intermission)
    // =====================================================================

    /** พักระหว่างรอบ */
    private void startIntermission(boolean isFinal) {
        stopIntermission();

        // เธรดพักระหว่างรอบ
        Thread thread = new Thread(new Runnable() {
            /** รอเวลาพักแล้วเริ่มรอบถัดไป */
            @Override
            public void run() {
                try {
                    Thread.sleep(INTERMISSION_SECONDS * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                if (!running) {
                    return;
                }

                if (isFinal) {
                    broadcast(SHOW_SCOREBOARD); // เปิดหน้าสกอร์บอร์ดสุดท้าย
                } else {
                    nextRound(); // เริ่มรอบถัดไป
                }
            }
        }, "GameServer-Intermission");

        thread.setDaemon(true);
        synchronized (lock) {
            intermission = thread;
        }
        thread.start();
    }

    /** ยกเลิกเธรดพักระหว่างรอบ */
    private void stopIntermission() {
        Thread thread;
        synchronized (lock) {
            thread = intermission;
            intermission = null;
        }
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
    }

    // =====================================================================
    //  การคำนวณคะแนน (Scoring)
    // =====================================================================

    /** คำนวณคะแนนของรอบ (+1 ต่อรูปที่ถูก, +2 โบนัส) */
    private void computeScoresLocked() {
        deltas.clear();
        correctCounts.clear();
        bonusFlags.clear();

        int expected;
        if (secret == null) {
            expected = 0;
        } else {
            expected = secret.length;
        }

        for (ClientHandler client : clients) {
            String name = client.playerName;
            int correct = 0;
            int delta = 0;
            boolean bonus = false;

            String[] guess;
            if (expected == 0 || name.equals(questioner)) {
                guess = null;
            } else {
                guess = guesses.get(name);
            }

            if (guess != null) {
                correct = countMatching(secret, guess);
                delta = correct;

                Long submittedAt = guessTimes.get(name);
                long elapsedSeconds;
                if (submittedAt == null) {
                    elapsedSeconds = 0;
                } else {
                    elapsedSeconds = (submittedAt.longValue() - roundStartMillis) / 1000;
                }

                bonus = (correct == expected && elapsedSeconds <= BONUS_WINDOW_SECONDS);

                if (bonus) {
                    delta += 2; // เพิ่มโบนัสความเร็ว
                }
            }

            deltas.put(name, delta);
            correctCounts.put(name, correct);
            bonusFlags.put(name, bonus);
        }

        // รวมคะแนนเข้ากับคะแนนสะสม
        for (Map.Entry<String, Integer> entry : deltas.entrySet()) {
            Integer current = scores.get(entry.getKey());
            int baseTotal;
            if (current == null) {
                baseTotal = 0;
            } else {
                baseTotal = current.intValue();
            }
            int total = baseTotal + entry.getValue().intValue();
            scores.put(entry.getKey(), total);
        }
    }

    // จำนวนรูปที่ตรงกัน
    private int countMatching(String[] target, String[] guess) {
        int matches = 0;
        // target ไม่เป็น null
        for (String wanted : target) {
            for (String picked : guess) {
                if (wanted.equalsIgnoreCase(picked)) {
                    matches++;
                    break;
                }
            }
        }
        return matches;
    }

    /** เพย์โหลด ROUND_RESULT: round, total, final, secret, reason, questioner, playerCount, ข้อมูลผู้เล่น */
    private String[] buildRoundResultPayload(int roundNumber, boolean isFinal, String reason) {
        ArrayList<String> out = new ArrayList<String>();
        out.add(String.valueOf(roundNumber));
        out.add(String.valueOf(totalRounds));
        String finalFlag;
        if (isFinal) {
            finalFlag = "1";
        } else {
            finalFlag = "0";
        }
        out.add(finalFlag);
        String secretCsv;
        if (secret == null) {
            secretCsv = "";
        } else {
            secretCsv = String.join(",", secret);
        }
        out.add(secretCsv);
        // reason ไม่เป็น null
        out.add(reason);
        String questionerName;
        if (questioner == null) {
            questionerName = "";
        } else {
            questionerName = questioner;
        }
        out.add(questionerName);
        out.add(String.valueOf(clients.size()));
        for (ClientHandler client : clients) {
            String name = client.playerName;
            // คะแนนถูกเติมครบทุกคนแล้ว
            String[] guess = guesses.get(name);
            int deltaValue = deltas.get(name).intValue();
            int totalValue = scores.get(name).intValue();
            int correctValue = correctCounts.get(name).intValue();

            out.add(name);
            out.add(String.valueOf(deltaValue));
            out.add(String.valueOf(totalValue));
            String guessCsv;
            if (guess == null) {
                guessCsv = "";
            } else {
                guessCsv = String.join(",", guess);
            }
            out.add(guessCsv);
            out.add(String.valueOf(correctValue));

            // bonus ถูกเติมครบทุกคนแล้ว
            if (bonusFlags.get(name).booleanValue()) {
                out.add("1");
            } else {
                out.add("0");
            }
        }
        return out.toArray(new String[0]);
    }

    // =====================================================================
    //  ระบบข้อความแชต (Chat)
    // =====================================================================

    /** ตรวจและส่งข้อความแชต */
    private void handleChat(ClientHandler sender, String text) {
        String clean = text.trim();
        if (clean.isEmpty()) {
            return;
        }
        if (clean.length() > 200) {
            clean = clean.substring(0, 200);
        }
        broadcast(CHAT, sender.playerName, clean);
    }

    // =====================================================================
    //  คลาสจัดการการเชื่อมต่อของผู้เล่นแต่ละคน (Per-client handler)
    // =====================================================================

    /** เธรดอ่านข้อมูลของ Client */
    private class ClientHandler extends Thread {
        private final Socket socket;
        private final Object writeLock = new Object();
        private DataInputStream in;
        private DataOutputStream out;
        private String playerName;
        private boolean joined;

        /** สร้าง ClientHandler สำหรับ Socket ใหม่ */
        ClientHandler(Socket socket) {
            super("GameServer-Client");
            this.socket = socket;
            setDaemon(true);
        }

        /** ลูปอ่านข้อความจาก Client */
        @Override
        public void run() {
            try {
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                while (running && !socket.isClosed()) {
                    dispatch(GameMessage.readFrom(in));
                }
            } catch (EOFException | SocketException e) {
                // การเชื่อมต่อปิด
            } catch (IOException e) {
                if (running) {
                    System.out.println("[server] connection error: " + e.getMessage());
                }
            } finally {
                handleDisconnect(this);
            }
        }

        /** แยกข้อความตามประเภท */
        private void dispatch(GameMessage message) {
            if (!joined) {
                if (JOIN.equals(message.type)) {
                    handleJoin(this, message.arg(0));
                } else {
                    send(ERROR, "You must join the room before sending anything else.");
                }
                return;
            }

            if (READY.equals(message.type)) {
                boolean value = "1".equals(message.arg(0))
                        || "true".equalsIgnoreCase(message.arg(0));
                synchronized (lock) {
                    ready.put(playerName, value);
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
                closeQuietly();
            } else if (JOIN.equals(message.type)) {
                send(ERROR, "You already joined the room.");
            } else {
                send(ERROR, "Unknown message type: " + message.type);
            }
        }

        /** ข้อความประเภทเดียว */
        void send(String type) {
            send(type, new String[0]);
        }

        /** ข้อความหนึ่งค่า */
        void send(String type, String arg) {
            send(type, new String[] {arg});
        }

        /** ข้อความสองค่า */
        void send(String type, String arg1, String arg2) {
            send(type, new String[] {arg1, arg2});
        }

        /** ส่ง GameMessage ไปยัง Client คนนี้ */
        void send(String type, String[] args) {
            send(new GameMessage(type, args));
        }

        /** เขียนข้อความพร้อมล็อก OutputStream */
        void send(GameMessage message) {
            synchronized (writeLock) {
                try {
                    message.writeTo(out);
                } catch (IOException e) {
                    closeQuietly();
                }
            }
        }

        /** ปิด Socket โดยไม่โยน Exception */
        void closeQuietly() {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
