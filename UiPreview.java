import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.*;
import javax.swing.border.*;

/**
 * UiPreview: หน้าต่างสำหรับดู UI ของทุกหน้าจอโดยไม่ต้องเริ่มเกมจริง
 * ใช้คลาสหน้าจอเดิมทั้งหมดแบบอ่านอย่างเดียว (ไม่แก้ไฟล์หน้าจอ)
 * ข้อมูลผู้เล่น/คะแนน/แชตเป็นข้อมูลตัวอย่างสำหรับ preview เท่านั้น
 *
 * วิธีรัน:
 *   javac -encoding UTF-8 *.java
 *   java UiPreview
 */
public class UiPreview extends JFrame {

    private static final String[] SAMPLE_NAMES = {"Mhee", "Nina", "Ton", "Fah"};
    private static final int[] SAMPLE_SCORES = {6, 10, 6, 2};

    private final JPanel stage = new JPanel(new BorderLayout());
    private final Map<String, JPanel> cache = new LinkedHashMap<>();
    private final List<ScreenSpec> specs = new ArrayList<>();
    private final List<NavButton> navButtons = new ArrayList<>();
    private ScreenSpec current;

    /** สร้างหน้าต่าง preview และเริ่มแสดงหน้าจอตัวอย่างแรก */
    public UiPreview() {
        super("UI Preview - Guess What It Looks Like?");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        buildSpecs();
        add(buildHeader(), BorderLayout.NORTH);
        stage.setBackground(AppDialog.BG_LIGHT);
        add(stage, BorderLayout.CENTER);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1360, screen.width - 20);
        int height = Math.min(842, screen.height - 20);
        setSize(width, height);
        setMinimumSize(new Dimension(Math.min(1100, width), Math.min(680, height)));
        setLocationRelativeTo(null);

