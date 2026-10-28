import java.io.*;
import java.net.*;
import java.util.List;

/** ไคลเอนต์เครือข่ายสำหรับเชื่อมต่อกับ GameServer */
public class GameClient {

    /** Listener ของข้อความและการตัดการเชื่อมต่อ */
    public interface Listener {
        // ทำงานเมื่อได้รับข้อความใหม่จากเซิร์ฟเวอร์
        void onMessage(String type, String[] args);

        // ทำงานเมื่อการเชื่อมต่อหลุดหรือสิ้นสุดลง
        void onDisconnected(String reason);
    }

    // เวลารอเชื่อมต่อ (5 วินาที)
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

    /** สร้าง GameClient สำหรับโฮสต์และพอร์ตที่ระบุ */
    public GameClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /** สถานะการเชื่อมต่อ */
    public boolean isConnected() {
        return connected;
    }

    /** เปิด Socket และเริ่มเธรดอ่านข้อมูล */
    public void connect() throws IOException {
        Socket newSocket = new Socket();
        try {
            // เชื่อมต่อพร้อม Timeout
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

        // เธรดอ่านข้อมูล
        Thread reader = new Thread(new Runnable() {
            /** ลูปอ่านข้อความจากเซิร์ฟเวอร์ */
            @Override
            public void run() {
                readLoop();
            }
        }, "GameClient-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    /** ลูปอ่านข้อความจาก Socket */
    private void readLoop() {
        try {
            while (connected) {
                // อ่านข้อความตามโครงสร้าง Protocol
                GameMessage message = GameMessage.readFrom(in);
                listener.onMessage(message.type, message.args);
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

    /** แจ้งเมื่อการเชื่อมต่อสิ้นสุด */
    private void notifyDisconnected(String reason) {
        if (!closing) {
            closing = true;
            listener.onDisconnected(reason);
        }
    }

    /** ข้อความประเภทเดียว */
    public void send(String type) {
        send(type, new String[0]);
    }

    /** ข้อความหนึ่งค่า */
    public void send(String type, String arg) {
        send(type, new String[] {arg});
    }

    /** ส่งข้อความโปรโตคอล (Thread-safe) */
    public void send(String type, String[] args) {
        if (!connected) {
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
    // คำสั่งสำเร็จรูป
    // =====================================================================

    /** ส่งคำขอ JOIN */
    public void join(String playerName) {
        send(GameServer.JOIN, playerName);
    }

    /** ส่งสถานะพร้อม */
    public void setReady(boolean ready) {
        String readyValue;
        if (ready) {
            readyValue = "1";
        } else {
            readyValue = "0";
        }
        send(GameServer.READY, readyValue);
    }

    /** ส่งคำขอ START_GAME */
    public void requestStartGame() {
        send(GameServer.START_GAME);
    }

    /** ส่งคำขอ NEW_GAME */
    public void requestNewGame() {
        send(GameServer.NEW_GAME);
    }

    /** ส่งชุดรูปทรงความลับ */
    public void submitSecret(List<String> shapes) {
        if (shapes != null && shapes.size() >= GameServer.MIN_SHAPES && shapes.size() <= GameServer.MAX_SHAPES) {
            send(GameServer.SECRET, shapes.toArray(new String[0]));
        }
    }

    /** ส่งคำตอบทาย */
    public void submitGuess(List<String> shapes) {
        if (shapes != null && !shapes.isEmpty() && shapes.size() <= GameServer.MAX_SHAPES) {
            send(GameServer.GUESS, shapes.toArray(new String[0]));
        }
    }

    /** ส่งข้อความแชต */
    public void sendChat(String text) {
        send(GameServer.CHAT, text);
    }

    /** แจ้งออกจากห้องและปิด Socket */
    public void leave() {
        closing = true;
        send(GameServer.LEAVE_ROOM);
        close();
    }

    /** ปิด Socket ทันที */
    public void close() {
        closing = true;
        connected = false;
        closeQuietly(socket);
    }

    /** ปิด Socket โดยไม่โยน Exception */
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
