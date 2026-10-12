import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * ScoreboardScreen: หน้าจอที่ 5 - สรุปผลคะแนนหลังจบเกม (Remake)
 *
 * กติกาของไฟล์นี้:
 *  - ไม่พึ่งคลาสธีม/คลาสโหลดรูปภายนอก (สีและฟอนต์เก็บเป็นค่าคงที่ในไฟล์ รูปโหลดด้วย Toolkit)
 *  - ไม่วาดเอง (ไม่ override paintComponent) ใช้ JPanel / JLabel / JButton ธรรมดา
 *  - ไม่ใช้ lambda ใช้ inner class แทน
 *  - เงื่อนไขทุกจุดเขียน if { } else { } ครบ ไม่ใช้ ?: และ switch
 */
public class ScoreboardScreen extends JPanel {

    // ===================== สี (คัดลอกค่าจากธีมเดิมเป็นตัวเลข) =====================
    private static final Color BG_LIGHT       = new Color(241, 245, 249);
    private static final Color BG_DARK_HEADER = new Color(24, 34, 53);
    private static final Color CARD_BG        = Color.WHITE;
    private static final Color CARD_BORDER    = new Color(226, 232, 240);
    private static final Color ROW_BG         = new Color(248, 250, 252);

    private static final Color PRIMARY_BLUE   = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER  = new Color(29, 78, 216);
    private static final Color PRIMARY_PRESS  = new Color(30, 64, 175);
    private static final Color BLUE_SOFT_TEXT = new Color(191, 219, 254);

    private static final Color SUCCESS_BG     = new Color(220, 252, 231);
    private static final Color SUCCESS_TEXT   = new Color(22, 101, 52);
    private static final Color SUCCESS_BORDER = new Color(187, 247, 208);

    private static final Color GOLD_BG        = new Color(254, 243, 199);
    private static final Color GOLD_TEXT      = new Color(180, 83, 9);
    private static final Color GOLD_BORDER    = new Color(253, 230, 138);
    private static final Color GOLD_BADGE_BG  = new Color(254, 240, 138);

    private static final Color TEXT_DARK      = new Color(15, 23, 42);
    private static final Color TEXT_MUTED     = new Color(100, 116, 139);
    private static final Color TEXT_LIGHT     = new Color(148, 163, 184);

    // ===================== ฟอนต์ =====================
    private static final Font FONT_TITLE_LARGE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_BODY        = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BODY_BOLD   = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_SMALL       = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL_BOLD  = new Font("Segoe UI", Font.BOLD, 11);

    // โฟลเดอร์ที่ใช้ค้นหารูป (ลำดับเดียวกับของเดิม)
    private static final String[] SEARCH_DIRS = {
        ".",
        "UI_ONLY",
        "object_texture/UI_ONLY",
        "object_texture",
        "../UI_ONLY",
        "../object_texture/UI_ONLY",
        "../../object_texture/UI_ONLY"
    };

    // ข้อมูลตัวอย่าง (เรียงตามอันดับ 1 -> 4)
    private static final String[] PLAYER_NAMES  = {"Aim", "Player 2", "Player 3", "Player 4"};
    private static final int[]    PLAYER_SCORES = {8, 6, 4, 3};

    private final ActionListener onNewGameAction;
    private final ActionListener onQuitAction;

    public ScoreboardScreen() {
        this(null, null);
    }

    public ScoreboardScreen(ActionListener onNewGameAction, ActionListener onQuitAction) {
        this.onNewGameAction = onNewGameAction;
        this.onQuitAction = onQuitAction;

        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        // ซ้ายกว้างคงที่ ขวายืดเต็มพื้นที่ที่เหลือ
        JPanel mainContent = new JPanel(new BorderLayout(32, 0));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(30, 40, 30, 40));

        JPanel leftColumn = buildLeftColumn();
        leftColumn.setPreferredSize(new Dimension(480, 100));

