import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

/** คลาสกลางของสี ฟอนต์ และคอมโพเนนต์ UI */
public final class AppDialog {

    // ===== ชุดสีหลักของระบบ (Palette) =====
    // สีพื้นหลังทั่วไปและการ์ด
    static final Color BG_LIGHT = new Color(241, 245, 249), CARD_BG = Color.WHITE;
    static final Color CARD_BORDER = new Color(226, 232, 240), ROW_BG = new Color(248, 250, 252);
    static final Color SLOT_BORDER = new Color(203, 213, 225);

    // สีน้ำเงินหลัก
    static final Color PRIMARY_BLUE = new Color(37, 99, 235), PRIMARY_HOVER = new Color(29, 78, 216);
    static final Color PRIMARY_PRESS = new Color(30, 64, 175);
    static final Color BLUE_LIGHT_BG = new Color(239, 246, 255), BLUE_BORDER = new Color(191, 219, 254);

    // สีเขียวแสดงสถานะสำเร็จ (Success)
    static final Color SUCCESS_BG = new Color(220, 252, 231), SUCCESS_TEXT = new Color(22, 101, 52);
    static final Color SUCCESS_BORDER = new Color(187, 247, 208);

    // สีเหลืองแสดงสถานะเตือน (Warning)
    static final Color WARNING_BG = new Color(254, 249, 195), WARNING_TEXT = new Color(133, 77, 14);
    static final Color WARNING_BORDER = new Color(254, 240, 138);

    // สีแดงแสดงสถานะผิดพลาด/อันตราย (Danger/Error)
    static final Color DANGER_BG = new Color(254, 226, 226), DANGER_TEXT = new Color(153, 27, 27);
    static final Color DANGER_BORDER = new Color(254, 202, 202);

    // สีทอง/ส้ม สำหรับโบนัสและคะแนนพิเศษ (Gold)
    static final Color GOLD_BG = new Color(254, 243, 199), GOLD_TEXT = new Color(180, 83, 9);
    static final Color GOLD_BORDER = new Color(253, 230, 138);

    // สีของข้อความในระดับต่างๆ
    static final Color TEXT_DARK = new Color(15, 23, 42), TEXT_MUTED = new Color(100, 116, 139);
    static final Color TEXT_LIGHT = new Color(148, 163, 184);

