import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * ScoreboardScreen: หน้าจอตารางคะแนนรวมสุดท้าย (หน้าจอที่ 5)
 * แสดงอันดับและผลคะแนนรวมทั้งหมดเมื่อจบทัวร์นาเมนต์
 * MainApp จะส่งข้อมูลคะแนนเข้ามาผ่านเมธอด setResults()
 */
public class ScoreboardScreen extends JPanel {

    private static final Color BG_DARK_HEADER = new Color(24, 34, 53); // สีกรมท่าเข้มสำหรับป้าย GAME OVER
    private static final Color GOLD_BADGE_BG = new Color(254, 240, 138); // สีพื้นหลังป้ายทอง WINNER
    private static final Color DANGER_AVATAR = new Color(244, 63, 94);   // สีอวาตาร์เริ่มต้นสำหรับอันดับทั่วไป

    private final ActionListener onNewGameAction; // ตัวจัดการเมื่อกดปุ่มเริ่มเกมใหม่
    private final ActionListener onQuitAction;    // ตัวจัดการเมื่อกดปุ่มออกจากห้อง

    // ข้อมูลคะแนนที่รับมาจากเซิร์ฟเวอร์
    private String[] playerNames = new String[0];
    private int[] playerScores = new int[0];
    private int roundsPlayed;            // จำนวนรอบที่เล่นไปแล้ว
    private int totalRounds;             // จำนวนรอบทั้งหมด (จำนวนผู้เล่น x 2)

    private JLabel congratsLabel;        // ป้ายแสดงความยินดีกับผู้ชนะ
    private JLabel gameOverBadge;        // ป้ายบอกสถานะ GAME OVER
    private JLabel completeBadge;        // ป้ายบอกสถานะการจบทัวร์นาเมนต์
    private JLabel finalScoreValue;      // ป้ายแสดงตัวเลขคะแนนของผู้ชนะ
    private JPanel rowsPanel;            // พาเนลแสดงรายการตารางอันดับ
    private JLabel roundsValue;          // ป้ายแสดงจำนวนรอบที่เล่นไป
    private JLabel playersValue;         // ป้ายแสดงจำนวนผู้เล่น
    private JButton nextBtn;             // ปุ่มเริ่มเกมใหม่

