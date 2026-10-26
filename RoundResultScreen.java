import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * RoundResultScreen: หน้าจอแสดงผลคะแนนและเฉลยของแต่ละรอบ (หน้าจอคั่นระหว่างรอบ)
 * สร้างขึ้นจากข้อมูล ROUND_RESULT ที่ได้รับจากเซิร์ฟเวอร์เท่านั้น
 * ทำให้ทุกเครื่องในห้องแสดงผลรูปทรงเฉลยความลับและตัวเลขคะแนนตรงกันทั้งหมด
 */
public class RoundResultScreen extends JPanel {

    private static final Color SUCCESS_LINE = new Color(22, 163, 74);  // สีกรอบสีเขียวสำหรับรูปทรงที่ทายถูก
    private static final Color DANGER_LINE = new Color(220, 38, 38);   // สีกรอบสีแดงสำหรับรูปทรงที่ทายผิด
    private static final int SECRET_ICON = 110;                        // ขนาดรูปทรงเฉลยความลับ
    private static final int SECRET_GAP = 16;                          // ระยะห่างระหว่างรูปทรงเฉลย
    private static final int GUESS_ICON = 42;                          // ขนาดรูปทรงที่ผู้เล่นแต่ละคนทาย
    private static final int AVATAR_SIZE = 34;                         // ขนาดอวาตาร์ของผู้เล่น
    private static final int ROW_GAP = 6;                              // ระยะห่างระหว่างแถวผู้เล่น
    private static final int COUNTDOWN_SECONDS = GameServer.INTERMISSION_SECONDS; // เวลานับถอยหลังช่วงพักรอบ (10 วินาที)

    private JLabel tagLabel;            // ป้ายประเภทการสรุปผล (ROUND SUMMARY หรือ FINAL ROUND SUMMARY)
    private JLabel titleLabel;          // ป้ายบอกหมายเลขรอบ เช่น "Round 1 of 4"
    private JLabel questionerLabel;     // ป้ายบอกชื่อผู้ตั้งโจทย์ (Questioner)
    private JLabel reasonBadge;         // ป้ายบอกเหตุผลที่จบรอบ (เช่น หมดเวลา หรือทายครบทุกคน)
    private JLabel secretTitleLabel;    // ป้ายหัวข้อรูปทรงเฉลย
    private JPanel secretRow;           // แถวแสดงรายการรูปทรงเฉลย
    private JLabel resultsTitleLabel;   // ป้ายหัวข้อตารางคะแนนรอบ
    private JLabel resultsBadge;        // ป้ายแสดงจำนวนผู้เล่นในรอบ
    private JPanel rowsPanel;           // พาเนลรายการแถวผลคะแนนของผู้เล่นแต่ละคน
    private JLabel countdownLabel;      // ป้ายนับถอยหลังก่อนเข้ารอบถัดไป

    private Timer countdown;            // ตัวนับถอยหลัง 1 ตัวต่อรอบ จะหยุดเมื่อเริ่มรอบใหม่
    private int secondsLeft;            // จำนวนวินาทีที่เหลือก่อนเปลี่ยนหน้าจอ

    /**
     * คอนสตรักเตอร์สร้างหน้าจอสรุปผลของรอบ แบ่งเป็น ส่วนหัว (Header), เนื้อหา (Body), และส่วนท้าย (Footer)
     */
    public RoundResultScreen() {
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        var mainContent = AppDialog.panel(new BorderLayout(24, 16));
        mainContent.setBorder(new EmptyBorder(24, 32, 24, 32));
        mainContent.add(buildHeader(), BorderLayout.NORTH);
        mainContent.add(buildBody(), BorderLayout.CENTER);
        mainContent.add(buildFooter(), BorderLayout.SOUTH);
        add(mainContent, BorderLayout.CENTER);
    }

    // =====================================================================
    //  ส่วนหัว (Header)
    // =====================================================================

