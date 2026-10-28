import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/** ส่วนหน้าต่างหลักของแอปพลิเคชัน */
public class MainApp_UI extends JFrame {

    // ตัวระบุชื่อของแต่ละหน้าจอ
    public static final String SCREEN_JOIN = "JOIN";
    public static final String SCREEN_LOBBY = "LOBBY";
    public static final String SCREEN_QUESTIONER = "QUESTIONER";
    public static final String SCREEN_GUESSER = "GUESSER";
    public static final String SCREEN_SCOREBOARD = "SCOREBOARD";
    public static final String SCREEN_ROUND_RESULT = "ROUND_RESULT";

    /** พื้นที่แสดงหน้าจอ */
    protected final JPanel screenHost = new JPanel(new BorderLayout());
    protected final Map<String, JPanel> screens = new LinkedHashMap<>();

    private String currentScreen = SCREEN_JOIN; // หน้าจอปัจจุบันที่กำลังแสดงอยู่

    /** สร้างหน้าต่างหลัก */
    public MainApp_UI() {
        setTitle("Guess What It Looks Like? - Multiplayer Party Game (LAN)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // ปรับขนาดให้พอดีหน้าจอ
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Insets usable = toolkit.getScreenInsets(getGraphicsConfiguration());
        Dimension screen = toolkit.getScreenSize();
        int width = Math.min(1360, screen.width - usable.left - usable.right);
        int height = Math.min(800, screen.height - usable.top - usable.bottom);
        setSize(width, height);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(screenHost, BorderLayout.CENTER);
    }

    /** ลงทะเบียนหน้าจอ */
    protected void registerScreen(String name, JPanel panel) {
        screens.put(name, panel);
    }

    /** สลับหน้าจอตามชื่อ */
    public void showScreen(String name) {
        currentScreen = name;
        screenHost.removeAll();
        screenHost.add(screens.get(name), BorderLayout.CENTER);
        screenHost.revalidate();
        screenHost.repaint();
    }

    /** ชื่อหน้าจอปัจจุบัน */
    protected String getCurrentScreen() {
        return currentScreen;
    }
}
