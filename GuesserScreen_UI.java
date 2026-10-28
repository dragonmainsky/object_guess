import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/** ส่วนหน้าตาของหน้าจอผู้ทาย */
public class GuesserScreen_UI extends JPanel {

    private static final int GHOST_SIZE = 64;        // ขนาดรูปทรงที่ลอยตามเคอร์เซอร์เมาส์ขณะลาก
    private static final int SLOT_ICON_WIDE = 78;    // ขนาดรูปทรงในช่องทายเมื่อรอบนั้นต้องการ 1-3 รูป
    private static final int SLOT_ICON_TIGHT = 54;   // ขนาดรูปทรงในช่องทายเมื่อรอบนั้นต้องการ 4-5 รูป (ย่อให้พอดีกับแถว)
    private static final Color STAGE_BG = new Color(247, 250, 255); // สีพื้นหลังของพื้นที่สเตจตรงกลาง

    protected final ShapeImages.DragBoard drag;                         // ตัวจัดการระบบลากและวาง (DragBoard)
    protected final List<ShapeImages.Picker> pickers = new ArrayList<>(); // รายการช่องเลือกรูปทรง
    protected JPanel guessSlotsPanel;     // พาเนลแสดงช่องทายรูปทรง
    protected JLabel pickCounterLabel;    // ป้ายนับจำนวนที่เลือกบนการ์ด Objects
    protected JButton submitBtn;          // ปุ่ม SUBMIT GUESS
    protected JPanel scoreRowsPanel;      // พาเนลแสดงรายการคะแนนสด
    protected JLabel roundLabel;          // ป้ายบอกหมายเลขรอบ
    protected JLabel scoreBadge;          // ป้ายแสดงคะแนนปัจจุบันของผู้เล่น
    protected JLabel submittedBadge;      // ป้ายแสดงสถานะว่าส่งคำตอบแล้วหรือยัง
    protected JLabel statusLabel;         // ป้ายข้อความแนะนำสถานะใต้การ์ด
    protected JLabel targetBadge;         // ป้ายแสดงจำนวนเป้าหมายรูปทรง
    protected JLabel timeBadge;           // ป้ายเวลานับถอยหลัง