    /**
     * สร้างส่วนหัวด้านบน: แสดงหมายเลขรอบ, ชื่อ Questioner และป้ายเหตุผลที่จบรอบ
     */
    private JPanel buildHeader() {
        tagLabel = AppDialog.label("ROUND SUMMARY", AppDialog.SUBTITLE, AppDialog.PRIMARY_BLUE);
        titleLabel = AppDialog.label("Round 1", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK);
        questionerLabel = AppDialog.label("Questioner: -", AppDialog.SCREEN_SUB, AppDialog.TEXT_MUTED);
        var titles = AppDialog.titleStack(2, tagLabel, titleLabel, questionerLabel);

        reasonBadge = AppDialog.badge("Round complete", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                SUCCESS_LINE, 8, 16);
        reasonBadge.setFont(AppDialog.SUBTITLE);
        var badgeHolder = AppDialog.group(FlowLayout.RIGHT, 0, 6, reasonBadge);
        return AppDialog.pageHeader(titles, badgeHolder, BorderLayout.EAST, 0, 0, 0, 0);
    }

    // =====================================================================
    //  ส่วนเนื้อหา: รูปทรงเฉลยความลับด้านบน และตารางผลคะแนนด้านล่าง (Body)
    // =====================================================================

    private JPanel buildBody() {
        var body = AppDialog.panel(new BorderLayout(0, 16));
        body.add(buildSecretCard(), BorderLayout.NORTH);
        body.add(buildResultsCard(), BorderLayout.CENTER);
        return body;
    }

    /**
     * การ์ดแสดงรูปทรงเฉลยความลับของรอบนี้
     */
    private JPanel buildSecretCard() {
        var card = AppDialog.card(new BorderLayout(0, 14), 18, 20);

        secretTitleLabel = AppDialog.label("Secret shapes", AppDialog.SUBTITLE, AppDialog.TEXT_DARK);
        card.add(secretTitleLabel, BorderLayout.NORTH);

        // แถวแสดงรูปทรงเฉลยเรียงกึ่งกลางแนวนอน
        secretRow = AppDialog.panel(new FlowLayout(FlowLayout.CENTER, SECRET_GAP, 8));
        card.add(secretRow, BorderLayout.CENTER);
        return card;
    }

    /**
     * แสดงแต่ละรูปทรงเฉลย: รูปภาพพร้อมชื่อรูปทรงตัวอักษรเล็กด้านล่าง
     */
    private JPanel buildSecretItem(String shapeName) {
        var item = AppDialog.panel(new BorderLayout(0, 6));
        item.add(shapeIcon(shapeName, SECRET_ICON, AppDialog.CARD_BORDER, 1), BorderLayout.CENTER);
        item.add(AppDialog.label(shapeName, AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER), BorderLayout.SOUTH);
        return item;
    }

