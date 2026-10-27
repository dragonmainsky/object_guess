import java.io.*;
import java.net.*;
import java.util.*;

/**
 * GameServer: เซิร์ฟเวอร์หลักของเกม (Authoritative LAN server)
 * เป็นศูนย์กลางในการควบคุมกฎการเล่น ดูแลรายชื่อผู้เล่น สถานะของเกม และการคำนวณคะแนนทั้งหมด
 * ข้อมูลที่เปลี่ยนแปลงได้ทั้งหมดจะถูกปกป้องด้วย Object Monitor ตัวเดียว (lock)
 * เพื่อป้องกันการเกิด Race Condition ระหว่าง Accept thread, Reader thread ของแต่ละ Client และ Round timer
 */
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
    /** จำนวนรอบที่ผู้เล่นแต่ละคนจะได้เป็นผู้ตั้งโจทย์ (Questioner) ใน 1 ทัวร์นาเมนต์ */
    public static final int ROUNDS_PER_PLAYER = 2;

    /**
     * ชื่อรูปทรงทั้ง 8 รูป เรียงตามลำดับในเมนูเลือก
     */
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

    /** มอนิเตอร์ล็อกตัวเดียวสำหรับปกป้องตัวแปรสถานะทั้งหมดด้านล่าง */
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

    public GameServer(int port) {
        this.port = port;
    }

    // =====================================================================
    //  วงจรการทำงานของเซิร์ฟเวอร์ (Lifecycle)
    // =====================================================================

    /**
     * ผูกพอร์ตและเริ่มเธรดรอรับการเชื่อมต่อ (Accept loop)
     * คืนค่า true หากเปิดพอร์ตสำเร็จ หรือ false หากพอร์ตถูกโปรแกรมอื่นใช้งานอยู่
     */
    public boolean start() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            startError = e.getMessage();
            System.out.println("[server] could not open port " + port + ": " + startError);
            return false;
        }
        running = true;
        acceptThread = new Thread(this::acceptLoop, "GameServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return true;
    }

    /** คืนข้อความผิดพลาดจากการเรียก start() ครั้งล่าสุด (ว่างหากเปิดพอร์ตสำเร็จ) */
    public String getStartError() {
        return startError;
    }

    // ตรวจสอบว่าเซิร์ฟเวอร์กำลังทำงานอยู่หรือไม่
    public boolean isRunning() {
        return running;
    }

    /**
     * ปิดห้องเซิร์ฟเวอร์: ส่งข้อความแจ้งเตือนผู้เล่นทุกคน ปิดพอร์ต และหยุดเธรดทั้งหมด
     */
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
            // ปิดเรียบร้อยแล้ว
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

    /**
     * ลูปสำหรับรอรับการเชื่อมต่อจาก Client เข้ามาอย่างต่อเนื่อง
     */
    private void acceptLoop() {
        while (running) {
            try {
                var socket = serverSocket.accept();
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

    private void broadcast(String type, String... args) {
        broadcast(new GameMessage(type, args));
    }

    /**
     * ส่งข้อความไปยัง Client ทุกคนที่อยู่ในห้อง
     */
    private void broadcast(GameMessage message) {
        for (ClientHandler client : clientsSnapshot()) {
            client.send(message);
        }
    }

    /**
     * กระจายรายชื่อผู้เล่นและสถานะความพร้อมไปยังทุกคนในห้อง
     */
    private void broadcastPlayerList() {
        String[] payload;
        synchronized (lock) {
            payload = buildPlayerListPayload();
        }
        broadcast(new GameMessage(PLAYER_LIST, payload));
    }

    // ดึง Snapshot ของรายการ Client เพื่อใช้ส่งข้อมูลนอกบล็อกล็อก
    private List<ClientHandler> clientsSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(clients);
        }
    }

    /**
     * สร้างชุดข้อมูลรายชื่อผู้เล่น: [จำนวนคน, ชื่อ, สถานะพร้อม, เป็นโฮสต์หรือไม่, ...]
     * ผู้เรียกต้องถือล็อก lock อยู่
     */
    private String[] buildPlayerListPayload() {
        var out = new ArrayList<String>();
        out.add(String.valueOf(clients.size()));
        String hostName = hostNameLocked();
        for (ClientHandler client : clients) {
            out.add(client.playerName);
            out.add(Boolean.TRUE.equals(ready.get(client.playerName)) ? "1" : "0");
            out.add(client.playerName.equals(hostName) ? "1" : "0");
        }
        return out.toArray(new String[0]);
    }

    /** คืนค่าชื่อโฮสต์ (ผู้เล่นคนแรกในลิสต์) ผู้เรียกต้องถือล็อก lock อยู่ */
    private String hostNameLocked() {
        return clients.isEmpty() ? "" : clients.get(0).playerName;
    }

    // =====================================================================
    //  การจัดการผู้เล่นเข้าและออกจากห้อง (Join and Leave)
    // =====================================================================

    /** ค้นหา ClientHandler ตามชื่อผู้เล่น (ผู้เรียกต้องถือล็อก lock อยู่) */
    private ClientHandler findByNameLocked(String name) {
        for (ClientHandler client : clients) {
            if (client.playerName != null && client.playerName.equalsIgnoreCase(name)) {
                return client;
            }
        }
        return null;
    }

    /**
     * ตรวจสอบและแปลงชื่อรูปทรงให้เป็นตัวพิมพ์มาตรฐาน
     * คืนค่า null หากชื่อไม่ถูกต้อง หรือมีรูปทรงซ้ำกัน
     */
    private static String[] normalizeShapes(String[] raw, int expectedCount) {
        if (raw == null || raw.length != expectedCount) {
            return null;
        }
        var canonical = new String[raw.length];
        var used = new HashSet<String>();
        for (int i = 0; i < raw.length; i++) {
            String match = null;
            for (String shape : SHAPE_NAMES) {
                if (shape.equalsIgnoreCase(raw[i])) {
                    match = shape;
                    break;
                }
            }
            if (match == null || !used.add(match)) {
                return null;   // ไม่พบชื่อรูปทรงที่ถูกต้อง หรือมีรูปทรงซ้ำกัน
            }
            canonical[i] = match;
        }
        return canonical;
    }

    /** ส่งข้อความ ERROR กลับไปยังผู้เล่น (ใช้ในบล็อกตรวจสอบก่อน return) */
    private static void reject(ClientHandler client, String message) {
        client.send(ERROR, message);
    }

    /** ส่ง JOIN_FAILED และปิดการเชื่อมต่อของผู้เล่น */
    private static void rejectJoin(ClientHandler handler, String message) {
        handler.send(JOIN_FAILED, message);
        handler.closeQuietly();
    }

    /**
     * ดำเนินการเมื่อมีคำขอเข้าร่วมห้อง (JOIN) จากผู้เล่น
     */
    private void handleJoin(ClientHandler handler, String rawName) {
        String name = (rawName == null) ? "" : rawName.trim();
        if (name.isEmpty()) { rejectJoin(handler, "Player name cannot be empty."); return; }
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }

        boolean isHost;
        synchronized (lock) {
            // ตรวจสอบชื่อซ้ำ
            if (findByNameLocked(name) != null) { rejectJoin(handler, "That name is already taken in this room."); return; }
            // ตรวจสอบว่ากำลังแข่งทัวร์นาเมนต์อยู่หรือไม่
            if (tournamentActive) { rejectJoin(handler, "A tournament is in progress. Please wait until it finishes."); return; }
            handler.playerName = name;
            handler.joined = true;
            clients.add(handler);
            ready.put(name, false);
            scores.putIfAbsent(name, 0);
            isHost = clients.get(0) == handler;
        }
        handler.send(JOIN_SUCCESS, name, isHost ? "1" : "0");
        broadcastPlayerList();
        broadcast(SYSTEM, name + " joined the room.");
    }

    /**
     * ดำเนินการเมื่อผู้เล่นหลุดการเชื่อมต่อหรือออกจากห้อง
     */
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

        // หากโฮสต์ออกจากห้อง ให้ปิดห้องทันที
        if (wasHost) {
            broadcast(SYSTEM, name + " (host) closed the room.");
            shutdown();
            return;
        }

        // ตรวจสอบสถานะการแข่งขันเมื่อมีคนออกจากห้องระหว่างเล่น
        boolean finishTournament = false, restartRound = false, endRoundNow = false;
        synchronized (lock) {
            if (tournamentActive) {
                if (clients.size() < MIN_PLAYERS) {
                    finishTournament = true; // ผู้เล่นเหลือน้อยกว่าขั้นต่ำ ให้จบทัวร์นาเมนต์
                } else if (roundRunning) {
                    if (name != null && name.equals(questioner)) {
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

    /**
     * ดำเนินการเมื่อโฮสต์กดเริ่มเกม (START_GAME)
     */
    private void handleStartGame(ClientHandler requester) {
        boolean newTournament;
        synchronized (lock) {
            if (roundRunning) { reject(requester, "A round is already in progress."); return; }
            if (clients.isEmpty() || clients.get(0) != requester) { reject(requester, "Only the host can start the game."); return; }
            if (clients.size() < MIN_PLAYERS) { reject(requester, "Need at least " + MIN_PLAYERS + " players to start."); return; }
            if (!othersReadyLocked(requester)) { reject(requester, "All other players must press READY before you can start."); return; }
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

    /**
     * คำขอเริ่มทัวร์นาเมนต์ใหม่ (NEW_GAME) จากหน้าจอสุดท้าย
     */
    private void handleNewGame(ClientHandler requester) {
        synchronized (lock) {
            if (clients.isEmpty() || clients.get(0) != requester) { reject(requester, "Only the host can start a new game."); return; }
            if (tournamentActive) { reject(requester, "A tournament is in progress."); return; }
            if (roundRunning) { reject(requester, "A round is in progress."); return; }
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

    /** ตรวจสอบว่าผู้เล่นคนอื่นทุกคนกดพร้อมแล้วหรือไม่ (โฮสต์ไม่ต้องกด) */
    private boolean othersReadyLocked(ClientHandler host) {
        for (ClientHandler client : clients) {
            if (client != host && !Boolean.TRUE.equals(ready.get(client.playerName))) {
                return false;
            }
        }
        return true;
    }

    // รีเซ็ตคะแนนและสถานะรอบทั้งหมด
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

    /**
     * เริ่มต้นรอบถัดไป: สุ่มจำนวนรูปทรง สลับคนเป็น Questioner และแจ้งเตือนทุกคน
     */
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

            // ตรวจสอบว่าขึ้นรอบใหญ่ (Cycle) ใหม่หรือไม่
            int cycle = (cycleSize > 0) ? (round - 1) / cycleSize : 0;
            if (cycle != cycleIndex) {
                startNewCycleLocked(cycle);
            }
            expectedShapes = lastCycleShapes;

            // ดึง Questioner คนถัดไปจากลำดับที่สุ่มไว้
            selectedQuestioner = takeNextQuestionerLocked();
            if (selectedQuestioner == null) {
                tournamentActive = false;
                roundRunning = false;
                return;
            }
            questioner = selectedQuestioner;
            currentRound = round;
            lockedTotal = totalRounds;
            shapesThisRound = expectedShapes;
            for (ClientHandler client : clients) {
                ready.put(client.playerName, false);
            }
        }
        // ประกาศข้อมูลรอบใหม่ไปยังทุกคน (นาฬิกาจะยังไม่เริ่มนับจนกว่า Questioner จะล็อกรูป)
        broadcast(ROUND_INFO, String.valueOf(currentRound), String.valueOf(lockedTotal),
                selectedQuestioner, String.valueOf(shapesThisRound));
    }

    /**
     * เริ่มต้นรอบใหญ่ใหม่: สุ่มจำนวนรูปทรงที่ไม่ซ้ำกับรอบใหญ่ก่อนหน้า และสับเปลี่ยนลำดับ Questioner
     */
    private void startNewCycleLocked(int cycle) {
        cycleIndex = cycle;
        lastCycleShapes = drawCycleShapeCountLocked();
        cycleOrder.clear();
        var names = new ArrayList<String>();
        for (ClientHandler client : clients) {
            names.add(client.playerName);
        }
        Collections.shuffle(names, random);
        cycleOrder.addAll(names);
    }

    /** สุ่มจำนวนรูปทรงระหว่าง MIN_SHAPES ถึง MAX_SHAPES โดยไม่ให้ซ้ำกับรอบก่อนหน้า */
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

    /** ดึง Questioner คนถัดไปจากลำดับที่เรียงไว้ */
    private String takeNextQuestionerLocked() {
        while (!cycleOrder.isEmpty()) {
            String candidate = cycleOrder.remove(0);
            if (findByNameLocked(candidate) != null) {
                return candidate;
            }
        }
        return clients.isEmpty() ? null : clients.get(0).playerName;
    }

    /** เริ่มรอบใหม่เมื่อ Questioner คนเดิมออกจากห้องไป */
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

    /**
     * เธรดนับถอยหลังของรอบ ส่งข้อความ TICK(round, remaining) ทุกๆ 1 วินาที
     * ใช้ System.nanoTime() เพื่อให้เวลาแม่นยำไม่คลาดเคลื่อน
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
                    endRound("time"); // หมดเวลา
                    return;
                }
                long sleepyMillis = millisToNextWholeSecond();
                try {
                    Thread.sleep(sleepyMillis <= 0 ? 50 : sleepyMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private int remainingSeconds() {
            long nanos = deadlineNanos - System.nanoTime();
            return (nanos <= 0) ? 0 : (int) ((nanos + 999_999_999L) / 1_000_000_000L);
        }

        // คำนวณมิลลิวินาทีจนกว่าจะครบวินาทีถัดไป เพื่อให้ส่ง TICK ตรงวินาทีพอดี
        private long millisToNextWholeSecond() {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0) {
                return 0;
            }
            long remainder = nanos % 1_000_000_000L;
            return (remainder == 0) ? 1000 : remainder / 1_000_000L;
        }
    }

    private void startRoundTimer(int roundNumber, int seconds) {
        stopRoundTimer();
        var timer = new RoundTimerThread(roundNumber, seconds);
        synchronized (lock) { roundTimer = timer; }
        timer.start();
    }

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
            try { timer.join(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // =====================================================================
    //  การกระทำในรอบการเล่น (Round actions)
    // =====================================================================

    /**
     * ดำเนินการเมื่อ Questioner ส่งชุดรูปทรงความลับ (SECRET)
     */
    private void handleSecret(ClientHandler sender, String[] shapes) {
        String[] canonical;
        int count, currentRound;
        synchronized (lock) {
            if (!roundRunning) { reject(sender, "No round is in progress."); return; }
            if (!sender.playerName.equals(questioner)) { reject(sender, "Only the questioner can set the secret shapes."); return; }
            if (secretLocked) { reject(sender, "The secret shapes are already locked for this round."); return; }
            if (shapes.length != expectedShapes) {
                reject(sender, "Pick exactly " + expectedShapes + " shape"
                        + (expectedShapes == 1 ? "" : "s") + " for this round."); return;
            }
            canonical = normalizeShapes(shapes, shapes.length);
            if (canonical == null) { reject(sender, "Those shapes are not valid (unknown or repeated)."); return; }
            secret = canonical;
            secretLocked = true;
            count = canonical.length;
            currentRound = round;
            // เริ่มนับเวลาของรอบเมื่อล็อกรูปทรงความลับ
            roundStartMillis = System.currentTimeMillis();
        }
        broadcast(SECRET_LOCKED, questioner, String.valueOf(count));
        broadcast(SYSTEM, "The questioner locked in " + count + " shape" + (count == 1 ? "" : "s")
                + ". Start guessing!");
        startRoundTimer(currentRound, ROUND_SECONDS);
    }

    /**
     * ดำเนินการเมื่อผู้ทายส่งคำตอบ (GUESS)
     */
    private void handleGuess(ClientHandler sender, String[] shapes) {
        int submitted;
        int totalGuessers;
        String[] canonical;
        synchronized (lock) {
            if (!roundRunning) { reject(sender, "No round is in progress."); return; }
            if (!secretLocked || secret == null) { reject(sender, "Wait for the questioner to lock the secret shapes first."); return; }
            if (sender.playerName.equals(questioner)) { reject(sender, "The questioner cannot submit a guess."); return; }
            if (guesses.containsKey(sender.playerName)) { reject(sender, "You already submitted a guess this round."); return; }
            if (shapes.length != secret.length) {
                reject(sender, "Pick exactly " + secret.length + " shape"
                        + (secret.length == 1 ? "" : "s") + " for this round."); return;
            }
            canonical = normalizeShapes(shapes, secret.length);
            if (canonical == null) { reject(sender, "Those shapes are not valid (unknown or repeated)."); return; }
            guesses.put(sender.playerName, canonical);
            guessTimes.put(sender.playerName, System.currentTimeMillis());
            submitted = countSubmittedLocked();
            totalGuessers = countGuessersLocked();
        }
        sender.send(GUESS_ACCEPTED);
        broadcast(GUESS_SUBMITTED, sender.playerName, String.valueOf(submitted), String.valueOf(totalGuessers));
        // หากผู้ทายทุกคนส่งคำตอบครบแล้ว ให้จบรอบทันที
        if (submitted >= totalGuessers) {
            endRound("all");
        }
    }

    /** นับจำนวนผู้เล่นที่ทำหน้าที่ทายทั้งหมด (ยกเว้น Questioner) */
    private int countGuessersLocked() {
        return (int) clients.stream().filter(client -> !client.playerName.equals(questioner)).count();
    }

    /** นับจำนวนผู้เล่นที่ส่งคำตอบทายเข้ามาแล้ว */
    private int countSubmittedLocked() {
        return (int) guesses.keySet().stream().filter(name -> !name.equals(questioner)).count();
    }

    /**
     * จบรอบการเล่น คำนวณคะแนน และส่งผล ROUND_RESULT ไปยังทุกคน
     */
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
        // เริ่มนับถอยหลังช่วงพักรอบ (Intermission) ก่อนเริ่มรอบถัดไปหรือเปิดหน้าสรุปคะแนนสุดท้าย
        startIntermission(isFinal);
    }

    /**
     * จบทัวร์นาเมนต์ก่อนกำหนด (เช่น ผู้เล่นเหลือน้อยกว่า 2 คน)
     */
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

    /**
     * หน่วงเวลา INTERMISSION_SECONDS (10 วินาที) เพื่อแสดงผลสรุปของรอบ
     * จากนั้นจะเริ่มรอบถัดไปหรือสั่งเปิดหน้าตารางคะแนนสุดท้าย
     */
    private void startIntermission(boolean isFinal) {
        stopIntermission();
        var thread = new Thread(() -> {
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
        }, "GameServer-Intermission");
        thread.setDaemon(true);
        synchronized (lock) {
            intermission = thread;
        }
        thread.start();
    }

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

    /**
     * คำนวณคะแนนที่ได้ในรอบนี้ (ทำงานฝั่งเซิร์ฟเวอร์เท่านั้น):
     * +1 ต่อรูปที่ทายถูก, +2 โบนัสพิเศษหากทายถูกทั้งหมดภายใน 10 วินาทีแรก
     * Questioner จะไม่ได้คะแนน
     */
    private void computeScoresLocked() {
        deltas.clear();
        correctCounts.clear();
        bonusFlags.clear();

        int expected = (secret == null) ? 0 : secret.length;
        for (ClientHandler client : clients) {
            String name = client.playerName;
            String[] guess = (expected == 0 || name.equals(questioner)) ? null : guesses.get(name);
            int correct = (guess == null) ? 0 : countMatching(secret, guess);
            Long submittedAt = guessTimes.get(name);
            long elapsedSeconds = (submittedAt == null) ? 0 : (submittedAt - roundStartMillis) / 1000;
            boolean bonus = guess != null && correct == expected && elapsedSeconds <= BONUS_WINDOW_SECONDS;
            int delta = correct + (bonus ? 2 : 0); // +2 โบนัสความเร็ว
            deltas.put(name, delta);
            correctCounts.put(name, correct);
            bonusFlags.put(name, bonus);
            scores.merge(name, delta, Integer::sum); // รวมคะแนนรอบนี้เข้ากับคะแนนสะสม
        }
    }

    // นับจำนวนรูปทรงที่ตรงกัน
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
     * สร้างชุดข้อมูลผลสรุปของรอบ ROUND_RESULT:
     * [round, totalRounds, isFinal, secretCsv, reason, questioner, playerCount,
     * (name, delta, total, guessCsv, correctCount, bonus) * playerCount]
     */
    private String[] buildRoundResultPayload(int roundNumber, boolean isFinal, String reason) {
        var out = new ArrayList<String>();
        out.add(String.valueOf(roundNumber));
        out.add(String.valueOf(totalRounds));
        out.add(isFinal ? "1" : "0");
        out.add(secret == null ? "" : String.join(",", secret));
        out.add(reason == null ? "" : reason);
        out.add(questioner == null ? "" : questioner);
        out.add(String.valueOf(clients.size()));
        for (ClientHandler client : clients) {
            String name = client.playerName;
            String[] guess = guesses.get(name);
            out.add(name);
            out.add(String.valueOf(deltas.getOrDefault(name, 0)));
            out.add(String.valueOf(scores.getOrDefault(name, 0)));
            out.add(guess == null ? "" : String.join(",", guess));
            out.add(String.valueOf(correctCounts.getOrDefault(name, 0)));
            out.add(Boolean.TRUE.equals(bonusFlags.get(name)) ? "1" : "0");
        }
        return out.toArray(new String[0]);
    }

    // =====================================================================
    //  ระบบข้อความแชต (Chat)
    // =====================================================================

    private void handleChat(ClientHandler sender, String text) {
        String clean = (text == null) ? "" : text.trim();
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

    /**
     * เธรดอ่านข้อมูลสำหรับแต่ละ Socket ที่เชื่อมต่อเข้ามา
     */
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
                    dispatch(GameMessage.readFrom(in));
                }
            } catch (EOFException | SocketException e) {
                // ไคลเอนต์ปิดการเชื่อมต่อตามปกติหรือเน็ตเวิร์กหลุด
            } catch (IOException e) {
                if (running) {
                    System.out.println("[server] connection error: " + e.getMessage());
                }
            } finally {
                handleDisconnect(this);
            }
        }

        /**
         * กระจายข้อความที่ได้รับจาก Client ไปยังเมธอดที่เกี่ยวข้อง
         */
        private void dispatch(GameMessage message) {
            if (!joined) {
                if (JOIN.equals(message.type)) {
                    handleJoin(this, message.arg(0));
                } else {
                    send(ERROR, "You must join the room before sending anything else.");
                }
                return;
            }
            switch (message.type) {
                case READY -> {
                    boolean value = "1".equals(message.arg(0)) || "true".equalsIgnoreCase(message.arg(0));
                    synchronized (lock) {
                        ready.put(playerName, value);
                    }
                    broadcastPlayerList();
                }
                case START_GAME -> handleStartGame(this);
                case SECRET -> handleSecret(this, message.args);
                case GUESS -> handleGuess(this, message.args);
                case NEW_GAME -> handleNewGame(this);
                case CHAT -> handleChat(this, message.arg(0));
                case LEAVE_ROOM -> closeQuietly();
                case JOIN -> send(ERROR, "You already joined the room.");
                default -> send(ERROR, "Unknown message type: " + message.type);
            }
        }

        void send(String type, String... args) {
            send(new GameMessage(type, args));
        }

        void send(GameMessage message) {
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
                // ปิดเรียบร้อยแล้ว
            }
        }
    }
}
