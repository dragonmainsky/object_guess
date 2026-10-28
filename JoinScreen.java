/** หน้าจอสร้างหรือเข้าร่วมห้อง */
public class JoinScreen extends JoinScreen_UI {

    /** Listener ของการสร้าง/เข้าร่วมห้อง */
    public interface JoinListener {
        // ทำงานเมื่อผู้เล่นเลือกสร้างห้อง (เป็นโฮสต์)
        void onCreateGame(String playerName, int port);

        // ทำงานเมื่อผู้เล่นเลือกเข้าร่วมห้อง
        void onJoinGame(String playerName, String serverIp, int port);
    }

    private final JoinListener listener;    // ตัวรับเหตุการณ์การสร้าง/เข้าร่วมเกม

    /** สร้างหน้าจอ JoinScreen */
    public JoinScreen(JoinListener listener) {
        super();
        this.listener = listener;
        setHostInfo(DEFAULT_PORT);
    }

    // =====================================================================
    // การทำงานและการตรวจสอบข้อมูล
    // =====================================================================

    /** ปุ่มสร้างห้อง */
    @Override
    public void onCreateClicked() {
        onCreateGame();
    }

    /** ปุ่มเข้าร่วมห้อง */
    @Override
    public void onJoinClicked() {
        onJoinGame();
    }

    /** ปุ่มออกจากโปรแกรม */
    @Override
    public void onLeaveAppClicked() {
        boolean confirmed = AppDialog.showConfirm(this, "Exit Game", "Are you sure you want to exit?");
        if (confirmed) {
            System.exit(0);
        }
    }

    /** จัดการ CREATE GAME */
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

    /** จัดการ JOIN GAME */
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

    /** แปลงพอร์ตเป็นตัวเลข (1-65535) */
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

    /** แสดงข้อผิดพลาด */
    private void fail(String status, String dialogTitle) {
        showStatus(status, true);
        AppDialog.showWarning(this, dialogTitle, status);
    }

    /** ตรวจสอบ IPv4 หรือชื่อโฮสต์ */
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

    // ตรวจสอบว่าข้อความเป็นตัวเลขทั้งหมด
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