        mainContent.add(leftColumn, BorderLayout.WEST);
        mainContent.add(buildRightColumn(), BorderLayout.CENTER);

        add(mainContent, BorderLayout.CENTER);
    }

    // =====================================================================
    //  คอลัมน์ซ้าย: ถ้วย, ป้าย GAME OVER, ข้อความ, การ์ดคะแนน
    // =====================================================================
    private JPanel buildLeftColumn() {
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        // ดันเนื้อหาลงมาจากขอบบนแทนการจัดกึ่งกลางแนวตั้ง
        left.setBorder(new EmptyBorder(40, 0, 0, 0));

        JLabel gameOverBadge = buildBadge("GAME OVER", BG_DARK_HEADER, Color.WHITE, null, 4, 14);

        JLabel congrats = new JLabel("Congratulations, " + PLAYER_NAMES[0] + "!", JLabel.CENTER);
        congrats.setFont(new Font("Segoe UI", Font.BOLD, 26));
        congrats.setForeground(TEXT_DARK);

        JLabel sub = new JLabel(
            "<html><center>You read the room, matched the shapes,<br>and took first place.</center></html>",
            JLabel.CENTER);
        sub.setFont(FONT_BODY);
        sub.setForeground(TEXT_MUTED);

        JPanel[] items = new JPanel[5];
        items[0] = wrapCenter(buildTrophyPanel(), 0);
        items[1] = wrapCenter(gameOverBadge);
        items[2] = wrapCenter(congrats);
        items[3] = wrapCenter(sub);
        items[4] = wrapCenter(buildScoreCard(), 8);

        left.add(buildStack(items, 12), BorderLayout.CENTER);
        return left;
    }

    /** กล่องสี่เหลี่ยมพื้นเหลืองอ่อน ใส่รูปถ้วย ถ้าหาไฟล์ไม่เจอจะใช้ข้อความแทน */
    private JPanel buildTrophyPanel() {
        JPanel trophy = new JPanel(new BorderLayout());
        trophy.setOpaque(true);
        trophy.setBackground(GOLD_BG);
        trophy.setBorder(new LineBorder(GOLD_BORDER, 2));
        trophy.setPreferredSize(new Dimension(170, 170));

        JLabel trophyLabel;
        ImageIcon icon = loadIcon("trophy.png", 100, 100);
        if (icon != null) {
            // เจอไฟล์รูปถ้วย
            trophyLabel = new JLabel(icon, JLabel.CENTER);
        } else {
            // ไม่เจอไฟล์ ใช้ข้อความแทน
            trophyLabel = new JLabel("TROPHY", JLabel.CENTER);
            trophyLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
            trophyLabel.setForeground(GOLD_TEXT);
        }
        trophy.add(trophyLabel, BorderLayout.CENTER);
        return trophy;
    }

    /** การ์ดสีน้ำเงิน แสดงคะแนนรวมของผู้ชนะ */
    private JPanel buildScoreCard() {
        JPanel card = new JPanel(new GridLayout(3, 1, 0, 0));
        card.setOpaque(true);
        card.setBackground(PRIMARY_BLUE);
        card.setBorder(new EmptyBorder(14, 10, 14, 10));
        card.setPreferredSize(new Dimension(220, 140));

        JLabel fsLabel = new JLabel("FINAL SCORE", JLabel.CENTER);
        fsLabel.setFont(FONT_SMALL_BOLD);
        fsLabel.setForeground(BLUE_SOFT_TEXT);

        JLabel fsValue = new JLabel(String.valueOf(PLAYER_SCORES[0]), JLabel.CENTER);
        fsValue.setFont(new Font("Segoe UI", Font.BOLD, 36));
        fsValue.setForeground(Color.WHITE);

        JLabel fsPoints = new JLabel("POINTS", JLabel.CENTER);
        fsPoints.setFont(FONT_SMALL_BOLD);
        fsPoints.setForeground(BLUE_SOFT_TEXT);

        card.add(fsLabel);
        card.add(fsValue);
        card.add(fsPoints);
        return card;
    }

    // =====================================================================
    //  คอลัมน์ขวา: การ์ดตารางคะแนน
    // =====================================================================
    private JPanel buildRightColumn() {
        JPanel card = new JPanel(new BorderLayout(0, 16));
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
            new LineBorder(CARD_BORDER, 1),
            new EmptyBorder(24, 28, 24, 28)));

        // ---------- หัวการ์ด ----------
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);

        JLabel tag = new JLabel("FINAL RESULTS");
        tag.setFont(FONT_SMALL_BOLD);
        tag.setForeground(PRIMARY_BLUE);

        JLabel title = new JLabel("Scoreboard");
        title.setFont(FONT_TITLE_LARGE);
        title.setForeground(TEXT_DARK);

        titles.add(tag);
        titles.add(title);
        cardHeader.add(titles, BorderLayout.WEST);

        JLabel completeBadge = buildBadge("4 ROUNDS COMPLETE", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER, 6, 14);
        JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        badgeHolder.setOpaque(false);
        badgeHolder.add(completeBadge);
        cardHeader.add(badgeHolder, BorderLayout.EAST);

        card.add(cardHeader, BorderLayout.NORTH);

        // ---------- เนื้อหากลาง: หัวตาราง + แถวผู้เล่น + เส้นแบ่ง + สถิติ ----------
        JPanel[] bodyItems = new JPanel[4];
        bodyItems[0] = buildTableHeader();
        bodyItems[1] = buildRowsPanel();
        bodyItems[2] = buildDivider();
        bodyItems[3] = buildStatsRow();
        card.add(buildStack(bodyItems, 12), BorderLayout.CENTER);

        // ---------- ปุ่มด้านล่าง ----------
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 12, 0));
        btnRow.setOpaque(false);

        JButton newGameBtn = buildButton("NEW GAME", PRIMARY_BLUE, PRIMARY_HOVER, PRIMARY_PRESS,
            Color.WHITE, PRIMARY_BLUE);
        newGameBtn.setPreferredSize(new Dimension(160, 44));
        if (onNewGameAction != null) {
            // มี action จากภายนอก (เช่น MainApp) ให้ผูกเข้าปุ่ม
            newGameBtn.addActionListener(onNewGameAction);
        } else {
            // โหมดทดสอบหน้าเดียว: ไม่มี action ปุ่มนี้จึงไม่ทำอะไร
        }

        JButton quitBtn = buildButton("QUIT", Color.WHITE, BG_LIGHT, CARD_BORDER,
            TEXT_DARK, CARD_BORDER);
        quitBtn.setPreferredSize(new Dimension(160, 44));
        if (onQuitAction != null) {
            // มี action จากภายนอก ให้ผูกเข้าปุ่ม
            quitBtn.addActionListener(onQuitAction);
        } else {
            // ไม่มี action จากภายนอก ใช้ตัวถามยืนยันแล้วออกโปรแกรม
            quitBtn.addActionListener(new QuitAction(this));
        }

        btnRow.add(newGameBtn);
        btnRow.add(quitBtn);
        card.add(btnRow, BorderLayout.SOUTH);

        return card;
    }

    /** หัวตาราง RANK / PLAYER / SCORE (ขนาดคอลัมน์ตรงกับแถวผู้เล่น) */
    private JPanel buildTableHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 13, 0, 13));

        JLabel rankHeader = new JLabel("RANK");
        rankHeader.setFont(FONT_SMALL_BOLD);
        rankHeader.setForeground(TEXT_LIGHT);
        rankHeader.setPreferredSize(new Dimension(36, 20));

        JLabel playerHeader = new JLabel("PLAYER");
        playerHeader.setFont(FONT_SMALL_BOLD);
        playerHeader.setForeground(TEXT_LIGHT);

        JLabel scoreHeader = new JLabel("SCORE", JLabel.RIGHT);
        scoreHeader.setFont(FONT_SMALL_BOLD);
        scoreHeader.setForeground(TEXT_LIGHT);
        scoreHeader.setPreferredSize(new Dimension(50, 20));

        header.add(rankHeader, BorderLayout.WEST);
        header.add(playerHeader, BorderLayout.CENTER);
        header.add(scoreHeader, BorderLayout.EAST);
        return header;
    }

    /** รวมแถวผู้เล่นทุกคนเรียงลงมา เว้นช่อง 6px */
    private JPanel buildRowsPanel() {
        JPanel rows = new JPanel(new GridLayout(PLAYER_NAMES.length, 1, 0, 6));
        rows.setOpaque(false);
        for (int i = 0; i < PLAYER_NAMES.length; i++) {
            rows.add(buildLeaderboardRow(i + 1, PLAYER_NAMES[i], PLAYER_SCORES[i]));
        }
        return rows;
    }

    /** แถวผู้เล่น 1 แถว: อันดับ + avatar + ชื่อ (+ป้าย WINNER) + คะแนน */
    private JPanel buildLeaderboardRow(int rank, String name, int score) {
        boolean isWinner;
        if (rank == 1) {
            isWinner = true;
        } else {
            isWinner = false;
        }

        Color rowBg;
        Color rowBorder;
        Color rankColor;
        Color scoreColor;
        if (isWinner) {
            // แถวผู้ชนะ: พื้นทอง
            rowBg = GOLD_BG;
            rowBorder = GOLD_BORDER;
            rankColor = GOLD_TEXT;
            scoreColor = GOLD_TEXT;
        } else {
            // แถวคนอื่น: พื้นเทาอ่อน (ขอบสีเดียวกับพื้นเพื่อให้ขนาดเท่ากัน)
            rowBg = ROW_BG;
            rowBorder = ROW_BG;
            rankColor = TEXT_MUTED;
            scoreColor = PRIMARY_BLUE;
        }

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(rowBg);
        row.setBorder(new CompoundBorder(
            new LineBorder(rowBorder, 1),
            new EmptyBorder(7, 12, 7, 12)));

        // อันดับ
        JLabel rankLabel = new JLabel(String.valueOf(rank));
        rankLabel.setFont(FONT_BODY_BOLD);
        rankLabel.setForeground(rankColor);
        rankLabel.setPreferredSize(new Dimension(36, 26));
        row.add(rankLabel, BorderLayout.WEST);

        // avatar + ชื่อ + ป้ายผู้ชนะ
        JPanel playerPart = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        playerPart.setOpaque(false);
        playerPart.add(buildAvatar(name.substring(0, 1), getAvatarColor(rank), 28));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FONT_BODY_BOLD);
        nameLabel.setForeground(TEXT_DARK);
        playerPart.add(nameLabel);

        if (isWinner) {
            // ผู้ชนะได้ป้าย WINNER
            playerPart.add(buildBadge("WINNER", GOLD_BADGE_BG, GOLD_TEXT, null, 2, 8));
        } else {
            // คนอื่นไม่มีป้าย
        }
        row.add(playerPart, BorderLayout.CENTER);

        // คะแนน
        JLabel scoreLabel = new JLabel(String.valueOf(score), JLabel.RIGHT);
        scoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        scoreLabel.setForeground(scoreColor);
        scoreLabel.setPreferredSize(new Dimension(50, 26));
        row.add(scoreLabel, BorderLayout.EAST);

        return row;
    }

    /** เลือกสี avatar ตามอันดับ */
    private Color getAvatarColor(int rank) {
        Color result;
        if (rank == 1) {
            result = new Color(234, 179, 8);
        } else if (rank == 2) {
            result = new Color(56, 189, 248);
        } else if (rank == 3) {
            result = new Color(139, 92, 246);
        } else {
            result = new Color(244, 63, 94);
        }
        return result;
    }

    /** แถบสถิติ 3 ช่องใต้เส้นแบ่ง */
    private JPanel buildStatsRow() {
        JPanel stats = new JPanel(new GridLayout(1, 3, 10, 0));
        stats.setOpaque(false);
        stats.add(buildStatItem("Best round", "Round 3"));
        stats.add(buildStatItem("Correct guesses", "8 / 12"));
        stats.add(buildStatItem("Fastest guess", "06.4s"));
        return stats;
    }

    private JPanel buildStatItem(String label, String value) {
        JPanel item = new JPanel(new GridLayout(2, 1, 0, 2));
        item.setOpaque(false);

        JLabel labelText = new JLabel(label);
        labelText.setFont(FONT_SMALL);
        labelText.setForeground(TEXT_LIGHT);

        JLabel valueText = new JLabel(value);
        valueText.setFont(FONT_BODY_BOLD);
        valueText.setForeground(TEXT_DARK);

        item.add(labelText);
        item.add(valueText);
        return item;
    }

    // =====================================================================
    //  ตัวช่วยสร้างหน้าตา (แทน CardPanel / PillBadge / ModernButton / AvatarCircle / Separator)
    // =====================================================================

    /** ป้ายสี่เหลี่ยมพื้นสี (แทน PillBadge) borderColor เป็น null ได้ */
    private JLabel buildBadge(String text, Color bg, Color fg, Color borderColor, int padV, int padH) {
        JLabel badge = new JLabel(text, JLabel.CENTER);
        badge.setFont(FONT_SMALL_BOLD);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        if (borderColor != null) {
            // มีเส้นขอบ
            badge.setBorder(new CompoundBorder(
                new LineBorder(borderColor, 1),
                new EmptyBorder(padV, padH, padV, padH)));
        } else {
            // ไม่มีเส้นขอบ เพิ่ม padding 1px ให้ขนาดเท่ากับแบบมีขอบ
            badge.setBorder(new EmptyBorder(padV + 1, padH + 1, padV + 1, padH + 1));
        }
        return badge;
    }

    /** avatar สี่เหลี่ยมจัตุรัสพื้นสี ตัวอักษรขาวอยู่กลาง (แทน AvatarCircle) */
    private JLabel buildAvatar(String initial, Color color, int size) {
        JLabel avatar = new JLabel(initial, JLabel.CENTER);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(color);
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(size, size));
        return avatar;
    }

    /** ปุ่ม JButton ธรรมดา สีเปลี่ยนตามสถานะผ่าน ButtonStateListener (แทน ModernButton) */
    private JButton buildButton(String text, Color normal, Color hover, Color pressed,
                                Color fg, Color borderColor) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(fg);
        btn.setBackground(normal);
        btn.setContentAreaFilled(false); // เรียกก่อน setOpaque เพราะบาง look and feel จะรีเซ็ต opaque
        btn.setOpaque(true);
        btn.setBorderPainted(true);
        btn.setBorder(new CompoundBorder(
            new LineBorder(borderColor, 1),
            new EmptyBorder(10, 18, 10, 18)));
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.setRolloverEnabled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.getModel().addChangeListener(new ButtonStateListener(btn, normal, hover, pressed));
        return btn;
    }

    /** เส้นแบ่งแนวนอนหนา 1px (แทน separator) */
    private JPanel buildDivider() {
        JPanel line = new JPanel();
        line.setOpaque(true);
        line.setBackground(CARD_BORDER);
        line.setPreferredSize(new Dimension(1, 1));
        return line;
    }

    /**
     * วางหลายชิ้นเรียงลงมาตามลำดับ ชิดบน แต่ละชิ้นสูงเท่าที่ต้องการ (แทนการเรียงแนวตั้งด้วย glue)
     * ใช้ BorderLayout ซ้อนกัน: ชิ้นแรกอยู่ NORTH ที่เหลืออยู่ในแผงถัดไปที่ CENTER
     */
    private JPanel buildStack(JPanel[] items, int gap) {
        JPanel root = new JPanel(new BorderLayout(0, gap));
        root.setOpaque(false);
        JPanel current = root;
        for (int i = 0; i < items.length; i++) {
            current.add(items[i], BorderLayout.NORTH);
            if (i < items.length - 1) {
                // ยังมีชิ้นต่อไป สร้างแผงชั้นถัดไป
                JPanel next = new JPanel(new BorderLayout(0, gap));
                next.setOpaque(false);
                current.add(next, BorderLayout.CENTER);
                current = next;
            } else {
                // ชิ้นสุดท้าย ไม่ต้องสร้างชั้นต่อ
            }
        }
        return root;
    }

    /** ครอบชิ้นเดียวให้อยู่กึ่งกลางแนวนอน */
    private JPanel wrapCenter(JPanel inner, int vPad) {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, vPad));
        holder.setOpaque(false);
        holder.add(inner);
        return holder;
    }

    private JPanel wrapCenter(JLabel inner) {
        JPanel holder = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        holder.setOpaque(false);
        holder.add(inner);
        return holder;
    }

    /** โหลดรูปด้วย Toolkit วนหาตามโฟลเดอร์ ถ้าไม่เจอคืนค่า null */
    private ImageIcon loadIcon(String fileName, int width, int height) {
        ImageIcon result = null;
        boolean found = false;
        for (int i = 0; i < SEARCH_DIRS.length; i++) {
            if (found == false) {
                File f = new File(SEARCH_DIRS[i], fileName);
                if (f.exists() && f.isFile()) {
                    // เจอไฟล์ในโฟลเดอร์นี้
                    Image raw = Toolkit.getDefaultToolkit().getImage(f.getPath());
                    Image scaled = raw.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                    result = new ImageIcon(scaled);
                    found = true;
                } else {
                    // ไม่เจอในโฟลเดอร์นี้ ลองโฟลเดอร์ถัดไป
                }
            } else {
                // เจอแล้ว ข้ามโฟลเดอร์ที่เหลือ
            }
        }
        return result;
    }

    // =====================================================================
    //  Inner classes (แทน lambda)
    // =====================================================================

    /** เปลี่ยนสีพื้นปุ่มตามสถานะ: กดอยู่ -> เมาส์อยู่บนปุ่ม -> ปกติ */
    private static class ButtonStateListener implements ChangeListener {
        private final JButton button;
        private final Color normal;
        private final Color hover;
        private final Color pressed;

        ButtonStateListener(JButton button, Color normal, Color hover, Color pressed) {
            this.button = button;
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }

        @Override
        public void stateChanged(ChangeEvent event) {
            ButtonModel model = button.getModel();
            if (model.isPressed() || model.isArmed()) {
                button.setBackground(pressed);
            } else if (model.isRollover()) {
                button.setBackground(hover);
            } else {
                button.setBackground(normal);
            }
        }
    }

    /** ใช้เมื่อไม่มี onQuitAction จากภายนอก: ถามยืนยันก่อนปิดโปรแกรม */
    private static class QuitAction implements ActionListener {
        private final JPanel parent;

        QuitAction(JPanel parent) {
            this.parent = parent;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            int res = JOptionPane.showConfirmDialog(parent, "Exit the game?", "Quit",
                JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                System.exit(0);
            } else {
                // ผู้เล่นเลือกไม่ออก กลับไปที่หน้าสรุปผลตามเดิม
            }
        }
    }

    /** สร้างหน้าต่างทดสอบหน้าเดียว (แทน lambda ใน invokeLater) */
    private static class ShowFrameTask implements Runnable {
        @Override
        public void run() {
            JFrame frame = new JFrame("Guess What It Looks Like? - Scoreboard");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 750);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new ScoreboardScreen());
            frame.setVisible(true);
        }
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(new ShowFrameTask());
    }
}