    /**
     * การ์ดตารางแสดงคะแนนของผู้เล่นทุกคนในรอบนี้
     */
    private JPanel buildResultsCard() {
        var card = AppDialog.card(new BorderLayout(0, 14), 18, 20);

        resultsTitleLabel = AppDialog.label("Round score", AppDialog.TITLE, AppDialog.TEXT_DARK);
        resultsBadge = AppDialog.badge("-", AppDialog.BG_LIGHT, AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 4, 10);
        var titles = AppDialog.panel(new BorderLayout());
        titles.add(resultsTitleLabel, BorderLayout.WEST);
        var badgeHolder = AppDialog.group(FlowLayout.RIGHT, 0, 0, resultsBadge);
        titles.add(badgeHolder, BorderLayout.EAST);
        card.add(titles, BorderLayout.NORTH);

        // แถวคะแนนของผู้เล่น รองรับการเลื่อน (Scroll) เมื่อมีผู้เล่นหลายคน
        rowsPanel = AppDialog.stack(AppDialog.panel());
        var scroll = new JScrollPane(rowsPanel);
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

    /**
     * ส่วนหัวของตาราง: คอลัมน์ PLAYER, GUESSED, ROUND, TOTAL
     */
    private JPanel buildTableHeader() {
        var header = AppDialog.panel(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(0, 15, 0, 15));
        header.add(AppDialog.tableTitle("PLAYER", 0, 18, JLabel.LEFT), BorderLayout.WEST);
        header.add(AppDialog.tableTitle("GUESSED", 0, 18, JLabel.LEFT), BorderLayout.CENTER);

        var right = AppDialog.panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(AppDialog.tableTitle("ROUND", 86, 18, JLabel.RIGHT));
        right.add(AppDialog.tableTitle("TOTAL", 50, 18, JLabel.RIGHT));
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // =====================================================================
    //  ส่วนท้าย: เวลานับถอยหลังก่อนเริ่มรอบต่อไป (Footer)
    // =====================================================================

    private JPanel buildFooter() {
        var footer = AppDialog.panel(new BorderLayout(12, 0));
        countdownLabel = AppDialog.label("Next round in " + COUNTDOWN_SECONDS + "s...", AppDialog.BODY_BOLD,
                AppDialog.PRIMARY_BLUE);
        footer.add(countdownLabel, BorderLayout.WEST);
        return footer;
    }

    // =====================================================================
    //  เมธอดแสดงผลข้อมูลที่เรียกโดย MainApp
    // =====================================================================

    /**
     * วาดเนื้อหาทั้งหมดของหน้าจอจากชุดข้อมูล ROUND_RESULT ที่ได้รับจากเซิร์ฟเวอร์
     * ข้อมูลทุกอาเรย์มีความยาวเท่ากัน โดยแต่ละดัชนีแทนผู้เล่น 1 คน
     */
    public void showResult(int round, int totalRounds, boolean isFinal, String[] secret, String reason,
                           String questioner, String[] names, int[] deltas, int[] totals,
                           String[][] guesses, int[] correctCounts, boolean[] bonuses) {
        String questionerName = (questioner == null) ? "" : questioner;
        titleLabel.setText("Round " + round + " of " + totalRounds);
        questionerLabel.setText("Questioner: " + (questionerName.isEmpty() ? "-" : questionerName));
        tagLabel.setText(isFinal ? "FINAL ROUND SUMMARY" : "ROUND SUMMARY");

        // ปรับข้อความและสีของป้ายเหตุผลที่จบรอบ
        if ("time".equals(reason)) {
            AppDialog.paintBadge(reasonBadge, "Time is up", AppDialog.DANGER_BG, AppDialog.DANGER_TEXT,
                    DANGER_LINE, 8, 16);
        } else {
            String badgeText = "all".equals(reason) ? "All guesses in" : "Round complete";
            AppDialog.paintBadge(reasonBadge, badgeText, AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                    SUCCESS_LINE, 8, 16);
        }

        // แสดงรูปทรงเฉลยความลับ
        secretRow.removeAll();
        if (secret != null && secret.length > 0) {
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

        resultsBadge.setText(names.length + (names.length == 1 ? " player" : " players"));

        // สร้างแถวแสดงผลสำหรับผู้เล่นแต่ละคน
        rowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            int delta = deltas[i];
            int total = totals[i];
            String[] guess = guesses[i];
            int correct = correctCounts[i];
            boolean bonus = bonuses[i];
            var row = buildPlayerRow(i, names[i], delta, total, guess, correct, bonus,
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

        // เริ่มเวลานับถอยหลังก่อนเปิดรอบใหม่หรือหน้าตารางคะแนนสุดท้าย
        startCountdown(isFinal);
        revalidate();
        repaint();
    }

    // =====================================================================
    //  การนับถอยหลังระหว่างพักรอบ (Countdown)
    // =====================================================================

    /**
     * เริ่มนับถอยหลังจาก INTERMISSION_SECONDS ลงไปจนถึง 0
     */
    private void startCountdown(boolean isFinal) {
        stopCountdown();
        secondsLeft = COUNTDOWN_SECONDS;
        setCountdownText(isFinal);
        countdown = new Timer(1000, event -> {
            if (event.getSource() != countdown) {
                return;   // Event ค้างจาก Timer ตัวเก่าที่ถูกสั่งหยุดไปแล้ว
            }
            secondsLeft--;
            if (secondsLeft <= 0) {
                secondsLeft = 0;
                setCountdownText(isFinal);
                stopCountdown();
            } else {
                setCountdownText(isFinal);
            }
        });
        countdown.start();
    }

    private void setCountdownText(boolean isFinal) {
        countdownLabel.setText((isFinal ? "Final scoreboard in " : "Next round in ") + secondsLeft + "s...");
    }

    /**
     * สั่งหยุดการนับถอยหลัง
     */
    public void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    // =====================================================================
    //  การสร้างแถวผู้เล่นและคอมโพเนนต์ย่อย (Rows and Small Factories)
    // =====================================================================

    /**
     * สร้างแถวผลงานของผู้เล่นแต่ละคน: แสดง Avatar, คำตอบที่ทาย (สีกรอบเขียวถ้าถูก, แดงถ้าผิด), โบนัส, คะแนนรอบนี้ และคะแนนรวม
     */
    private JPanel buildPlayerRow(int index, String name, int delta, int total, String[] guess, int correct,
                                  boolean bonus, boolean isQuestioner, String[] secret) {
        var row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(AppDialog.ROW_BG);
        row.setBorder(new CompoundBorder(new LineBorder(AppDialog.ROW_BG, 1), new EmptyBorder(10, 14, 10, 14)));

        // ข้อมูลผู้เล่นฝั่งซ้าย (Avatar, ชื่อ, ป้าย Questioner)
        var questionerBadge = isQuestioner
                ? AppDialog.badge("Questioner", AppDialog.CARD_BORDER, AppDialog.TEXT_DARK,
                        AppDialog.CARD_BORDER, 2, 8)
                : null;
        row.add(AppDialog.playerWho(AppDialog.initialOf(name), AppDialog.avatarColor(index), name,
                AVATAR_SIZE, 8, questionerBadge), BorderLayout.WEST);

        // ส่วนแสดงรูปทรงที่ผู้เล่นทายตรงกลาง (พร้อมสีกรอบเขียว/แดง)
        var guessed = AppDialog.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        if (isQuestioner) {
            guessed.add(mutedLabel("picked the secret"));
        } else if (guess.length == 0) {
            guessed.add(mutedLabel("did not guess"));
        } else {
            for (String shapeName : guess) {
                guessed.add(shapeIcon(shapeName, GUESS_ICON,
                        containsShape(secret, shapeName) ? SUCCESS_LINE : DANGER_LINE, 2));
            }
            guessed.add(mutedLabel(correct + " / " + guess.length + " correct"));
        }
        row.add(guessed, BorderLayout.CENTER);

        // ส่วนแสดงคะแนนฝั่งขวา (ป้ายโบนัส, คะแนนที่ได้เพิ่มในรอบนี้, คะแนนรวม)
        var score = AppDialog.panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (bonus) {
            score.add(AppDialog.badge("BONUS +2", AppDialog.GOLD_BG, AppDialog.GOLD_TEXT, AppDialog.GOLD_BORDER, 3, 8));
        }
        if (delta > 0) {
            score.add(AppDialog.badge("+" + delta, AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT, SUCCESS_LINE, 3, 10));
        } else {
            score.add(AppDialog.badge("+0", AppDialog.BG_LIGHT, AppDialog.TEXT_MUTED, AppDialog.CARD_BORDER, 3, 10));
        }

        var totalLabel = AppDialog.label(String.valueOf(total), AppDialog.STRONG,
                total > 0 ? AppDialog.PRIMARY_BLUE : AppDialog.TEXT_MUTED, JLabel.RIGHT);
        totalLabel.setPreferredSize(new Dimension(46, 24));
        score.add(totalLabel);
        row.add(score, BorderLayout.EAST);
        return row;
    }

    /** ตรวจสอบว่ารูปทรงที่ระบุมีอยู่ในชุดเฉลยหรือไม่ */
    private static boolean containsShape(String[] shapes, String wanted) {
        if (shapes == null || wanted == null) {
            return false;
        }
        for (String shape : shapes) {
            if (wanted.equalsIgnoreCase(shape)) {
                return true;
            }
        }
        return false;
    }

    private static JLabel mutedLabel(String text) {
        return AppDialog.label(text, AppDialog.SMALL, AppDialog.TEXT_MUTED);
    }

    /** สร้างรูปภาพรูปทรงพร้อมใส่สีกรอบตามที่กำหนด */
    private static JPanel shapeIcon(String shapeName, int size, Color line, int width) {
        var picture = ShapeImages.shape(shapeName, size);
        picture.setToolTipText(shapeName);
        picture.setBorder(new LineBorder(line, width));
        return picture;
    }
}