    /** สร้างหน้าจอผู้ทาย */
    public GuesserScreen_UI() {
        drag = new ShapeImages.DragBoard(this, GHOST_SIZE);
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        JPanel mainContent = AppDialog.panel(new BorderLayout(12, 12));
        mainContent.setBorder(new EmptyBorder(18, 24, 14, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(createColumns(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        // รองรับปุ่ม ENTER
        KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        manager.addKeyEventDispatcher(event -> {
            boolean submitNow = isShowing() && event.getID() == KeyEvent.KEY_PRESSED
                    && event.getKeyCode() == KeyEvent.VK_ENTER
                    && !(manager.getActiveWindow() instanceof JDialog);
            if (submitNow) {
                submitBtn.doClick();
            }
            return false;   // ไม่ระงับ Event เพื่อให้ปุ่มอื่นๆ ทำงานได้ตามปกติ
        });
    }

    /** วาดรูปทรงที่ลอยตามเมาส์ */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drag.paintGhost(g);
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จาก UI (Event hooks)
    // =====================================================================

    /** ปุ่ม SUBMIT GUESS */
    protected void onSubmitClicked() {
    }

    /** วางรูปทรงในช่องทาย */
    protected void onShapeDroppedToSlot(String shapeName, int slotIndex) {
    }

    /** ลากรูปทรงกลับไปยัง Objects */
    protected void onShapeRemoved(String shapeName) {
    }

    /** สถานะล็อกอินพุต */
    protected boolean isInputLocked() {
        return false;
    }

    // =====================================================================
    //  ส่วนหัวและคอลัมน์ (Sub header and Columns)
    // =====================================================================

    /** แถบหัวข้อด้านบน */
    private JPanel createSubHeader() {
        JPanel titles = AppDialog.titleStack(2,
                AppDialog.label("Guess what the Questioner selected!", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK),
                AppDialog.label("Choose the same shapes before time runs out.", AppDialog.SCREEN_SUB, AppDialog.TEXT_MUTED));

        targetBadge = AppDialog.topBadge("Target: ?", AppDialog.BLUE_LIGHT_BG, AppDialog.PRIMARY_BLUE,
                AppDialog.BLUE_BORDER, false);
        timeBadge = AppDialog.topBadge("Time: --:--", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, true);
        scoreBadge = AppDialog.topBadge("Score: 0", AppDialog.BG_LIGHT, AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, false);
        submittedBadge = AppDialog.topBadge("Not submitted", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, false);
        JPanel badges = AppDialog.group(FlowLayout.RIGHT, 12, 0,
                targetBadge, timeBadge, scoreBadge, submittedBadge);
        return AppDialog.pageHeader(titles, badges, BorderLayout.CENTER, 4, 0, 6, 0);
    }

    /** รวมสามคอลัมน์ */
    private JPanel createColumns() {
        JPanel left = createLeftPanel();
        left.setPreferredSize(new Dimension(240, 0));
        JPanel right = createRightPanel();
        right.setPreferredSize(new Dimension(300, 0));

        JPanel columns = AppDialog.panel(new BorderLayout(14, 0));
        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);
        return columns;
    }

    // =====================================================================
    // คอลัมน์ซ้าย: สกอร์บอร์ดและกติกา
    // =====================================================================

    /** คอลัมน์ซ้าย */
    private JPanel createLeftPanel() {
        scoreRowsPanel = AppDialog.panel(new GridLayout(0, 1, 0, 8));
        roundLabel = AppDialog.label("Waiting to start", AppDialog.SMALL, AppDialog.TEXT_MUTED);
        JPanel scoreHeader = AppDialog.panel(new BorderLayout());
        scoreHeader.add(AppDialog.label("Scoreboard", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.WEST);
        scoreHeader.add(roundLabel, BorderLayout.EAST);

        JPanel scoreCard = AppDialog.card(new BorderLayout(0, 12));
        scoreCard.add(scoreHeader, BorderLayout.NORTH);
        scoreCard.add(scoreRowsPanel, BorderLayout.CENTER);

        // กติกาคะแนน
        JPanel ruleRows = AppDialog.panel(new GridLayout(0, 1, 0, 8));
        ruleRows.add(createRuleRow("+1", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT, AppDialog.SUCCESS_BORDER,
                "for each correct shape"));
        ruleRows.add(createRuleRow("+2", AppDialog.GOLD_BG, AppDialog.GOLD_TEXT, AppDialog.GOLD_BORDER,
                "all correct under 10 sec"));
        ruleRows.add(createRuleRow("0", AppDialog.DANGER_BG, AppDialog.DANGER_TEXT, AppDialog.DANGER_BORDER,
                "for an incorrect shape"));
        JPanel guideCard = AppDialog.card(new BorderLayout(0, 12));
        guideCard.add(AppDialog.header("Scoring", AppDialog.SUBTITLE, null, null, null), BorderLayout.NORTH);
        guideCard.add(ruleRows, BorderLayout.CENTER);

        JPanel left = AppDialog.panel(new BorderLayout(0, 12));
        left.add(scoreCard, BorderLayout.NORTH);
        left.add(AppDialog.pin(guideCard, BorderLayout.NORTH), BorderLayout.CENTER);
        return left;
    }

    // แถวกติกาคะแนน
    private JPanel createRuleRow(String badgeText, Color bg, Color fg, Color border, String ruleText) {
        JLabel badge = AppDialog.badge(badgeText, bg, fg, border, 2, 6);
        badge.setPreferredSize(new Dimension(34, 22));

        JPanel row = AppDialog.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(badge);
        row.add(AppDialog.label(ruleText, AppDialog.SMALL, AppDialog.TEXT_DARK));
        return row;
    }

    // =====================================================================
    // คอลัมน์กลาง: ช่องทาย
    // =====================================================================

    /** คอลัมน์กลาง */
    private JPanel createCenterPanel() {
        JPanel centerCard = AppDialog.card(new BorderLayout(0, 12));
        centerCard.add(AppDialog.header("Your guess", AppDialog.TITLE, "Drag shapes here from Objects",
                AppDialog.BODY, null), BorderLayout.NORTH);
        centerCard.add(createStagePanel(), BorderLayout.CENTER);

        // แถบสถานะใต้การ์ด
        JPanel hintRow = AppDialog.panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hintRow.add(AppDialog.label("TIP", AppDialog.SMALL_BOLD, AppDialog.WARNING_TEXT));
        statusLabel = AppDialog.label("Waiting for the questioner to lock in the secret shapes...",
                AppDialog.SMALL, AppDialog.TEXT_MUTED);
        hintRow.add(statusLabel);

        JPanel center = AppDialog.panel(new BorderLayout(0, 10));
        center.add(centerCard, BorderLayout.CENTER);
        center.add(hintRow, BorderLayout.SOUTH);
        return center;
    }

    /** สเตจกลาง: ช่องทาย */
    private JPanel createStagePanel() {
        guessSlotsPanel = AppDialog.panel();
        JPanel middleRow = AppDialog.panel(new BorderLayout());
        middleRow.setBorder(new EmptyBorder(6, 24, 6, 24));
        middleRow.add(guessSlotsPanel, BorderLayout.CENTER);

        JPanel stage = new JPanel(new BorderLayout());
        stage.setBackground(STAGE_BG);
        stage.setBorder(AppDialog.box(AppDialog.CARD_BORDER, 1, 14, 14));
        stage.add(middleRow, BorderLayout.CENTER);
        return stage;
    }

    /** สร้างช่องทายตามจำนวนเป้าหมาย */
    protected void renderGuessSlots(List<String> guessedShapes, int requiredPicks) {
        guessSlotsPanel.removeAll();
        guessSlotsPanel.setLayout(new GridLayout(1, Math.max(1, requiredPicks), 14, 0));
        if (requiredPicks == 0) {
            guessSlotsPanel.add(AppDialog.label("Waiting for the questioner to lock in the shapes...",
                    AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER));
        } else {
            for (int i = 0; i < requiredPicks; i++) {
                guessSlotsPanel.add(createSlot(i, guessedShapes, requiredPicks));
            }
        }
        guessSlotsPanel.revalidate();
        guessSlotsPanel.repaint();
    }

    // ขนาดรูปในช่องทาย
    private int slotIconSize(int requiredPicks) {
        if (requiredPicks > 3) {
            return SLOT_ICON_TIGHT;
        }
        return SLOT_ICON_WIDE;
    }

    /** สร้างช่องทายหนึ่งช่อง */
    private JPanel createSlot(int index, List<String> guessedShapes, int requiredPicks) {
        boolean filled = index < guessedShapes.size();
        int verticalGap;
        if (filled) {
            verticalGap = 8;
        } else {
            verticalGap = 0;
        }
        JPanel slot = new JPanel(new BorderLayout(0, verticalGap));
        if (filled) {
            String name = guessedShapes.get(index);
            int icon = slotIconSize(requiredPicks);
            slot.setBackground(Color.WHITE);
            int slotPadding;
            if (requiredPicks > 3) {
                slotPadding = 8;
            } else {
                slotPadding = 14;
            }
            slot.setBorder(AppDialog.box(AppDialog.PRIMARY_BLUE, 2, slotPadding, 6));
            slot.add(ShapeImages.shape(name, icon), BorderLayout.CENTER);
            Font nameFont;
            if (requiredPicks > 3) {
                nameFont = AppDialog.SMALL_BOLD;
            } else {
                nameFont = AppDialog.BODY_BOLD;
            }
            slot.add(AppDialog.label(name, nameFont, AppDialog.TEXT_DARK, JLabel.CENTER),
                    BorderLayout.SOUTH);
            drag.draggable(slot, name, true, () -> isInputLocked());   // ลากกลับไปที่ Objects เพื่อลบออก
        } else {
            slot.setBackground(AppDialog.ROW_BG);
            slot.setBorder(AppDialog.box(AppDialog.SLOT_BORDER, 2, 0, 0));
            slot.add(AppDialog.label("Empty", AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER), BorderLayout.CENTER);
        }
        // Drop zone
        drag.dropZone(slot, () -> true, on -> {
            if (on) {
                slot.setBackground(AppDialog.BLUE_LIGHT_BG);
            } else if (filled) {
                slot.setBackground(Color.WHITE);
            } else {
                slot.setBackground(AppDialog.ROW_BG);
            }
            if (!filled) {
                if (on) {
                    slot.setBorder(AppDialog.box(AppDialog.PRIMARY_BLUE, 2, 0, 0));
                } else {
                    slot.setBorder(AppDialog.box(AppDialog.SLOT_BORDER, 2, 0, 0));
                }
            }
            slot.repaint();
        }, (shapeName, where) -> onShapeDroppedToSlot(shapeName, index));
        return slot;
    }

    // =====================================================================
    // คอลัมน์ขวา: Objects และปุ่มส่ง
    // =====================================================================

    /** คอลัมน์ขวา */
    private JPanel createRightPanel() {
        pickCounterLabel = AppDialog.label(picksCountText(0, 0), AppDialog.BODY_BOLD, AppDialog.PRIMARY_BLUE);
        JPanel objectsCard = AppDialog.card(new BorderLayout(0, 12));
        objectsCard.add(AppDialog.header("Objects", AppDialog.SUBTITLE, "Drag your shapes into the guess",
                AppDialog.BODY, pickCounterLabel), BorderLayout.NORTH);

        JPanel grid = AppDialog.panel(new GridLayout(4, 2, 8, 8));
        grid.setPreferredSize(new Dimension(0, 316));   // ล็อกความสูงคงที่เพื่อให้ขนาดการ์ดไม่เปลี่ยน
        for (String name : ShapeImages.NAMES) {
            ShapeImages.Picker picker = new ShapeImages.Picker(name, 34, 8, 6, false);
            pickers.add(picker);
            drag.draggable(picker, name, false, () -> isInputLocked());
            grid.add(picker);
        }
        objectsCard.add(grid, BorderLayout.CENTER);

        // ปุ่ม SUBMIT GUESS
        submitBtn = AppDialog.button("SUBMIT GUESS", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER,
                AppDialog.PRIMARY_PRESS, Color.WHITE, null, 10, 18);
        submitBtn.setPreferredSize(new Dimension(0, 44));
        submitBtn.addActionListener(event -> onSubmitClicked());
        objectsCard.add(submitBtn, BorderLayout.SOUTH);

        // การ์ด Objects เป็นพื้นที่ลบรูปทรง
        drag.dropZone(objectsCard, () -> drag.fromBoard(), on -> {
            if (on) {
                objectsCard.setBackground(AppDialog.DANGER_BG);
                objectsCard.setBorder(AppDialog.box(AppDialog.DANGER_BORDER, 1, 16, 16));
            } else {
                objectsCard.setBackground(AppDialog.CARD_BG);
                objectsCard.setBorder(AppDialog.box(AppDialog.CARD_BORDER, 1, 16, 16));
            }
        }, (shapeName, where) -> onShapeRemoved(shapeName));

        JPanel hintRow = AppDialog.panel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        hintRow.add(AppDialog.keyCap("ENTER"));
        hintRow.add(AppDialog.label("to submit your guess", AppDialog.SMALL, AppDialog.TEXT_MUTED));

        JPanel right = AppDialog.panel(new BorderLayout(0, 12));
        right.add(objectsCard, BorderLayout.NORTH);
        right.add(AppDialog.pin(hintRow, BorderLayout.NORTH), BorderLayout.CENTER);
        return right;
    }

    // =====================================================================
    // การวาดข้อมูลจากสถานะ
    // =====================================================================

    /** อัปเดต UI ตามจำนวนรูปที่ทาย */
    protected void renderSelection(List<String> guessedShapes, int requiredPicks) {
        renderGuessSlots(guessedShapes, requiredPicks);
        pickCounterLabel.setText(picksCountText(guessedShapes.size(), requiredPicks));
        for (ShapeImages.Picker picker : pickers) {
            picker.setSelected(guessedShapes.contains(picker.shapeName));
        }
    }

    /** ข้อความจำนวนรูปที่เลือก */
    private static String picksCountText(int selected, int requiredPicks) {
        if (requiredPicks == 0) {
            return selected + " / ?";
        }
        return selected + " / " + requiredPicks;
    }

    /** ป้ายจำนวนเป้าหมาย */
    protected void renderTarget(int requiredPicks) {
        targetBadge.setText(AppDialog.targetText(requiredPicks));
    }

    /** ป้ายหมายเลขรอบ */
    protected void renderRoundLabel(int roundNumber, int totalRounds) {
        roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
    }

    /** ปุ่มส่งคำตอบ */
    protected void renderSubmitEnabled(boolean enabled) {
        submitBtn.setEnabled(enabled);
        if (enabled) {
            submitBtn.setBackground(AppDialog.PRIMARY_BLUE);
        } else {
            submitBtn.setBackground(AppDialog.SLOT_BORDER);
        }
    }

    /** ป้ายสถานะการส่ง */
    protected void renderSubmitted(boolean submitted) {
        String badgeText;
        Color badgeBackground;
        Color badgeForeground;
        Color badgeBorder;
        if (submitted) {
            badgeText = "Submitted";
            badgeBackground = AppDialog.SUCCESS_BG;
            badgeForeground = AppDialog.SUCCESS_TEXT;
            badgeBorder = AppDialog.SUCCESS_BORDER;
        } else {
            badgeText = "Not submitted";
            badgeBackground = AppDialog.WARNING_BG;
            badgeForeground = AppDialog.WARNING_TEXT;
            badgeBorder = AppDialog.WARNING_BORDER;
        }
        AppDialog.paintTopBadge(submittedBadge, badgeText, badgeBackground, badgeForeground,
                badgeBorder, false);
    }

    /** เวลารอก่อนเริ่ม */
    protected void renderTimeWaiting() {
        AppDialog.showWaitingTime(timeBadge);
    }

    /** สกอร์บอร์ดฝั่งซ้าย */
    protected void renderScoreboard(String[] names, int[] scores, String myName) {
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            int score;
            if (i < scores.length) {
                score = scores[i];
            } else {
                score = 0;
            }
            boolean me = names[i].equals(myName);
            if (me) {
                scoreBadge.setText("Score: " + score);
            }
            scoreRowsPanel.add(AppDialog.scoreRow(names[i], i, score, me, 5, 9, false, 0));
        }
        scoreRowsPanel.revalidate();
        scoreRowsPanel.repaint();
    }

    /** เวลานับถอยหลัง */
    public void setTimeLeft(int seconds) {
        AppDialog.setTimeLeft(timeBadge, seconds);
    }

    /** ข้อความสถานะใต้การ์ด */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }
}