    /**
     * คอนสตรักเตอร์สร้างหน้าจอสกอร์บอร์ด จัดวางแบบ 2 คอลัมน์ (ซ้าย: ถ้วยรางวัลและผู้ชนะ, ขวา: ตารางอันดับ)
     */
    public ScoreboardScreen(ActionListener onNewGameAction, ActionListener onQuitAction) {
        this.onNewGameAction = onNewGameAction;
        this.onQuitAction = onQuitAction;
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        var mainContent = AppDialog.panel(new BorderLayout(32, 0));
        mainContent.setBorder(new EmptyBorder(30, 40, 30, 40));

        var leftColumn = buildLeftColumn();
        leftColumn.setPreferredSize(new Dimension(480, 100));
        mainContent.add(leftColumn, BorderLayout.WEST);
        mainContent.add(buildRightColumn(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
        refresh();
    }

    // =====================================================================
    //  การรับข้อมูลผลการแข่งขันจากเซิร์ฟเวอร์ (Results from Server)
    // =====================================================================

    /**
     * รับข้อมูลคะแนนล่าสุดจากเซิร์ฟเวอร์ จัดเรียงคะแนนจากมากไปน้อย (Insertion Sort) แล้วรีเฟรชหน้าจอ
     */
    public void setResults(String[] names, int[] scores, int rounds, int totalRounds) {
        if (names != null && scores != null && names.length > 0) {
            // ใช้ Insertion Sort เพื่อให้ผู้เล่นที่คะแนนเท่ากันคงลำดับเดิมตามที่เซิร์ฟเวอร์ส่งมา
            String[] sortedNames = new String[names.length];
            int[] sortedScores = new int[names.length];
            for (int i = 0; i < names.length; i++) {
                sortedNames[i] = names[i];
                sortedScores[i] = (i < scores.length) ? scores[i] : 0;
            }
            for (int i = 1; i < sortedNames.length; i++) {
                String name = sortedNames[i];
                int score = sortedScores[i];
                int j = i - 1;
                while (j >= 0 && sortedScores[j] < score) {
                    sortedNames[j + 1] = sortedNames[j];
                    sortedScores[j + 1] = sortedScores[j];
                    j--;
                }
                sortedNames[j + 1] = name;
                sortedScores[j + 1] = score;
            }
            playerNames = sortedNames;
            playerScores = sortedScores;
        }
        roundsPlayed = rounds;
        this.totalRounds = totalRounds;
        refresh();
    }

    /**
     * เปิด/ปิดการทำงานของปุ่ม NEW GAME (เฉพาะโฮสต์เท่านั้นที่สามารถกดได้)
     */
    public void setNextEnabled(boolean enabled) {
        nextBtn.setEnabled(enabled);
        nextBtn.setToolTipText(enabled
                ? "Continue to the next round, or start a fresh tournament."
                : "Only the host can continue or start a new game.");
    }

    /**
     * วาดเนื้อหาทั้งหมดใหม่ตามคะแนนและสถานะรอบล่าสุด
     */
    private void refresh() {
        if (playerNames.length > 0) {
            congratsLabel.setText("Congratulations, " + playerNames[0] + "!");
            finalScoreValue.setText(String.valueOf(playerScores[0]));
        } else {
            congratsLabel.setText("No players yet");
            finalScoreValue.setText("0");
        }

        gameOverBadge.setText("GAME OVER");
        completeBadge.setText("TOURNAMENT COMPLETE");
        nextBtn.setText("NEW GAME");

        roundsValue.setText(roundsPlayed + " / " + totalRounds);
        playersValue.setText(String.valueOf(playerNames.length));

        rowsPanel.removeAll();
        rowsPanel.setLayout(new GridLayout(Math.max(1, playerNames.length), 1, 0, 6));
        for (int i = 0; i < playerNames.length; i++) {
            rowsPanel.add(buildLeaderboardRow(i + 1, playerNames[i], playerScores[i]));
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
        revalidate();
        repaint();
    }

    // =====================================================================
    //  คอลัมน์ซ้าย: ถ้วยรางวัล, ผู้ชนะ และคะแนนสูงสุด (Left Column)
    // =====================================================================

    private JPanel buildLeftColumn() {
        var left = AppDialog.panel(new BorderLayout());
        left.setBorder(new EmptyBorder(40, 0, 0, 0));

        gameOverBadge = AppDialog.badge("GAME OVER", BG_DARK_HEADER, Color.WHITE, null, 4, 14);
        congratsLabel = AppDialog.label("", AppDialog.TITLE_LARGE,
                AppDialog.TEXT_DARK, JLabel.CENTER);

        var items = new JPanel[] {
            AppDialog.centerFlow(buildTrophyPanel(), 0),
            AppDialog.centerFlow(gameOverBadge, 0),
            AppDialog.centerFlow(congratsLabel, 0),
            AppDialog.centerFlow(buildScoreCard(), 8)
        };
        left.add(stackItems(items, 12), BorderLayout.CENTER);
        return left;
    }

    /** กล่องสีทองแสดงรูปถ้วยรางวัล (Trophy) */
    private JPanel buildTrophyPanel() {
        var trophy = new JPanel(new BorderLayout());
        trophy.setBackground(AppDialog.GOLD_BG);
        trophy.setBorder(new LineBorder(AppDialog.GOLD_BORDER, 2));
        trophy.setPreferredSize(new Dimension(170, 170));
        trophy.add(ShapeImages.shape("Trophy", 100), BorderLayout.CENTER);
        return trophy;
    }

    /** การ์ดสีฟ้าแสดงคะแนนรวมของผู้ชนะ */
    private JPanel buildScoreCard() {
        var card = new JPanel(new GridLayout(3, 1, 0, 0));
        card.setBackground(AppDialog.PRIMARY_BLUE);
        card.setBorder(new EmptyBorder(14, 10, 14, 10));
        card.setPreferredSize(new Dimension(220, 140));

        finalScoreValue = AppDialog.label("0", AppDialog.f(Font.BOLD, 36),
                Color.WHITE, JLabel.CENTER);
        card.add(AppDialog.label("FINAL SCORE", AppDialog.SMALL_BOLD, AppDialog.BLUE_BORDER, JLabel.CENTER));
        card.add(finalScoreValue);
        card.add(AppDialog.label("POINTS", AppDialog.SMALL_BOLD, AppDialog.BLUE_BORDER, JLabel.CENTER));
        return card;
    }

    // =====================================================================
    //  คอลัมน์ขวา: ตารางอันดับและสถิติ (Right Column)
    // =====================================================================

    private JPanel buildRightColumn() {
        var card = AppDialog.card(new BorderLayout(0, 16), 24, 28);

        var titles = AppDialog.titleStack(2,
                AppDialog.label("FINAL RESULTS", AppDialog.SMALL_BOLD, AppDialog.PRIMARY_BLUE),
                AppDialog.label("Scoreboard", AppDialog.TITLE_24, AppDialog.TEXT_DARK));

        completeBadge = AppDialog.badge("TOURNAMENT COMPLETE", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                AppDialog.SUCCESS_BORDER, 6, 14);
        var badgeHolder = AppDialog.group(FlowLayout.RIGHT, 0, 6, completeBadge);

        var cardHeader = AppDialog.pageHeader(titles, badgeHolder, BorderLayout.EAST, 0, 0, 0, 0);
        card.add(cardHeader, BorderLayout.NORTH);

        card.add(stackItems(new JPanel[] {buildTableHeader(), buildRowsPanel(), buildDivider(), buildStatsRow()}, 12),
                BorderLayout.CENTER);

        // ปุ่ม NEW GAME สำหรับเริ่มทัวร์นาเมนต์ใหม่
        nextBtn = AppDialog.button("NEW GAME", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER, AppDialog.PRIMARY_PRESS,
                Color.WHITE, AppDialog.PRIMARY_BLUE, 10, 18);
        nextBtn.setPreferredSize(new Dimension(160, 44));
        nextBtn.addActionListener(onNewGameAction);

        // ปุ่ม QUIT สำหรับออกจากห้องกลับไปหน้าแรก
        var quitBtn = AppDialog.button("QUIT", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 10, 18);
        quitBtn.setPreferredSize(new Dimension(160, 44));
        quitBtn.addActionListener(onQuitAction);

        var buttonRow = AppDialog.panel(new GridLayout(1, 2, 12, 0));
        buttonRow.add(nextBtn);
        buttonRow.add(quitBtn);
        card.add(buttonRow, BorderLayout.SOUTH);
        return card;
    }

    /** ส่วนหัวตาราง: RANK, PLAYER, SCORE */
    private JPanel buildTableHeader() {
        var header = AppDialog.panel(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(0, 13, 0, 13));

        header.add(AppDialog.tableTitle("RANK", 36, 20, JLabel.LEFT), BorderLayout.WEST);
        header.add(AppDialog.tableTitle("PLAYER", 0, 20, JLabel.LEADING), BorderLayout.CENTER);
        header.add(AppDialog.tableTitle("SCORE", 50, 20, JLabel.RIGHT), BorderLayout.EAST);
        return header;
    }

    private JPanel buildRowsPanel() {
        rowsPanel = AppDialog.panel();
        return rowsPanel;
    }

    /**
     * สร้างแถวอันดับแต่ละแถว: อันดับ, Avatar, ชื่อผู้เล่น (มีป้าย WINNER หากเป็นอันดับ 1) และคะแนนรวม
     */
    private JPanel buildLeaderboardRow(int rank, String name, int score) {
        boolean winner = rank == 1;
        Color rowBg = winner ? AppDialog.GOLD_BG : AppDialog.ROW_BG;
        Color rowBorder = winner ? AppDialog.GOLD_BORDER : AppDialog.ROW_BG;
        Color rankColor = winner ? AppDialog.GOLD_TEXT : AppDialog.TEXT_MUTED;
        Color scoreColor = winner ? AppDialog.GOLD_TEXT : AppDialog.PRIMARY_BLUE;

        var row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(rowBg);
        row.setBorder(new CompoundBorder(new LineBorder(rowBorder, 1), new EmptyBorder(7, 12, 7, 12)));

        var rankLabel = AppDialog.label(String.valueOf(rank), AppDialog.BODY_BOLD, rankColor);
        rankLabel.setPreferredSize(new Dimension(36, 26));
        row.add(rankLabel, BorderLayout.WEST);

        var winnerBadge = winner
                ? AppDialog.badge("WINNER", GOLD_BADGE_BG, AppDialog.GOLD_TEXT, null, 2, 8)
                : null;
        row.add(AppDialog.playerWho(name.substring(0, 1), rankColor(rank), name, 28, 10, winnerBadge),
                BorderLayout.CENTER);

        var scoreLabel = AppDialog.label(String.valueOf(score), AppDialog.TITLE, scoreColor, JLabel.RIGHT);
        scoreLabel.setPreferredSize(new Dimension(50, 26));
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    /** กำหนดสีของอวาตาร์ตามลำดับอันดับ (1: ทอง, 2: ฟ้า, 3: ม่วง) */
    private static Color rankColor(int rank) {
        if (rank == 1) {
            return new Color(234, 179, 8);
        }
        if (rank == 2) {
            return new Color(56, 189, 248);
        }
        return rank == 3 ? new Color(139, 92, 246) : DANGER_AVATAR;
    }

    /** สร้างแถวแสดงสถิติสรุป: จำนวนรอบที่เล่น, จำนวนผู้เล่น */
    private JPanel buildStatsRow() {
        roundsValue = AppDialog.label("", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        playersValue = AppDialog.label("", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);

        var stats = AppDialog.panel(new GridLayout(1, 2, 10, 0));
        stats.add(statItem("Rounds played", roundsValue));
        stats.add(statItem("Players", playersValue));
        return stats;
    }

    private static JPanel statItem(String title, JLabel value) {
        var item = AppDialog.panel(new GridLayout(2, 1, 0, 2));
        item.add(AppDialog.label(title, AppDialog.SMALL, AppDialog.TEXT_LIGHT));
        item.add(value);
        return item;
    }

    /** เส้นคั่นแนวนอน 1px */
    private static JPanel buildDivider() {
        var line = new JPanel();
        line.setBackground(AppDialog.CARD_BORDER);
        line.setPreferredSize(new Dimension(1, 1));
        return line;
    }

    /** ซ้อนคอมโพเนนต์เรียงจากบนลงล่างตามระยะห่างที่กำหนด */
    private static JPanel stackItems(JPanel[] items, int gap) {
        var root = AppDialog.panel(new BorderLayout(0, gap));
        var current = root;
        for (int i = 0; i < items.length; i++) {
            current.add(items[i], BorderLayout.NORTH);
            if (i < items.length - 1) {
                var next = AppDialog.panel(new BorderLayout(0, gap));
                current.add(next, BorderLayout.CENTER);
                current = next;
            }
        }
        return root;
    }

}
