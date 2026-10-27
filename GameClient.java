import java.io.*;
import java.net.*;
import java.util.List;

/**
 * GameClient: คลาสจัดการระบบเครือข่ายฝั่ง Client สำหรับผู้เล่นแต่ละคน
 * ดูแลการเชื่อมต่อผ่าน TCP Socket ไปยังเครื่องโฮสต์ (GameServer)
 * มีเธรดทำงานเบื้องหลัง (Background Reader Thread) เพื่อรอรับข้อความจากเซิร์ฟเวอร์
 * แล้วส่งต่อให้กับ {@link Listener} โดยส่วนนี้จะไม่ยุ่งเกี่ยวกับ Swing UI โดยตรง
 */
public class GameClient {

    /**
     * อินเตอร์เฟสสำหรับรับข้อความและการแจ้งเตือนเมื่อการเชื่อมต่อสิ้นสุด
     * เมธอดที่ทำงานในนี้ต้องทำงานอย่างรวดเร็วและไม่บล็อกการทำงานของเธรด
     */
    public interface Listener {
        // ทำงานเมื่อได้รับข้อความใหม่จากเซิร์ฟเวอร์
        void onMessage(String type, String[] args);

        // ทำงานเมื่อการเชื่อมต่อหลุดหรือสิ้นสุดลง
        void onDisconnected(String reason);
    }

    // เวลาจำกัดในการพยายามเชื่อมต่อเซิร์ฟเวอร์ (5 วินาที)
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String host;              // ไอพีหรือที่อยู่ของเครื่องโฮสต์
    private final int port;                 // พอร์ตที่เซิร์ฟเวอร์เปิดให้บริการ
    private final Listener listener;        // ตัวรับเหตุการณ์เครือข่าย
    private final Object writeLock = new Object(); // ล็อกสำหรับการส่งข้อมูลแบบเธรดปลอดภัย (Thread-safe)

    private Socket socket;                  // ซ็อกเก็ตการเชื่อมต่อ
    private DataInputStream in;             // สตรีมสำหรับอ่านข้อมูลแบบมีโครงสร้าง
    private DataOutputStream out;           // สตรีมสำหรับเขียนข้อมูลแบบมีโครงสร้าง
    private boolean connected;     // สถานะการเชื่อมต่อ (เชื่อมต่ออยู่หรือไม่)
    private boolean closing;       // สถานะว่ากำลังอยู่ในกระบวนการปิดหรือไม่

