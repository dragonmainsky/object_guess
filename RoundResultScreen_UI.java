import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/** ส่วนหน้าตาของหน้าจอสรุปผลรอบ */
public class RoundResultScreen_UI extends JPanel {

    private static final Color SUCCESS_LINE = new Color(22, 163, 74);  // สีกรอบสีเขียวสำหรับรูปทรงที่ทายถูก
    private static final Color DANGER_LINE = new Color(220, 38, 38);   // สีกรอบสีแดงสำหรับรูปทรงที่ทายผิด
    private static final int SECRET_ICON = 110;                        // ขนาดรูปทรงเฉลยความลับ
    private static final int SECRET_GAP = 16;                          // ระยะห่างระหว่างรูปทรงเฉลย
    private static final int GUESS_ICON = 42;                          // ขนาดรูปทรงที่ผู้เล่นแต่ละคนทาย
    private static final int AVATAR_SIZE = 34;                         // ขนาดอวาตาร์ของผู้เล่น
    private static final int ROW_GAP = 6;                              // ระยะห่างระหว่างแถวผู้เล่น

    protected JLabel tagLabel;            // ป้ายประเภทการสรุปผล (ROUND SUMMARY หรือ FINAL ROUND SUMMARY)
    protected JLabel titleLabel;          // ป้ายบอกหมายเลขรอบ เช่น "Round 1 of 4"
    protected JLabel questionerLabel;     // ป้ายบอกชื่อผู้ตั้งโจทย์ (Questioner)
    protected JLabel reasonBadge;         // ป้ายบอกเหตุผลที่จบรอบ (เช่น หมดเวลา หรือทายครบทุกคน)
    protected JLabel secretTitleLabel;    // ป้ายหัวข้อรูปทรงเฉลย
    protected JPanel secretRow;           // แถวแสดงรายการรูปทรงเฉลย
    protected JLabel resultsTitleLabel;   // ป้ายหัวข้อตารางคะแนนรอบ
    protected JLabel resultsBadge;        // ป้ายแสดงจำนวนผู้เล่นในรอบ
    protected JPanel rowsPanel;           // พาเนลรายการแถวผลคะแนนของผู้เล่นแต่ละคน
    protected JLabel countdownLabel;      // ป้ายนับถอยหลังก่อนเข้ารอบถัดไป

    /** สร้างหน้าจอสรุปผลรอบ */
    public RoundResultScreen_UI() {
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        JPanel mainContent = AppDialog.panel(new BorderLayout(24, 16));
        mainContent.setBorder(new EmptyBorder(24, 32, 24, 32));
        mainContent.add(buildHeader(), BorderLayout.NORTH);
        mainContent.add(buildBody(), BorderLayout.CENTER);
        mainContent.add(buildFooter(), BorderLayout.SOUTH);
        add(mainContent, BorderLayout.CENTER);
    }

    // =====================================================================
    //  ส่วนหัว (Header)
    // =====================================================================

    /** ส่วนหัว: รอบ, Questioner, เหตุผล */
    private JPanel buildHeader() {
        tagLabel = AppDialog.label("ROUND SUMMARY", AppDialog.SUBTITLE, AppDialog.PRIMARY_BLUE);
        titleLabel = AppDialog.label("Round 1", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK);
        questionerLabel = AppDialog.label("Questioner: -", AppDialog.SCREEN_SUB, AppDialog.TEXT_MUTED);
        JPanel titles = AppDialog.titleStack(2, tagLabel, titleLabel, questionerLabel);

        reasonBadge = AppDialog.badge("Round complete", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                SUCCESS_LINE, 8, 16);
        reasonBadge.setFont(AppDialog.SUBTITLE);
        JPanel badgeHolder = AppDialog.group(FlowLayout.RIGHT, 0, 6, reasonBadge);
        return AppDialog.pageHeader(titles, badgeHolder, BorderLayout.EAST, 0, 0, 0, 0);
    }

    // =====================================================================
    // ส่วนเนื้อหา
    // =====================================================================

    /** ส่วนเนื้อหา: เฉลยและตารางคะแนน */
    private JPanel buildBody() {
        JPanel body = AppDialog.panel(new BorderLayout(0, 16));
        body.add(buildSecretCard(), BorderLayout.NORTH);
        body.add(buildResultsCard(), BorderLayout.CENTER);
        return body;
    }

    /** การ์ดเฉลยรูปทรง */
    private JPanel buildSecretCard() {
        JPanel card = AppDialog.card(new BorderLayout(0, 14), 18, 20);

        secretTitleLabel = AppDialog.label("Secret shapes", AppDialog.SUBTITLE, AppDialog.TEXT_DARK);
        card.add(secretTitleLabel, BorderLayout.NORTH);

        // แถวรูปทรงเฉลย
        secretRow = AppDialog.panel(new FlowLayout(FlowLayout.CENTER, SECRET_GAP, 8));
        card.add(secretRow, BorderLayout.CENTER);
        return card;
    }

