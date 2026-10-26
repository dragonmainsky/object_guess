import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/**
 * MainApp_UI: ส่วนหน้าต่างหลักของแอปพลิเคชัน
 * เก็บการตั้งค่าหน้าต่าง พื้นที่แสดงหน้าจอ และการสลับหน้าจอ โดยไม่ถือ logic ของเกมหรือเครือข่าย
 */
public class MainApp_UI extends JFrame {

    // ตัวระบุชื่อของแต่ละหน้าจอ
    public static final String SCREEN_JOIN = "JOIN";
    public static final String SCREEN_LOBBY = "LOBBY";
    public static final String SCREEN_QUESTIONER = "QUESTIONER";
    public static final String SCREEN_GUESSER = "GUESSER";
    public static final String SCREEN_SCOREBOARD = "SCOREBOARD";
    public static final String SCREEN_ROUND_RESULT = "ROUND_RESULT";

    /** พื้นที่แสดงผลสำหรับสลับหน้าจอพาเนลต่างๆ ด้วยเมธอด showScreen() */
    protected final JPanel screenHost = new JPanel(new BorderLayout());
    protected final Map<String, JPanel> screens = new LinkedHashMap<>();

    private String currentScreen = SCREEN_JOIN; // หน้าจอปัจจุบันที่กำลังแสดงอยู่

    /**
     * คอนสตรักเตอร์กำหนดขนาดและตำแหน่งหน้าต่าง พร้อมพื้นที่สำหรับสลับหน้าจอ
     */
    public MainApp_UI() {
        setTitle("Guess What It Looks Like? - Multiplayer Party Game (LAN)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // ปรับขนาดหน้าต่างให้เหมาะสมกับหน้าจอที่ใช้งานได้ เพื่อไม่ให้ปุ่มด้านล่างตกขอบจอ
        var toolkit = Toolkit.getDefaultToolkit();
        Insets usable = toolkit.getScreenInsets(getGraphicsConfiguration());
        Dimension screen = toolkit.getScreenSize();
        int width = Math.min(1360, screen.width - usable.left - usable.right);
        int height = Math.min(800, screen.height - usable.top - usable.bottom);
        setSize(width, height);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(screenHost, BorderLayout.CENTER);
    }

    /**
     * ลงทะเบียนหน้าจอเพื่อให้ showScreen() เรียกแสดงได้ตามชื่อ
     */
    protected void registerScreen(String name, JPanel panel) {
        screens.put(name, panel);
    }

    /**
     * สลับพาเนลหน้าจอที่กำลังแสดงอยู่ตามชื่อที่ระบุ
     */
    public void showScreen(String name) {
        currentScreen = name;
        screenHost.removeAll();
        screenHost.add(screens.get(name), BorderLayout.CENTER);
        screenHost.revalidate();
        screenHost.repaint();
    }

    /** คืนชื่อหน้าจอปัจจุบันที่กำลังแสดงอยู่ */
    protected String getCurrentScreen() {
        return currentScreen;
    }
}