    /**
     * คอนสตรักเตอร์สำหรับสร้าง GameClient กำหนดที่อยู่โฮสต์ พอร์ต และ Listener
     */
    public GameClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /**
     * ตรวจสอบว่าขณะนี้กำลังเชื่อมต่อกับเซิร์ฟเวอร์อยู่หรือไม่
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * เปิดการเชื่อมต่อ Socket และเริ่มเธรดสำหรับอ่านข้อมูล
     * จะบล็อกการทำงานไม่เกิน CONNECT_TIMEOUT_MS
     */
    public void connect() throws IOException {
        Socket newSocket = new Socket();
        try {
            // เชื่อมต่อไปยัง IP และ Port ที่ระบุ พร้อมกำหนด Timeout
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            newSocket.setTcpNoDelay(true); // ปิด Nagle's algorithm เพื่อให้ส่งแพ็กเก็ตทันทีโดยไม่หน่วง
            in = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
        } catch (IOException e) {
            closeQuietly(newSocket);
            throw e;   // หากหาโฮสต์ไม่เจอ ถูกปฏิเสธ หรือหมดเวลา จะส่ง Exception ให้ผู้เรียกจัดการ
        }
        socket = newSocket;
        connected = true;
        closing = false;

        // เริ่มเธรดเบื้องหลังสำหรับรอรับข้อมูลจากเซิร์ฟเวอร์ตลอดเวลา
        Thread reader = new Thread(new Runnable() {
            /** เริ่มลูปอ่านข้อความจากเซิร์ฟเวอร์ในเธรดเบื้องหลัง */
            @Override
            public void run() {
                readLoop();
            }
        }, "GameClient-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    /**
     * ลูปอ่านข้อความจาก Socket ต่อเนื่องจนกว่าการเชื่อมต่อจะสิ้นสุด แล้วแจ้งเตือนเมื่อหลุด
     */
    private void readLoop() {
        try {
            while (connected) {
                // อ่านข้อความตามโครงสร้าง Protocol
                GameMessage message = GameMessage.readFrom(in);
                if (listener != null) {
                    listener.onMessage(message.type, message.args);
                }
            }
        } catch (EOFException e) {
            notifyDisconnected("The host closed the connection.");
        } catch (SocketException e) {
            if (!closing) {
                notifyDisconnected("Connection to the host was lost.");
            }
        } catch (IOException e) {
            if (!closing) {
                notifyDisconnected("Connection error: " + e.getMessage());
            }
        } finally {
            connected = false;
        }
    }

    /**
     * แจ้งเตือนไปยัง Listener เมื่อการเชื่อมต่อสิ้นสุดลง (ป้องกันการแจ้งซ้ำ)
     */
    private void notifyDisconnected(String reason) {
        if (!closing) {
            closing = true;
            if (listener != null) {
                listener.onDisconnected(reason);
            }
        }
    }

    /** ส่งข้อความประเภทเดียว โดยไม่มีข้อมูลเพิ่มเติม */
    public void send(String type) {
        send(type, new String[0]);
    }

    /** ส่งข้อความที่มีข้อมูลเพิ่มเติมหนึ่งค่า */
    public void send(String type, String arg) {
        send(type, new String[] {arg});
    }

    /**
     * ส่งข้อความ Protocol ไปยังเซิร์ฟเวอร์ มีการป้องกันการเขียนข้อมูลพร้อมกัน (Thread-safe)
     */
    public void send(String type, String[] args) {
        if (!connected || out == null) {
            return;
        }
        synchronized (writeLock) {
            try {
                new GameMessage(type, args).writeTo(out);
            } catch (IOException e) {
                notifyDisconnected("Could not reach the host: " + e.getMessage());
            }
        }
    }

    // =====================================================================
    //  เมธอดสำเร็จรูปสำหรับส่งคำสั่งต่างๆ (Request shortcuts)
    // =====================================================================

    /** ส่งคำขอเข้าร่วมห้องพร้อมระบุชื่อผู้เล่น */
    public void join(String playerName) {
        send(GameServer.JOIN, playerName);
    }

    /** ส่งสถานะความพร้อมของผู้เล่น (พร้อม = "1", ไม่พร้อม = "0") */
    public void setReady(boolean ready) {
        send(GameServer.READY, ready ? "1" : "0");
    }

    /** ส่งคำขอเริ่มเกม (สำหรับโฮสต์) */
    public void requestStartGame() {
        send(GameServer.START_GAME);
    }

    /** ส่งคำขอเริ่มเกมใหม่หลังจากจบเกม */
    public void requestNewGame() {
        send(GameServer.NEW_GAME);
    }

    /**
     * ส่งชุดรูปทรงความลับที่ผู้ตั้งโจทย์ (Questioner) เลือก
     */
    public void submitSecret(List<String> shapes) {
        if (shapes != null && shapes.size() >= GameServer.MIN_SHAPES && shapes.size() <= GameServer.MAX_SHAPES) {
            send(GameServer.SECRET, shapes.toArray(new String[0]));
        }
    }

    /**
     * ส่งชุดรูปทรงที่ผู้ทาย (Guesser) ทาย
     */
    public void submitGuess(List<String> shapes) {
        if (shapes != null && !shapes.isEmpty() && shapes.size() <= GameServer.MAX_SHAPES) {
            send(GameServer.GUESS, shapes.toArray(new String[0]));
        }
    }

    /** ส่งข้อความแชตไปยังห้อง */
    public void sendChat(String text) {
        send(GameServer.CHAT, text);
    }

    /**
     * แจ้งเตือนเซิร์ฟเวอร์ว่ากำลังออกจากห้อง แล้วปิด Socket
     */
    public void leave() {
        closing = true;
        if (connected) {
            send(GameServer.LEAVE_ROOM);
        }
        close();
    }

    /**
     * ปิดการเชื่อมต่อ Socket ทันที ปลอดภัยต่อการเรียกจากทุกเธรด
     */
    public void close() {
        closing = true;
        connected = false;
        closeQuietly(socket);
    }

    /**
     * ฟังก์ชันช่วยปิด Socket โดยดักจับและละเว้น Exception
     */
    private static void closeQuietly(Socket socket) {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
            // ซ็อกเก็ตปิดอยู่แล้ว ไม่ต้องดำเนินการใดๆ
        }
    }
}