    /** รูปทรงเฉลยหนึ่งรูป */
    private JPanel buildSecretItem(String shapeName) {
        JPanel item = AppDialog.panel(new BorderLayout(0, 6));
        item.add(shapeIcon(shapeName, SECRET_ICON, AppDialog.CARD_BORDER, 1), BorderLayout.CENTER);
        item.add(AppDialog.label(shapeName, AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER), BorderLayout.SOUTH);
        return item;
    }

    /** การ์ดตารางคะแนน */
    private JPanel buildResultsCard() {
        JPanel card = AppDialog.card(new BorderLayout(0, 14), 18, 20);

        resultsTitleLabel = AppDialog.label("Round score", AppDialog.TITLE, AppDialog.TEXT_DARK);
        resultsBadge = AppDialog.badge("-", AppDialog.BG_LIGHT, AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 4, 10);
        JPanel titles = AppDialog.panel(new BorderLayout());
        titles.add(resultsTitleLabel, BorderLayout.WEST);
        JPanel badgeHolder = AppDialog.group(FlowLayout.RIGHT, 0, 0, resultsBadge);
        titles.add(badgeHolder, BorderLayout.EAST);
        card.add(titles, BorderLayout.NORTH);

        // พื้นที่เลื่อนของตาราง
        rowsPanel = AppDialog.stack(AppDialog.panel());
        JScrollPane scroll = new JScrollPane(rowsPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBlockIncrement(80);
        scroll.setColumnHeaderView(buildTableHeader());
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    /** หัวตาราง: PLAYER, GUESSED, ROUND, TOTAL */
    private JPanel buildTableHeader() {
        JPanel header = AppDialog.panel(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(0, 15, 0, 15));
        header.add(AppDialog.tableTitle("PLAYER", 0, 18, JLabel.LEFT), BorderLayout.WEST);
        header.add(AppDialog.tableTitle("GUESSED", 0, 18, JLabel.LEFT), BorderLayout.CENTER);

        JPanel right = AppDialog.panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(AppDialog.tableTitle("ROUND", 86, 18, JLabel.RIGHT));
        right.add(AppDialog.tableTitle("TOTAL", 50, 18, JLabel.RIGHT));
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // =====================================================================
    // ส่วนท้าย
    // =====================================================================

    /** ส่วนท้าย: เวลานับถอยหลัง */
    private JPanel buildFooter() {
        JPanel footer = AppDialog.panel(new BorderLayout(12, 0));
        countdownLabel = AppDialog.label("", AppDialog.BODY_BOLD, AppDialog.PRIMARY_BLUE);
        footer.add(countdownLabel, BorderLayout.WEST);
        return footer;
    }

    // =====================================================================
    // เมธอดแสดงผลข้อมูล
    // =====================================================================

    /** วาดส่วนหัวของรอบ */
    protected void renderHeader(int round, int totalRounds, boolean isFinal, String questioner) {
        String questionerName;
        if (questioner == null) {
            questionerName = "";
        } else {
            questionerName = questioner;
        }
        titleLabel.setText("Round " + round + " of " + totalRounds);
        String questionerDisplay;
        if (questionerName.isEmpty()) {
            questionerDisplay = "-";
        } else {
            questionerDisplay = questionerName;
        }
        questionerLabel.setText("Questioner: " + questionerDisplay);
        if (isFinal) {
            tagLabel.setText("FINAL ROUND SUMMARY");
        } else {
            tagLabel.setText("ROUND SUMMARY");
        }
    }

    /** อัปเดตป้ายเหตุผล */
    protected void renderReason(String text, boolean danger) {
        if (danger) {
            AppDialog.paintBadge(reasonBadge, text, AppDialog.DANGER_BG, AppDialog.DANGER_TEXT,
                    DANGER_LINE, 8, 16);
        } else {
            AppDialog.paintBadge(reasonBadge, text, AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                    SUCCESS_LINE, 8, 16);
        }
    }

    /** แสดงรูปทรงเฉลย */
    protected void renderSecret(String[] secret) {
        secretRow.removeAll();
        // secret ไม่เป็น null (เหลือตรวจว่ามีรูปทรงหรือไม่)
        if (secret.length > 0) {
            for (String shapeName : secret) {
                secretRow.add(buildSecretItem(shapeName));
            }
            secretTitleLabel.setText("Secret shapes (" + secret.length + ")");
        } else {
            secretRow.add(AppDialog.label("No secret shapes were locked in this round.", AppDialog.BODY,
                    AppDialog.TEXT_MUTED));
            secretTitleLabel.setText("Secret shapes");
        }
        secretRow.revalidate();
        secretRow.repaint();
    }

    /** สร้างแถวผลของผู้เล่นหนึ่งคน */
    protected void renderResults(String[] names, int[] deltas, int[] totals, String[][] guesses,
                                 int[] correctCounts, boolean[] bonuses, String questioner, String[] secret) {
        // ค่าจาก showResult() ไม่เป็น null
        String questionerName = questioner;
        String playerWord;
        if (names.length == 1) {
            playerWord = " player";
        } else {
            playerWord = " players";
        }
        resultsBadge.setText(names.length + playerWord);

        // แถวผลของผู้เล่น
        rowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            int delta = deltas[i];
            int total = totals[i];
            String[] guess = guesses[i];
            int correct = correctCounts[i];
            boolean bonus = bonuses[i];
            JPanel row = buildPlayerRow(i, names[i], delta, total, guess, correct, bonus,
                    names[i].equals(questionerName), secret);
            row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            rowsPanel.add(row);
            if (i < names.length - 1) {
                rowsPanel.add(AppDialog.strut(ROW_GAP));
            }
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    /** วาดข้อความนับถอยหลัง */
    protected void renderCountdown(int seconds, boolean isFinal) {
        String prefix;
        if (isFinal) {
            prefix = "Final scoreboard in ";
        } else {
            prefix = "Next round in ";
        }
        countdownLabel.setText(prefix + seconds + "s...");
    }

    // =====================================================================
    // ฟังก์ชันช่วยสร้างแถว
    // =====================================================================

    /** แถวผลงานผู้เล่น */
    private JPanel buildPlayerRow(int index, String name, int delta, int total, String[] guess, int correct,
                                  boolean bonus, boolean isQuestioner, String[] secret) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(AppDialog.ROW_BG);
        row.setBorder(new CompoundBorder(new LineBorder(AppDialog.ROW_BG, 1), new EmptyBorder(10, 14, 10, 14)));

        // ข้อมูลผู้เล่น
        JLabel questionerBadge;
        if (isQuestioner) {
            questionerBadge = AppDialog.badge("Questioner", AppDialog.CARD_BORDER, AppDialog.TEXT_DARK,
                    AppDialog.CARD_BORDER, 2, 8);
        } else {
            questionerBadge = null;
        }
        row.add(AppDialog.playerWho(AppDialog.initialOf(name), AppDialog.avatarColor(index), name,
                AVATAR_SIZE, 8, questionerBadge), BorderLayout.WEST);

        // รูปทรงที่ทาย
        JPanel guessed = AppDialog.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        if (isQuestioner) {
            guessed.add(mutedLabel("picked the secret"));
        } else if (guess.length == 0) {
            guessed.add(mutedLabel("did not guess"));
        } else {
            for (String shapeName : guess) {
                Color borderColor;
                if (containsShape(secret, shapeName)) {
                    borderColor = SUCCESS_LINE;
                } else {
                    borderColor = DANGER_LINE;
                }
                guessed.add(shapeIcon(shapeName, GUESS_ICON, borderColor, 2));
            }
            guessed.add(mutedLabel(correct + " / " + guess.length + " correct"));
        }
        row.add(guessed, BorderLayout.CENTER);

        // คะแนน
        JPanel score = AppDialog.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (bonus) {
            score.add(AppDialog.badge("BONUS +2", AppDialog.GOLD_BG, AppDialog.GOLD_TEXT, AppDialog.GOLD_BORDER, 3, 8));
        }
        if (delta > 0) {
            score.add(AppDialog.badge("+" + delta, AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT, SUCCESS_LINE, 3, 10));
        } else {
            score.add(AppDialog.badge("+0", AppDialog.BG_LIGHT, AppDialog.TEXT_MUTED, AppDialog.CARD_BORDER, 3, 10));
        }

        JLabel totalLabel;
        if (total > 0) {
            totalLabel = AppDialog.label(String.valueOf(total), AppDialog.STRONG,
                    AppDialog.PRIMARY_BLUE, JLabel.RIGHT);
        } else {
            totalLabel = AppDialog.label(String.valueOf(total), AppDialog.STRONG,
                    AppDialog.TEXT_MUTED, JLabel.RIGHT);
        }
        totalLabel.setPreferredSize(new Dimension(46, 24));
        score.add(totalLabel);
        row.add(score, BorderLayout.EAST);
        return row;
    }

    /** ตรวจว่ารูปทรงอยู่ในชุดเฉลย */
    private static boolean containsShape(String[] shapes, String wanted) {
        // shapes และ wanted ไม่เป็น null
        for (String shape : shapes) {
            if (wanted.equalsIgnoreCase(shape)) {
                return true;
            }
        }
        return false;
    }

    /** ข้อความสีเทา */
    private static JLabel mutedLabel(String text) {
        return AppDialog.label(text, AppDialog.SMALL, AppDialog.TEXT_MUTED);
    }

    /** รูปภาพรูปทรงพร้อมกรอบสี */
    private static JPanel shapeIcon(String shapeName, int size, Color line, int width) {
        JPanel picture = ShapeImages.shape(shapeName, size);
        picture.setToolTipText(shapeName);
        picture.setBorder(new LineBorder(line, width));
        return picture;
    }
}