    /** สี Avatar ตามลำดับผู้เล่น */
    private static final Color[] AVATARS = {
        new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153), new Color(16, 185, 129),
        new Color(245, 158, 11), new Color(14, 165, 233), new Color(244, 63, 94), new Color(99, 102, 241)
    };

    // ===== ฟอนต์มาตรฐานของเกม (Fonts) =====
    // ฟอนต์ Tahoma
    static Font f(int style, int size) {
        return new Font("Tahoma", style, size);
    }

    static final Font TITLE_LARGE = f(Font.BOLD, 26), TITLE_24 = f(Font.BOLD, 24), TITLE = f(Font.BOLD, 18);
    static final Font STRONG = f(Font.BOLD, 16), SUBTITLE = f(Font.BOLD, 14), BODY_BOLD = f(Font.BOLD, 13);
    static final Font SMALL_BOLD_12 = f(Font.BOLD, 12), SMALL_BOLD = f(Font.BOLD, 11), KEYCAP = f(Font.BOLD, 9);
    static final Font SCREEN_SUB = f(Font.PLAIN, 15), PLAIN_14 = f(Font.PLAIN, 14), BODY = f(Font.PLAIN, 13);
    static final Font SMALL_12 = f(Font.PLAIN, 12), SMALL = f(Font.PLAIN, 11);

    // =====================================================================
    // หน้าต่างแจ้งเตือนและยืนยัน
    // =====================================================================

    /** แสดงข้อมูลทั่วไป */
    public static void showInfo(Container parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** แสดงคำเตือน */
    public static void showWarning(Container parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    /** แสดงข้อผิดพลาด */
    public static void showError(Container parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /** แสดงกล่องยืนยัน Yes/No */
    public static boolean showConfirm(Container parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    // =====================================================================
    // คอมโพเนนต์สำเร็จรูป
    // =====================================================================

    /** JPanel โปร่งแสง */
    static JPanel panel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    /** JPanel โปร่งแสงแบบ BorderLayout */
    static JPanel panel(BorderLayout layout) {
        JPanel p = panel();
        p.setLayout(layout);
        return p;
    }

    /** JPanel โปร่งแสงแบบ FlowLayout */
    static JPanel panel(FlowLayout layout) {
        JPanel p = panel();
        p.setLayout(layout);
        return p;
    }

    /** JPanel โปร่งแสงแบบ GridLayout */
    static JPanel panel(GridLayout layout) {
        JPanel p = panel();
        p.setLayout(layout);
        return p;
    }

    /** การ์ดสีขาว */
    static JPanel card() {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, 16, 16));
        return card;
    }

    /** การ์ดแบบ BorderLayout */
    static JPanel card(BorderLayout layout) {
        JPanel card = card();
        card.setLayout(layout);
        return card;
    }

    /** การ์ดพร้อมระยะเว้น */
    static JPanel card(int padV, int padH) {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, padV, padH));
        return card;
    }

    /** การ์ดแบบ BorderLayout พร้อมระยะเว้น */
    static JPanel card(BorderLayout layout, int padV, int padH) {
        JPanel card = card(padV, padH);
        card.setLayout(layout);
        return card;
    }

    /** ขอบเส้นพร้อมระยะเว้น */
    static CompoundBorder box(Color line, int thickness, int padV, int padH) {
        return new CompoundBorder(new LineBorder(line, thickness), new EmptyBorder(padV, padH, padV, padH));
    }

    /** ช่องว่างแนวตั้ง */
    static JPanel strut(int height) {
        return filler(0, height, height);
    }

    /** ช่องว่างยืดหยุ่น */
    static JPanel glue() {
        return filler(0, 0, Integer.MAX_VALUE);
    }

    // ฟังก์ชันสร้างพื้นที่เติมเต็ม
    private static JPanel filler(int width, int height, int maxHeight) {
        JPanel filler = panel();
        Dimension fixed = new Dimension(width, height);
        filler.setMinimumSize(fixed);
        filler.setPreferredSize(fixed);
        filler.setMaximumSize(new Dimension(Short.MAX_VALUE, maxHeight));
        return filler;
    }

    /** BoxLayout แนวตั้ง */
    static JPanel stack(JPanel panel) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /** ตรึงคอมโพเนนต์ที่ขอบ */
    static JPanel pin(Container c, String edge) {
        JPanel holder = panel();
        holder.setLayout(new BorderLayout());
        holder.add(c, edge);
        return holder;
    }

    // สร้าง JLabel ทั่วไป
    static JLabel label(String text, Font font, Color color) {
        return label(text, font, color, JLabel.LEADING);
    }

    // JLabel พร้อมแนวข้อความ
    static JLabel label(String text, Font font, Color color, int align) {
        JLabel label = new JLabel(text, align);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** ป้าย Badge */
    static JLabel badge(String text, Color bg, Color fg, Color line, int padV, int padH) {
        return paintBadge(new JLabel(text, JLabel.CENTER), text, bg, fg, line, padV, padH);
    }

    /** อัปเดต Badge */
    static JLabel paintBadge(JLabel badge, String text, Color bg, Color fg, Color line, int padV, int padH) {
        badge.setText(text);
        badge.setFont(SMALL_BOLD);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        if (line == null) {
            badge.setBorder(new EmptyBorder(padV + 1, padH + 1, padV + 1, padH + 1));
        } else {
            badge.setBorder(box(line, 1, padV, padH));
        }
        badge.repaint();
        return badge;
    }

    /** ป้ายหัวข้อ */
    static JLabel topBadge(String text, Color bg, Color fg, Color line, boolean strong) {
        return paintTopBadge(new JLabel(text, JLabel.CENTER), text, bg, fg, line, strong);
    }

    /** อัปเดตป้ายหัวข้อ */
    static JLabel paintTopBadge(JLabel badge, String text, Color bg, Color fg, Color line, boolean strong) {
        badge.setText(text);
        if (strong) {
            badge.setFont(STRONG);
        } else {
            badge.setFont(SUBTITLE);
        }
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        if (strong) {
            badge.setBorder(box(line, 1, 10, 20));
        } else {
            badge.setBorder(box(line, 1, 8, 16));
        }
        badge.repaint();
        return badge;
    }

    /** ป้าย Avatar */
    static JLabel avatar(String text, Color bg, int size) {
        JLabel tile = label(text, f(Font.BOLD, size / 2), Color.WHITE, JLabel.CENTER);
        tile.setOpaque(true);
        tile.setBackground(bg);
        Dimension fixed = new Dimension(size, size);
        tile.setPreferredSize(fixed);
        tile.setMinimumSize(fixed);
        tile.setMaximumSize(fixed);
        return tile;
    }

    /** ป้ายแนะนำปุ่ม */
    static JLabel keyCap(String text) {
        JLabel key = label(text, KEYCAP, new Color(51, 65, 85), JLabel.CENTER);
        key.setOpaque(true);
        key.setBackground(Color.WHITE);
        key.setBorder(box(SLOT_BORDER, 1, 3, 6));
        return key;
    }

    /** หัวข้อการ์ด 2 บรรทัด */
    static JPanel header(String title, Font titleFont, String sub, Font subFont, JLabel right) {
        JPanel titleRow = panel();
        titleRow.setLayout(new BorderLayout());
        titleRow.add(label(title, titleFont, TEXT_DARK), BorderLayout.WEST);
        if (right != null) {
            JPanel holder = panel();
            holder.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            holder.add(right);
            titleRow.add(holder, BorderLayout.EAST);
        }
        JPanel header = panel();
        header.setLayout(new BorderLayout(0, 2));
        header.add(titleRow, BorderLayout.NORTH);
        if (sub != null) {
            header.add(label(sub, subFont, TEXT_MUTED), BorderLayout.CENTER);
        }
        return header;
    }

    /** กลุ่ม label/panel แนวเดียว */
    static JPanel group(int align, int hgap, int vgap, Container... items) {
        JPanel group = panel(new FlowLayout(align, hgap, vgap));
        for (Container item : items) {
            group.add(item);
        }
        return group;
    }

    /** หัวคอลัมน์ตาราง */
    static JLabel tableTitle(String text, int width, int height, int align) {
        JLabel label = label(text, SMALL_BOLD, TEXT_LIGHT, align);
        if (width > 0) {
            label.setPreferredSize(new Dimension(width, height));
        }
        return label;
    }

    /** กลุ่มหัวข้อแนวตั้ง */
    static JPanel titleStack(int gap, JLabel... labels) {
        JPanel titles = panel(new GridLayout(labels.length, 1, 0, gap));
        for (JLabel label : labels) {
            titles.add(label);
        }
        return titles;
    }

    /** แถบหัวเรื่อง */
    static JPanel pageHeader(JPanel titles, Container actions, String actionsEdge,
                             int top, int left, int bottom, int right) {
        JPanel header = panel(new BorderLayout());
        header.setBorder(new EmptyBorder(top, left, bottom, right));
        header.add(titles, BorderLayout.WEST);
        if (actions != null) {
            header.add(actions, actionsEdge);
        }
        return header;
    }

    /** ข้อมูลผู้เล่น */
    static JPanel playerWho(String avatarText, Color avatarColor, String name, int avatarSize,
                            int hgap, JLabel badge) {
        JPanel who = panel(new FlowLayout(FlowLayout.LEFT, hgap, 0));
        who.add(avatar(avatarText, avatarColor, avatarSize));
        who.add(label(name, BODY_BOLD, TEXT_DARK));
        if (badge != null) {
            who.add(badge);
        }
        return who;
    }

    /** แถวคะแนนสด (maxHeight = 0 = ไม่จำกัด) */
    static JPanel scoreRow(String name, int index, int score, boolean highlight,
                           int padV, int padH, boolean fixedScoreWidth, int maxHeight) {
        JPanel row = panel(new BorderLayout());
        if (maxHeight > 0) {
            row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        }
        row.setOpaque(highlight);
        row.setBackground(BLUE_LIGHT_BG);
        if (highlight) {
            row.setBorder(box(BLUE_BORDER, 1, 4, 8));
        } else {
            row.setBorder(new EmptyBorder(padV, padH, padV, padH));
        }

        row.add(playerWho(initialOf(name), avatarColor(index), name, 24, 8, null), BorderLayout.WEST);
        JLabel scoreLabel = label(String.valueOf(score), BODY_BOLD, TEXT_DARK, JLabel.RIGHT);
        if (fixedScoreWidth) {
            scoreLabel.setPreferredSize(new Dimension(24, 24));
        }
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    /** ป้ายเวลานับถอยหลัง */
    static void setTimeLeft(JLabel badge, int seconds) {
        boolean hurry = seconds <= GameServer.BONUS_WINDOW_SECONDS;
        Color background;
        Color foreground;
        Color border;
        if (hurry) {
            background = DANGER_BG;
            foreground = DANGER_TEXT;
            border = DANGER_BORDER;
        } else {
            background = WARNING_BG;
            foreground = WARNING_TEXT;
            border = WARNING_BORDER;
        }
        paintTopBadge(badge, timeText(seconds), background, foreground, border, true);
    }

    /** สถานะรอก่อนเริ่มเวลา */
    static void showWaitingTime(JLabel badge) {
        paintTopBadge(badge, "Time: --:--", WARNING_BG, WARNING_TEXT, WARNING_BORDER, true);
    }

    /** จัดกลางแนวนอน */
    static JPanel centerFlow(Container inner, int padV) {
        JPanel holder = panel(new FlowLayout(FlowLayout.CENTER, 0, padV));
        holder.add(inner);
        return holder;
    }

    /** ปุ่ม Flat */
    static JButton button(String text, Color bg, Color hover, Color pressed, Color fg, Color line, int padV, int padH) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setContentAreaFilled(false);   // ต้องปิดก่อนเพื่อไม่ให้ Look and Feel รีเซ็ตค่าสี
        button.setOpaque(true);
        button.setBorderPainted(line != null);
        if (line == null) {
            button.setBorder(new EmptyBorder(padV, padH, padV, padH));
        } else {
            button.setBorder(box(line, 1, padV, padH));
        }
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new ButtonHover(button, bg, hover, pressed));
        return button;
    }

    /** อักษรตัวแรกของชื่อ */
    static String initialOf(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase();
    }

    /** สี Avatar ตามลำดับ */
    static Color avatarColor(int index) {
        return AVATARS[Math.abs(index) % AVATARS.length];
    }

    /** ข้อความเวลา mm:ss */
    static String timeText(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("Time: %02d:%02d", safe / 60, safe % 60);
    }

    /** ข้อความเป้าหมายจำนวนรูป */
    static String targetText(int shapes) {
        if (shapes <= 0) {
            return "Target: ?";
        }
        String shapeWord;
        if (shapes == 1) {
            shapeWord = " Shape";
        } else {
            shapeWord = " Shapes";
        }
        return "Target: " + shapes + shapeWord;
    }

    /** ตัวจัดการสีปุ่มตามเมาส์ */
    static class ButtonHover extends MouseAdapter {
        private final JButton button;
        private final Color normal;
        private final Color hover;
        private final Color pressed;
        private boolean inside;

        /** สีของปุ่มสามสถานะ */
        ButtonHover(JButton button, Color normal, Color hover, Color pressed) {
            this.button = button;
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }

        // เมาส์เข้าปุ่ม (hover)
        @Override
        public void mouseEntered(MouseEvent event) {
            inside = true;
            apply(hover);
        }

        // เมาส์ออกจากปุ่ม
        @Override
        public void mouseExited(MouseEvent event) {
            inside = false;
            apply(normal);
        }

        // ขณะกดปุ่ม ให้ใช้สี pressed
        @Override
        public void mousePressed(MouseEvent event) {
            apply(pressed);
        }

        // ปล่อยเมาส์
        @Override
        public void mouseReleased(MouseEvent event) {
            if (inside) {
                apply(hover);
            } else {
                apply(normal);
            }
        }

        // สีปุ่มเมื่อใช้งานได้
        private void apply(Color color) {
            if (color != null && button.isEnabled()) {
                button.setBackground(color);
            }
        }
    }

}