        selectSpec(specs.get(0));
        addWindowListener(new WindowAdapter() {
            /** หยุด Timer ของหน้าจอตัวอย่างทั้งหมดก่อนปิดหน้าต่าง */
            @Override
            public void windowClosing(WindowEvent event) {
                stopCountdowns();
            }
        });
        setVisible(true);
    }

    // =====================================================================
    //  รายการหน้าจอตัวอย่าง
    // =====================================================================

    /** ลงทะเบียนรายการหน้าจอตัวอย่างทั้งหมด */
    private void buildSpecs() {
        specs.add(new ScreenSpec("join", "Join screen", "Host or join a room", () -> buildJoin()));
        specs.add(new ScreenSpec("lobby", "Lobby", "Players, readiness and chat", () -> buildLobby()));
        specs.add(new ScreenSpec("questioner", "Questioner", "Pick the secret shapes", () -> buildQuestioner()));
        specs.add(new ScreenSpec("guesser", "Guesser", "Fill the guess slots", () -> buildGuesser()));
        specs.add(new ScreenSpec("result", "Round result", "Reveal and round scores", () -> buildRoundResult()));
        specs.add(new ScreenSpec("scoreboard", "Scoreboard", "Final tournament ranking", () -> buildScoreboard()));
    }

    /** สร้างแถบเมนูด้านบนสำหรับสลับหน้าจอและเปิดหน้าจอขนาดเต็ม */
    private JPanel buildHeader() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setBackground(AppDialog.CARD_BG);
        bar.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, AppDialog.CARD_BORDER),
                new EmptyBorder(7, 14, 7, 14)));

        JPanel brand = new JPanel(new GridLayout(2, 1, 0, 0));
        brand.setOpaque(false);
        brand.add(AppDialog.label("UI PREVIEW", AppDialog.SMALL_BOLD_12, AppDialog.PRIMARY_BLUE));
        brand.add(AppDialog.label("Sample data - no server", AppDialog.SMALL, AppDialog.TEXT_MUTED));
        bar.add(brand, BorderLayout.WEST);

        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        nav.setOpaque(false);
        for (ScreenSpec spec : specs) {
            NavButton button = new NavButton(spec.title);
            button.setToolTipText(spec.subtitle);
            button.addActionListener(event -> selectSpec(spec));
            navButtons.add(button);
            nav.add(button);
        }
        bar.add(nav, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton full = AppDialog.button("FULL SIZE", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER,
                AppDialog.PRIMARY_PRESS, Color.WHITE, AppDialog.PRIMARY_BLUE, 6, 12);
        full.setFont(AppDialog.SMALL_BOLD_12);
        full.addActionListener(event -> openFullSize());
        JButton reload = AppDialog.button("RELOAD", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 6, 12);
        reload.setFont(AppDialog.SMALL_BOLD_12);
        reload.addActionListener(event -> reloadCurrent());
        actions.add(full);
        actions.add(reload);
        bar.add(actions, BorderLayout.EAST);

        return bar;
    }

    /** เปลี่ยนไปแสดงหน้าจอที่เลือก โดยสร้างครั้งแรกแล้วเก็บไว้ใน cache */
    private void selectSpec(ScreenSpec spec) {
        if (spec == null) {
            return;
        }
        current = spec;
        for (int i = 0; i < specs.size(); i++) {
            navButtons.get(i).setActive(specs.get(i) == spec);
        }
        JPanel screen = cache.computeIfAbsent(spec.key, key -> spec.factory.get());
        stage.removeAll();
        stage.add(screen, BorderLayout.CENTER);
        stage.revalidate();
        stage.repaint();
        setTitle("UI Preview - " + spec.title);
    }

    /** สร้างหน้าจอปัจจุบันใหม่เพื่อล้างสถานะตัวอย่าง */
    private void reloadCurrent() {
        if (current == null) {
            return;
        }
        JPanel old = cache.remove(current.key);
        stopCountdown(old);
        JPanel fresh = current.factory.get();
        cache.put(current.key, fresh);
        stage.removeAll();
        stage.add(fresh, BorderLayout.CENTER);
        stage.revalidate();
        stage.repaint();
    }

    /** เปิดหน้าจอปัจจุบันในหน้าต่างใหม่ขนาดใหญ่ */
    private void openFullSize() {
        if (current == null) {
            return;
        }
        JPanel screen = current.factory.get();
        JFrame frame = new JFrame("Full size - " + current.title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(screen, BorderLayout.CENTER);
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        frame.setSize(Math.min(1360, screenSize.width - 60), Math.min(800, screenSize.height - 60));
        frame.setMinimumSize(new Dimension(1000, 640));
        frame.setLocationRelativeTo(this);
        frame.addWindowListener(new WindowAdapter() {
            /** หยุด Timer ของหน้าจอที่เปิดแบบเต็มจอเมื่อปิดหน้าต่าง */
            @Override
            public void windowClosing(WindowEvent event) {
                stopCountdown(screen);
            }
        });
        frame.setVisible(true);
    }

    /** หยุด Timer นับถอยหลังหากหน้าจอเป็น RoundResultScreen */
    private static void stopCountdown(JPanel screen) {
        if (screen instanceof RoundResultScreen) {
            ((RoundResultScreen) screen).stopCountdown();
        }
    }

    /** หยุด Timer ของทุกหน้าจอที่เก็บไว้ใน cache */
    private void stopCountdowns() {
        for (JPanel screen : cache.values()) {
            stopCountdown(screen);
        }
    }

    // =====================================================================
    //  หน้าจอตัวอย่าง
    // =====================================================================

    /** สร้างหน้าจอ Join ด้วยข้อมูลตัวอย่าง */
    private JPanel buildJoin() {
        AtomicReference<JoinScreen> ref = new AtomicReference<JoinScreen>();
        JoinScreen screen = new JoinScreen(new JoinScreen.JoinListener() {
            /** แสดงข้อความตัวอย่างเมื่อกดสร้างห้อง */
            @Override
            public void onCreateGame(String playerName, int port) {
                if (ref.get() != null) {
                    ref.get().showStatus("Preview only: would host as " + playerName + " on port " + port + ".");
                }
            }

            /** แสดงข้อความตัวอย่างเมื่อกดเข้าร่วมห้อง */
            @Override
            public void onJoinGame(String playerName, String serverIp, int port) {
                if (ref.get() != null) {
                    ref.get().showStatus("Preview only: would join " + serverIp + ":" + port
                            + " as " + playerName + ".");
                }
            }
        });
        ref.set(screen);
        screen.setHostInfo(GameServer.DEFAULT_PORT);
        return screen;
    }

    /** สร้างหน้าจอ Lobby พร้อมผู้เล่นและข้อความแชตตัวอย่าง */
    private JPanel buildLobby() {
        Map<String, Boolean> ready = readyMap();
        LobbyScreen screen = new LobbyScreen(event -> {
        }, event -> {
        });
        screen.setMyName("Mhee");
        screen.setHost(true);
        screen.setAddress("192.168.1.24:5000");
        screen.setPlayers(Arrays.asList(SAMPLE_NAMES), ready, "Mhee");
        screen.setStatusText("Get ready while the host prepares the round.");
        screen.setReadyAction(event -> {
            Boolean mheeReady = ready.get("Mhee");
            if (mheeReady != null && mheeReady.booleanValue()) {
                ready.put("Mhee", false);
            } else {
                ready.put("Mhee", true);
            }
            screen.setPlayers(Arrays.asList(SAMPLE_NAMES), ready, "Mhee");
        });
        screen.setChatAction(text -> screen.addChatMessage("Mhee", text));
        screen.addChatMessage("System", "Mhee created the room.");
        screen.addChatMessage("Nina", "Ready when you are!");
        screen.addChatMessage("Ton", "Let's go.");
        return screen;
    }

    /** สร้างหน้าจอ Questioner พร้อมรูปทรงที่เลือกไว้ล่วงหน้า */
    private JPanel buildQuestioner() {
        AtomicReference<QuestionerScreen> ref = new AtomicReference<QuestionerScreen>();
        QuestionerScreen screen = new QuestionerScreen(event -> {
            if (ref.get() != null) {
                ref.get().lockSelection();
            }
        });
        ref.set(screen);
        screen.setMyName("Mhee");
        screen.setScoreboard(SAMPLE_NAMES, SAMPLE_SCORES);
        screen.startRound(2, 8, 3);
        screen.setTimeLeft(24);
        setQuestionerPicks(screen, "Circle", "Triangle", "Hexagon");
        screen.setStatus("Preview sample: three shapes placed. Press CONFIRM to see the locked state.");
        return screen;
    }

    /** สร้างหน้าจอ Guesser พร้อมคำตอบตัวอย่างในช่องทาย */
    private JPanel buildGuesser() {
        AtomicReference<GuesserScreen> ref = new AtomicReference<GuesserScreen>();
        GuesserScreen screen = new GuesserScreen(event -> {
            if (ref.get() != null) {
                ref.get().markSubmitted();
            }
        });
        ref.set(screen);
        screen.setMyName("Nina");
        screen.setScoreboard(SAMPLE_NAMES, SAMPLE_SCORES);
        screen.startRound(2, 8, "Mhee", 3);
        screen.onSecretLocked("Mhee", 3);
        screen.setTimeLeft(18);
        setGuesserPicks(screen, "Circle", "Triangle", "Hexagon");
        screen.setStatus("Preview sample: all slots filled. Press SUBMIT GUESS to see the submitted state.");
        return screen;
    }

    /** สร้างหน้าจอสรุปผลรอบด้วยข้อมูลตัวอย่าง */
    private JPanel buildRoundResult() {
        RoundResultScreen screen = new RoundResultScreen();
        screen.showResult(2, 8, false,
                new String[] {"Circle", "Triangle", "Hexagon"}, "all", "Mhee",
                SAMPLE_NAMES, new int[] {0, 5, 2, 0}, SAMPLE_SCORES,
                new String[][] {
                    {},
                    {"Circle", "Triangle", "Hexagon"},
                    {"Circle", "Hexagon"},
                    {}
                },
                new int[] {0, 3, 2, 0}, new boolean[] {false, true, false, false});
        return screen;
    }

    /** สร้างหน้าจอสกอร์บอร์ดด้วยคะแนนตัวอย่าง */
    private JPanel buildScoreboard() {
        ScoreboardScreen screen = new ScoreboardScreen(event -> {
        }, event -> {
        });
        screen.setResults(SAMPLE_NAMES, SAMPLE_SCORES, 8, 8);
        screen.setNextEnabled(true);
        return screen;
    }

    // =====================================================================
    //  ข้อมูลตัวอย่างและ reflection สำหรับเติมสถานะที่ไม่มี public setter
    // =====================================================================

    /** สร้างแผนที่สถานะพร้อมสำหรับข้อมูลตัวอย่าง */
    private static Map<String, Boolean> readyMap() {
        LinkedHashMap<String, Boolean> ready = new LinkedHashMap<String, Boolean>();
        ready.put("Mhee", false);
        ready.put("Nina", true);
        ready.put("Ton", true);
        ready.put("Fah", false);
        return ready;
    }

    /** ใส่รูปทรงตัวอย่างให้ Questioner ผ่าน reflection (ใช้เฉพาะหน้า preview) */
    @SuppressWarnings("unchecked")
    private static void setQuestionerPicks(QuestionerScreen screen, String... shapes) {
        try {
            Field selectedField = QuestionerScreen.class.getDeclaredField("selectedShapes");
            selectedField.setAccessible(true);
            Set<String> selected = (Set<String>) selectedField.get(screen);
            selected.addAll(Arrays.asList(shapes));

            Field spotsField = QuestionerScreen.class.getDeclaredField("shapeSpots");
            spotsField.setAccessible(true);
            Map<String, double[]> spots = (Map<String, double[]>) spotsField.get(screen);
            double[][] positions = {{0.30, 0.36}, {0.50, 0.62}, {0.70, 0.38}};
            for (int i = 0; i < shapes.length; i++) {
                spots.put(shapes[i], positions[i % positions.length]);
            }

            Method refresh = QuestionerScreen.class.getDeclaredMethod("refreshUI");
            refresh.setAccessible(true);
            refresh.invoke(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("[preview] Could not seed questioner sample: " + e);
        }
    }

    /** ใส่รูปทรงตัวอย่างให้ Guesser ผ่าน reflection (ใช้เฉพาะหน้า preview) */
    @SuppressWarnings("unchecked")
    private static void setGuesserPicks(GuesserScreen screen, String... shapes) {
        try {
            Field guessedField = GuesserScreen.class.getDeclaredField("guessedShapes");
            guessedField.setAccessible(true);
            List<String> guessed = (List<String>) guessedField.get(screen);
            guessed.addAll(Arrays.asList(shapes));

            Method refresh = GuesserScreen.class.getDeclaredMethod("refreshSelection");
            refresh.setAccessible(true);
            refresh.invoke(screen);
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("[preview] Could not seed guesser sample: " + e);
        }
    }

    // =====================================================================
    //  คลาสช่วยเหลือของ preview
    // =====================================================================

    /** ข้อมูลของหน้าจอตัวอย่างหนึ่งรายการ */
    private static final class ScreenSpec {
        final String key;
        final String title;
        final String subtitle;
        final Supplier<JPanel> factory;

        /** เก็บชื่อและฟังก์ชันสร้างหน้าจอตัวอย่าง */
        ScreenSpec(String key, String title, String subtitle, Supplier<JPanel> factory) {
            this.key = key;
            this.title = title;
            this.subtitle = subtitle;
            this.factory = factory;
        }

        /** ใช้ชื่อหน้าจอเป็นข้อความแทนออบเจกต์ */
        @Override
        public String toString() {
            return title;
        }
    }

    /** ปุ่มเมนูด้านบนสำหรับสลับหน้าจอตัวอย่าง */
    private static final class NavButton extends JButton {
        private boolean active;
        private boolean over;

        /** ตั้งค่าปุ่มนำทางและเอฟเฟกต์เมื่อเมาส์ชี้ */
        NavButton(String text) {
            super(text);
            setFont(AppDialog.SMALL_BOLD_12);
            setForeground(AppDialog.TEXT_DARK);
            setBorder(new EmptyBorder(7, 11, 7, 11));
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                // เมื่อเมาส์เข้าปุ่ม ให้แสดงสถานะ hover
                @Override
                public void mouseEntered(MouseEvent event) {
                    over = true;
                    repaint();
                }

                // เมื่อเมาส์ออกจากปุ่ม ให้ยกเลิกสถานะ hover
                @Override
                public void mouseExited(MouseEvent event) {
                    over = false;
                    repaint();
                }
            });
        }

        /** เปลี่ยนสถานะปุ่มที่กำลังถูกเลือก */
        void setActive(boolean active) {
            this.active = active;
            setForeground(active ? Color.WHITE : AppDialog.TEXT_DARK);
            repaint();
        }

        /** วาดพื้นหลังปุ่มตามสถานะ active / hover / pressed */
        @Override
        protected void paintComponent(Graphics g) {
            Color fill = active ? AppDialog.PRIMARY_BLUE
                    : (getModel().isPressed() ? AppDialog.BLUE_LIGHT_BG
                    : (over ? AppDialog.BG_LIGHT : AppDialog.CARD_BG));
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (!active) {
                g.setColor(AppDialog.CARD_BORDER);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            super.paintComponent(g);
        }
    }

    /** เปิดหน้าต่าง preview */
    public static void main(String[] args) {
        new UiPreview();
    }
}
