import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * AppDialog: คลาสศูนย์กลางสำหรับจัดการรูปลักษณ์ของเกม (ชุดสี, ฟอนต์, คอมโพเนนต์ UI สำเร็จรูป)
 * และกล่องข้อความแจ้งเตือน/ยืนยัน (Alert & Confirmation Dialogs) ที่ใช้ JOptionPane มาตรฐานของ Java
 * เพื่อให้ทุกหน้าจอมีดีไซน์และธีมที่เป็นมาตรฐานเดียวกันทั้งแอปพลิเคชัน
 */
public final class AppDialog {

    // ===== ชุดสีหลักของระบบ (Palette) =====
    // สีพื้นหลังทั่วไปและการ์ด
    static final Color BG_LIGHT = new Color(241, 245, 249), CARD_BG = Color.WHITE;
    static final Color CARD_BORDER = new Color(226, 232, 240), ROW_BG = new Color(248, 250, 252);
    static final Color SLOT_BORDER = new Color(203, 213, 225);

    // สีน้ำเงินโทนหลัก (Primary Blue) สำหรับปุ่มและการไฮไลต์
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

    /** สีประจำตัวของผู้เล่น (Avatar colours) วนใช้ตามลำดับที่ผู้เล่นเข้าร่วมห้อง */
    private static final Color[] AVATARS = {
        new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153), new Color(16, 185, 129),
        new Color(245, 158, 11), new Color(14, 165, 233), new Color(244, 63, 94), new Color(99, 102, 241)
    };

    // ===== ฟอนต์มาตรฐานของเกม (Fonts) =====
    // ฟังก์ชันช่วยสร้างฟอนต์ Tahoma ตามรูปแบบและขนาดที่ต้องการ
    static Font f(int style, int size) {
        return new Font("Tahoma", style, size);
    }

    static final Font TITLE_LARGE = f(Font.BOLD, 26), TITLE_24 = f(Font.BOLD, 24), TITLE = f(Font.BOLD, 18);
    static final Font STRONG = f(Font.BOLD, 16), SUBTITLE = f(Font.BOLD, 14), BODY_BOLD = f(Font.BOLD, 13);
    static final Font SMALL_BOLD_12 = f(Font.BOLD, 12), SMALL_BOLD = f(Font.BOLD, 11), KEYCAP = f(Font.BOLD, 9);
    static final Font SCREEN_SUB = f(Font.PLAIN, 15), PLAIN_14 = f(Font.PLAIN, 14), BODY = f(Font.PLAIN, 13);
    static final Font SMALL_12 = f(Font.PLAIN, 12), SMALL = f(Font.PLAIN, 11);

    // =====================================================================
    //  หน้าต่างแจ้งเตือนและยืนยัน (Alerts and Confirmation)
    // =====================================================================

    /**
     * แสดงหน้าต่างแจ้งข้อมูลทั่วไปพร้อมปุ่ม OK (ดีฟอลต์ของ Java)
     */
    public static void showInfo(Container parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * แสดงหน้าต่างแจ้งเตือนข้อควรระวังพร้อมปุ่ม OK (ดีฟอลต์ของ Java)
     */
    public static void showWarning(Container parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
    }

    /**
     * แสดงหน้าต่างแจ้งข้อผิดพลาดพร้อมปุ่ม OK (ดีฟอลต์ของ Java)
     */
    public static void showError(Container parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    /**
     * แสดงหน้าต่างยืนยัน Yes/No คืนค่า true เมื่อผู้เล่นกดปุ่มยืนยัน (Yes)
     */
    public static boolean showConfirm(Container parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    // =====================================================================
    //  คอมโพเนนต์สำเร็จรูปที่ใช้งานร่วมกันในหลายหน้าจอ (Small factories)
    // =====================================================================

    /** สร้าง JPanel โปร่งแสง (Opaque = false) */
    static JPanel panel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    /** สร้าง JPanel โปร่งแสงพร้อม BorderLayout */
    static JPanel panel(BorderLayout layout) {
        JPanel p = panel();
        p.setLayout(layout);
        return p;
    }

    /** สร้าง JPanel โปร่งแสงพร้อม FlowLayout */
    static JPanel panel(FlowLayout layout) {
        JPanel p = panel();
        p.setLayout(layout);
        return p;
    }

    /** สร้าง JPanel โปร่งแสงพร้อม GridLayout */
    static JPanel panel(GridLayout layout) {
        JPanel p = panel();
        p.setLayout(layout);
        return p;
    }

    /** สร้าง JPanel สีขาวลักษณะเป็นการ์ด มีขอบบางและระยะเว้น 16px */
    static JPanel card() {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, 16, 16));
        return card;
    }

    /** สร้างการ์ดพร้อม BorderLayout */
    static JPanel card(BorderLayout layout) {
        JPanel card = card();
        card.setLayout(layout);
        return card;
    }

    /** สร้างการ์ดที่มีระยะเว้นด้านในตามที่กำหนด */
    static JPanel card(int padV, int padH) {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, padV, padH));
        return card;
    }

    /** สร้างการ์ดพร้อม BorderLayout และระยะเว้นด้านในตามที่กำหนด */
    static JPanel card(BorderLayout layout, int padV, int padH) {
        JPanel card = card(padV, padH);
        card.setLayout(layout);
        return card;
    }

    /** สร้างขอบเส้น 1px พร้อมระยะเว้นด้านใน (Padding) */
    static CompoundBorder box(Color line, int thickness, int padV, int padH) {
        return new CompoundBorder(new LineBorder(line, thickness), new EmptyBorder(padV, padH, padV, padH));
    }

    /** สร้างช่องว่างคงที่แนวตั้งสำหรับ BoxLayout */
    static JPanel strut(int height) {
        return filler(0, height, height);
    }

    /** สร้างช่องว่างยืดหยุ่นที่กินพื้นที่ที่เหลือใน BoxLayout */
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

    /** กำหนด BoxLayout แนวตั้งให้พาเนล (เรียงจากบนลงล่าง) */
    static JPanel stack(JPanel panel) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /** ตรึงคอมโพเนนต์ไว้ที่ขอบด้านใดด้านหนึ่ง (เช่น NORTH หรือ SOUTH) โดยคงขนาดเดิมไว้ */
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

    // สร้าง JLabel พร้อมกำหนดการจัดตำแหน่งข้อความ
    static JLabel label(String text, Font font, Color color, int align) {
        JLabel label = new JLabel(text, align);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** สร้างป้ายข้อความ (Badge) ขนาดเล็กที่มีสีพื้นและเส้นขอบ */
    static JLabel badge(String text, Color bg, Color fg, Color line, int padV, int padH) {
        return paintBadge(new JLabel(text, JLabel.CENTER), text, bg, fg, line, padV, padH);
    }

    /** อัปเดตสีและข้อความของป้ายเดิมโดยคงขนาดไว้ */
    static JLabel paintBadge(JLabel badge, String text, Color bg, Color fg, Color line, int padV, int padH) {
        badge.setText(text);
        badge.setFont(SMALL_BOLD);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(line == null ? new EmptyBorder(padV + 1, padH + 1, padV + 1, padH + 1)
                : box(line, 1, padV, padH));
        badge.repaint();
        return badge;
    }

    /** สร้างป้ายส่วนหัวขนาดใหญ่ (Header badge) ของหน้าจอเกม */
    static JLabel topBadge(String text, Color bg, Color fg, Color line, boolean strong) {
        return paintTopBadge(new JLabel(text, JLabel.CENTER), text, bg, fg, line, strong);
    }

    /** อัปเดตสีและข้อความของป้ายส่วนหัว */
    static JLabel paintTopBadge(JLabel badge, String text, Color bg, Color fg, Color line, boolean strong) {
        badge.setText(text);
        badge.setFont(strong ? STRONG : SUBTITLE);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(box(line, 1, strong ? 10 : 8, strong ? 20 : 16));
        badge.repaint();
        return badge;
    }

    /** สร้างป้ายสี่เหลี่ยมสำหรับ Avatar แสดงตัวอักษรหรือตัวเลข */
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

    /** สร้างป้ายสไตล์ปุ่มคีย์บอร์ด (เช่น แนะนำให้กด ENTER) */
    static JLabel keyCap(String text) {
        JLabel key = label(text, KEYCAP, new Color(51, 65, 85), JLabel.CENTER);
        key.setOpaque(true);
        key.setBackground(Color.WHITE);
        key.setBorder(box(SLOT_BORDER, 1, 3, 6));
        return key;
    }

    /** สร้างส่วนหัวการ์ด 2 บรรทัด (ชื่อหลัก, คำอธิบายย่อย, วิดเจ็ตฝั่งขวา) */
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

    /** สร้างกลุ่ม label/panel แนวเดียวตามการจัดตำแหน่งและระยะห่างที่กำหนด */
    static JPanel group(int align, int hgap, int vgap, Container... items) {
        JPanel group = panel(new FlowLayout(align, hgap, vgap));
        for (Container item : items) {
            group.add(item);
        }
        return group;
    }

    /** สร้างหัวคอลัมน์ของตาราง พร้อมความกว้างและความสูงตามที่กำหนด */
    static JLabel tableTitle(String text, int width, int height, int align) {
        JLabel label = label(text, SMALL_BOLD, TEXT_LIGHT, align);
        if (width > 0) {
            label.setPreferredSize(new Dimension(width, height));
        }
        return label;
    }

    /** สร้างกลุ่มหัวข้อแนวตั้งจาก label หลายบรรทัด */
    static JPanel titleStack(int gap, JLabel... labels) {
        JPanel titles = panel(new GridLayout(labels.length, 1, 0, gap));
        for (JLabel label : labels) {
            titles.add(label);
        }
        return titles;
    }

    /** สร้างแถบหัวเรื่องที่มีกลุ่มหัวข้อด้านซ้ายและ actions ตามขอบที่กำหนด */
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

    /** สร้างกลุ่มข้อมูลผู้เล่น: avatar, ชื่อ และ badge ที่ไม่บังคับ */
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

    /**
     * สร้างแถวคะแนนสดสำหรับ Questioner/Guesser
     * maxHeight = 0 หมายถึงไม่ล็อกความสูงของแถว
     */
    static JPanel scoreRow(String name, int index, int score, boolean highlight,
                           int padV, int padH, boolean fixedScoreWidth, int maxHeight) {
        JPanel row = panel(new BorderLayout());
        if (maxHeight > 0) {
            row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        }
        row.setOpaque(highlight);
        row.setBackground(BLUE_LIGHT_BG);
        row.setBorder(highlight ? box(BLUE_BORDER, 1, 4, 8) : new EmptyBorder(padV, padH, padV, padH));

        row.add(playerWho(initialOf(name), avatarColor(index), name, 24, 8, null), BorderLayout.WEST);
        JLabel scoreLabel = label(String.valueOf(score), BODY_BOLD, TEXT_DARK, JLabel.RIGHT);
        if (fixedScoreWidth) {
            scoreLabel.setPreferredSize(new Dimension(24, 24));
        }
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    /** อัปเดตป้ายเวลานับถอยหลังและเปลี่ยนเป็นสีแดงในช่วงท้าย */
    static void setTimeLeft(JLabel badge, int seconds) {
        boolean hurry = seconds <= GameServer.BONUS_WINDOW_SECONDS;
        paintTopBadge(badge, timeText(seconds),
                hurry ? DANGER_BG : WARNING_BG,
                hurry ? DANGER_TEXT : WARNING_TEXT,
                hurry ? DANGER_BORDER : WARNING_BORDER, true);
    }

    /** แสดงสถานะรอก่อนเริ่มนับเวลา */
    static void showWaitingTime(JLabel badge) {
        paintTopBadge(badge, "Time: --:--", WARNING_BG, WARNING_TEXT, WARNING_BORDER, true);
    }

    /** จัดคอมโพเนนต์ไว้กลางแนวนอนด้วย FlowLayout */
    static JPanel centerFlow(Container inner, int padV) {
        JPanel holder = panel(new FlowLayout(FlowLayout.CENTER, 0, padV));
        holder.add(inner);
        return holder;
    }

    /** สร้างปุ่มสไตล์ Flat พร้อมระบบเปลี่ยนสีเมื่อเมาส์ชี้และกด */
    static JButton button(String text, Color bg, Color hover, Color pressed, Color fg, Color line, int padV, int padH) {
        JButton button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setContentAreaFilled(false);   // ต้องปิดก่อนเพื่อไม่ให้ Look and Feel รีเซ็ตค่าสี
        button.setOpaque(true);
        button.setBorderPainted(line != null);
        button.setBorder(line == null ? new EmptyBorder(padV, padH, padV, padH) : box(line, 1, padV, padH));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new ButtonHover(button, bg, hover, pressed));
        return button;
    }

    /** ดึงตัวอักษรตัวแรกของชื่อผู้เล่นสำหรับแสดงบน Avatar */
    static String initialOf(String name) {
        return (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
    }

    /** ดึงสีประจำตัว Avatar ของผู้เล่นตามลำดับ */
    static Color avatarColor(int index) {
        return AVATARS[Math.abs(index) % AVATARS.length];
    }

    /** แปลงวินาทีเป็นรูปแบบข้อความนับถอยหลัง "Time: mm:ss" */
    static String timeText(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("Time: %02d:%02d", safe / 60, safe % 60);
    }

    /** สร้างข้อความเป้าหมายจำนวนรูปทรง เช่น "Target: N Shapes" */
    static String targetText(int shapes) {
        if (shapes <= 0) {
            return "Target: ?";
        }
        return "Target: " + shapes + (shapes == 1 ? " Shape" : " Shapes");
    }

    /**
     * คลาสตรวจจับการเลื่อนเมาส์บนปุ่ม เพื่อสลับสีสถานะปกติ (normal), ชี้ (hover) และกด (pressed)
     */
    static class ButtonHover extends MouseAdapter {
        private final JButton button;
        private final Color normal;
        private final Color hover;
        private final Color pressed;
        private boolean inside;

        /** เก็บสีทั้งสามสถานะของปุ่มเพื่อใช้สลับสีตอนเมาส์ชี้และกด */
        ButtonHover(JButton button, Color normal, Color hover, Color pressed) {
            this.button = button;
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }

        // เมื่อเมาส์เข้าปุ่ม ให้ใช้สี hover
        @Override
        public void mouseEntered(MouseEvent event) {
            inside = true;
            apply(hover);
        }

        // เมื่อเมาส์ออกจากปุ่ม ให้กลับไปใช้สีปกติ
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

        // เมื่อปล่อยเมาส์ ให้กลับไปใช้สี hover หรือสีปกติ
        @Override
        public void mouseReleased(MouseEvent event) {
            apply(inside ? hover : normal);
        }

        // เปลี่ยนสีพื้นหลังปุ่มเฉพาะเมื่อปุ่มยังใช้งานได้
        private void apply(Color color) {
            if (color != null && button.isEnabled()) {
                button.setBackground(color);
            }
        }
    }

}
