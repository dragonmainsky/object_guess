/**
 * JoinScreen: หน้าจอแรก (หน้าจอที่ 1) ของเกม
 * ใช้สำหรับสร้างห้อง (Host) บนเครื่องนี้ หรือเข้าร่วมห้อง (Join) ของเพื่อนผ่านเครือข่าย LAN เดียวกัน
 * หน้านี้จะไม่เปิด Socket เองโดยตรง แต่จะตรวจสอบความถูกต้องของข้อมูลแล้วส่งต่อผ่าน {@link JoinListener}
 */
public class JoinScreen extends JoinScreen_UI {

    /**
     * อินเตอร์เฟสสำหรับแจ้งเตือนเมื่อผู้เล่นเลือกสร้างห้องหรือเข้าร่วมห้อง
     * เมธอดเหล่านี้จะทำงานบน Event Dispatch Thread (UI Thread)
     */
    public interface JoinListener {
        // ทำงานเมื่อผู้เล่นเลือกสร้างห้อง (เป็นโฮสต์)
        void onCreateGame(String playerName, int port);

        // ทำงานเมื่อผู้เล่นเลือกเข้าร่วมห้อง
        void onJoinGame(String playerName, String serverIp, int port);
    }

    private final JoinListener listener;    // ตัวรับเหตุการณ์การสร้าง/เข้าร่วมเกม

    /**
     * คอนสตรักเตอร์สำหรับสร้างหน้าจอ JoinScreen แบ่งการจัดวางออกเป็น 2 ฝั่ง (ซ้าย-ขวา)
     */
    public JoinScreen(JoinListener listener) {
        super();
        this.listener = listener;
        setHostInfo(DEFAULT_PORT);
    }

    // =====================================================================
    //  การทำงานและการตรวจสอบความถูกต้องของข้อมูล (Actions and Validation)
    // =====================================================================

    /** ผู้เล่นกดปุ่มสร้างห้อง (Host) */
    @Override
    protected void onCreateClicked() {
        onCreateGame();
    }

    /** ผู้เล่นกดปุ่มเข้าร่วมห้อง (Join) */
    @Override
    protected void onJoinClicked() {
        onJoinGame();
    }

    /** ผู้เล่นกดปุ่มออกจากโปรแกรม พร้อมยืนยันก่อนปิด */
    @Override
    protected void onLeaveAppClicked() {
        boolean confirmed = AppDialog.showConfirm(this, "Exit Game", "Are you sure you want to exit?");
        if (confirmed) {
            System.exit(0);
        }
    }

    /**
     * ดำเนินการเมื่อผู้เล่นกดปุ่ม CREATE GAME (HOST)
     * ตรวจสอบว่ากรอกชื่อและพอร์ตถูกต้องหรือไม่ก่อนส่งให้ listener
     */
    private void onCreateGame() {
        String name = getPlayerName();
        if (name.isEmpty()) {
            showStatus("Enter your player name first.", true);
            AppDialog.showWarning(this, "Name required", "Please enter your player name.");
            return;
        }
        if (name.length() > GameServer.MAX_NAME_LENGTH) {
            showStatus("Name must be at most " + GameServer.MAX_NAME_LENGTH + " characters.", true);
            return;
        }
        int port = parsePort();
        if (port >= 0) {
            listener.onCreateGame(name, port);
        }
    }

    /**
     * ดำเนินการเมื่อผู้เล่นกดปุ่ม JOIN GAME
     * ตรวจสอบความถูกต้องของชื่อผู้เล่น, IP เซิร์ฟเวอร์ และพอร์ต ก่อนส่งให้ listener
     */
    private void onJoinGame() {
        String name = getPlayerName();
        String ip = getServerIp();
        if (name.isEmpty()) {
            fail("Please enter your player name.", "Name required");
        } else if (name.length() > GameServer.MAX_NAME_LENGTH) {
            fail("Name must be at most " + GameServer.MAX_NAME_LENGTH + " characters.", "Name too long");
        } else if (ip.isEmpty()) {
            fail("Please enter the host's IP address.", "Server IP required");
        } else if (!isValidAddress(ip)) {
            fail("\"" + ip + "\" does not look like a valid IPv4 address or host name.", "Invalid address");
        } else {
            int port = parsePort();
            if (port >= 0) {
                listener.onJoinGame(name, ip, port);
            }
        }
    }

    /**
     * แปลงข้อความพอร์ตเป็นตัวเลข พร้อมตรวจสอบให้อยู่ระหว่าง 1 ถึง 65535
     */
    private int parsePort() {
        String text = getServerPortText();
        if (text.isEmpty()) {
            fail("Please enter the port number (default " + DEFAULT_PORT + ").", "Port required");
            return -1;
        }
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            fail("\"" + text + "\" is not a number. Use " + DEFAULT_PORT + " unless told otherwise.", "Invalid port");
            return -1;
        }
        if (port < 1 || port > 65535) {
            fail("Port must be between 1 and 65535.", "Invalid port");
            return -1;
        }
        return port;
    }

    /**
     * แสดงข้อความแจ้งเตือนข้อผิดพลาดทั้งบนแถบสถานะและกล่องข้อความ Dialog
     */
    private void fail(String status, String dialogTitle) {
        showStatus(status, true);
        AppDialog.showWarning(this, dialogTitle, status);
    }

    /**
     * ตรวจสอบรูปแบบของที่อยู่ IPv4 หรือชื่อโฮสต์ เพื่อรองรับทั้ง IP จริงและ localhost
     */
    private static boolean isValidAddress(String value) {
        if (value.isEmpty() || value.length() > 253 || value.indexOf(' ') >= 0) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length == 4 && isNumeric(parts[0]) && isNumeric(parts[1]) && isNumeric(parts[2]) && isNumeric(parts[3])) {
            try {
                for (String part : parts) {
                    int octet = Integer.parseInt(part);
                    if (octet < 0 || octet > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ฟังก์ชันตรวจสอบว่าข้อความประกอบด้วยตัวเลขทั้งหมดหรือไม่
    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
