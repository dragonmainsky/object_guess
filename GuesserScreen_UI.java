import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/**
 * GuesserScreen_UI: ส่วนหน้าตาของหน้าจอผู้ทาย
 * เก็บคอมโพเนนต์ ระบบลากวาง และเมธอดสร้าง/วาด UI ทั้งหมด โดยไม่ถือสถานะของเกม
 */
public class GuesserScreen_UI extends JPanel {

    private static final int GHOST_SIZE = 64;        // ขนาดรูปทรงที่ลอยตามเคอร์เซอร์เมาส์ขณะลาก
    private static final int SLOT_ICON_WIDE = 78;    // ขนาดรูปทรงในช่องทายเมื่อรอบนั้นต้องการ 1-3 รูป
    private static final int SLOT_ICON_TIGHT = 54;   // ขนาดรูปทรงในช่องทายเมื่อรอบนั้นต้องการ 4-5 รูป (ย่อให้พอดีกับแถว)
    private static final Color STAGE_BG = new Color(247, 250, 255); // สีพื้นหลังของพื้นที่สเตจตรงกลาง

    protected final ShapeImages.DragBoard drag;                         // ตัวจัดการระบบลากและวาง (DragBoard)
    protected final List<ShapeImages.Picker> pickers = new ArrayList<>(); // รายการช่องเลือกรูปทรง
    protected JPanel guessSlotsPanel;     // พาเนลแสดงช่องทายรูปทรง
    protected JLabel pickCounterLabel;    // ป้ายนับจำนวนที่เลือกบนการ์ด Objects
    protected JButton submitBtn;          // ปุ่มกดยืนยันส่งคำตอบ (SUBMIT GUESS)
    protected JPanel scoreRowsPanel;      // พาเนลแสดงรายการคะแนนสด
    protected JLabel roundLabel;          // ป้ายบอกหมายเลขรอบ
    protected JLabel scoreBadge;          // ป้ายแสดงคะแนนปัจจุบันของผู้เล่น
    protected JLabel submittedBadge;      // ป้ายแสดงสถานะว่าส่งคำตอบแล้วหรือยัง
    protected JLabel statusLabel;         // ป้ายข้อความแนะนำสถานะใต้การ์ด
    protected JLabel targetBadge;         // ป้ายแสดงจำนวนเป้าหมายรูปทรง
    protected JLabel timeBadge;           // ป้ายเวลานับถอยหลัง

    /**
     * คอนสตรักเตอร์สร้างหน้าจอผู้ทาย จัดวางแบบ 3 คอลัมน์ และผูกปุ่ม ENTER สำหรับส่งคำตอบ
     */
    public GuesserScreen_UI() {
        drag = new ShapeImages.DragBoard(this, GHOST_SIZE);
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        JPanel mainContent = AppDialog.panel(new BorderLayout(12, 12));
        mainContent.setBorder(new EmptyBorder(18, 24, 14, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(createColumns(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        // รองรับการกดปุ่ม ENTER บนคีย์บอร์ดเพื่อส่งคำตอบทันที หากหน้าจอนี้แสดงอยู่และไม่มี Dialog บัง
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

    /**
     * วาดรูปทรงโปร่งแสงที่กำลังลากตามตำแหน่งเคอร์เซอร์เมาส์ทับบนหน้าจอ
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drag.paintGhost(g);
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จาก UI (Event hooks)
    // =====================================================================

    /** เรียกเมื่อผู้เล่นกดปุ่ม SUBMIT GUESS */
    protected void onSubmitClicked() {
    }

    /** เรียกเมื่อมีการวางรูปทรงลงในช่องทาย */
    protected void onShapeDroppedToSlot(String shapeName, int slotIndex) {
    }

    /** เรียกเมื่อมีการลากรูปทรงกลับไปยังรายการ Objects */
    protected void onShapeRemoved(String shapeName) {
    }

    /** ตรวจสอบว่าอินพุตถูกล็อกอยู่หรือไม่ */
    protected boolean isInputLocked() {
        return false;
    }

    // =====================================================================
    //  ส่วนหัวและคอลัมน์ (Sub header and Columns)
    // =====================================================================

    /**
     * สร้างแถบส่วนหัวด้านบน: แสดงคำแนะนำการเล่น พร้อมป้ายเป้าหมาย เวลา คะแนน และสถานะส่งคำตอบ
     */
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

    /**
     * รวมคอลัมน์ซ้าย, กลาง, ขวา เข้าด้วยกัน
     */
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
    //  คอลัมน์ซ้าย: สกอร์บอร์ดและกติกาการให้คะแนน (Left Column)
    // =====================================================================

    /** สร้างคอลัมน์ซ้าย ประกอบด้วยการ์ดสกอร์บอร์ดและกติกาการให้คะแนน */
    private JPanel createLeftPanel() {
        scoreRowsPanel = AppDialog.panel(new GridLayout(0, 1, 0, 8));
        roundLabel = AppDialog.label("Waiting to start", AppDialog.SMALL, AppDialog.TEXT_MUTED);
        JPanel scoreHeader = AppDialog.panel(new BorderLayout());
        scoreHeader.add(AppDialog.label("Scoreboard", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.WEST);
        scoreHeader.add(roundLabel, BorderLayout.EAST);

        JPanel scoreCard = AppDialog.card(new BorderLayout(0, 12));
        scoreCard.add(scoreHeader, BorderLayout.NORTH);
        scoreCard.add(scoreRowsPanel, BorderLayout.CENTER);

        // การ์ดแสดงกติกาคะแนน: +1 ถูกต้อง, +2 ถูกหมดใน 10 วิ, 0 ผิด
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

    // สร้างแถวแสดงกติกาคะแนน
    private JPanel createRuleRow(String badgeText, Color bg, Color fg, Color border, String ruleText) {
        JLabel badge = AppDialog.badge(badgeText, bg, fg, border, 2, 6);
        badge.setPreferredSize(new Dimension(34, 22));

        JPanel row = AppDialog.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(badge);
        row.add(AppDialog.label(ruleText, AppDialog.SMALL, AppDialog.TEXT_DARK));
        return row;
    }

    // =====================================================================
    //  คอลัมน์กลาง: สเตจและช่องวางรูปทาย (Center Column)
    // =====================================================================

    /** สร้างคอลัมน์กลาง: การ์ดช่องวางรูปทาย พร้อมแถบคำแนะนำและสถานะ */
    private JPanel createCenterPanel() {
        JPanel centerCard = AppDialog.card(new BorderLayout(0, 12));
        centerCard.add(AppDialog.header("Your guess", AppDialog.TITLE, "Drag shapes here from Objects",
                AppDialog.BODY, null), BorderLayout.NORTH);
        centerCard.add(createStagePanel(), BorderLayout.CENTER);

        // แถบคำแนะนำด้านล่างการ์ดทำหน้าที่เป็นป้ายแสดงสถานะรอบด้วย
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

    /**
     * สเตจกลาง: ช่องวางรูปทาย
     */
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

    /**
     * สร้างช่องทายตามจำนวนเป้าหมายของรอบ
     */
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

    // กำหนดขนาดรูปภาพในช่องทาย ถ้าต้องการเกิน 3 รูป จะย่อขนาดลงเพื่อให้พอดีแถว
    private int slotIconSize(int requiredPicks) {
        return requiredPicks > 3 ? SLOT_ICON_TIGHT : SLOT_ICON_WIDE;
    }

    /**
     * สร้างแต่ละช่องสำหรับใส่รูปทาย: หากมีรูปแล้วจะเป็นการ์ดพร้อมรูปและลากได้ หากว่างจะเป็นกล่องคำว่า "Empty"
     * ทั้งสองแบบรองรับการปล่อยวางรูปทรง (Drop zone)
     */
    private JPanel createSlot(int index, List<String> guessedShapes, int requiredPicks) {
        boolean filled = index < guessedShapes.size();
        JPanel slot = new JPanel(new BorderLayout(0, filled ? 8 : 0));
        if (filled) {
            String name = guessedShapes.get(index);
            int icon = slotIconSize(requiredPicks);
            slot.setBackground(Color.WHITE);
            slot.setBorder(AppDialog.box(AppDialog.PRIMARY_BLUE, 2, requiredPicks > 3 ? 8 : 14, 6));
            slot.add(ShapeImages.shape(name, icon), BorderLayout.CENTER);
            slot.add(AppDialog.label(name, requiredPicks > 3 ? AppDialog.SMALL_BOLD : AppDialog.BODY_BOLD,
                    AppDialog.TEXT_DARK, JLabel.CENTER), BorderLayout.SOUTH);
            drag.draggable(slot, name, true, () -> isInputLocked());   // ลากกลับไปที่ Objects เพื่อลบออก
        } else {
            slot.setBackground(AppDialog.ROW_BG);
            slot.setBorder(AppDialog.box(AppDialog.SLOT_BORDER, 2, 0, 0));
            slot.add(AppDialog.label("Empty", AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER), BorderLayout.CENTER);
        }
        // กำหนดให้ช่องนี้เป็น Drop zone
        drag.dropZone(slot, () -> true, on -> {
            slot.setBackground(on ? AppDialog.BLUE_LIGHT_BG : (filled ? Color.WHITE : AppDialog.ROW_BG));
            if (!filled) {
                slot.setBorder(AppDialog.box(on ? AppDialog.PRIMARY_BLUE : AppDialog.SLOT_BORDER, 2, 0, 0));
            }
            slot.repaint();
        }, (shapeName, where) -> onShapeDroppedToSlot(shapeName, index));
        return slot;
    }

    // =====================================================================
    //  คอลัมน์ขวา: รายการ Objects และปุ่มส่งคำตอบ (Right Column)
    // =====================================================================

    /** สร้างคอลัมน์ขวา: การ์ดรายการ Objects สำหรับลาก และปุ่มส่งคำตอบ */
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

        // ปุ่มกดยืนยันส่งคำตอบ (SUBMIT GUESS)
        submitBtn = AppDialog.button("SUBMIT GUESS", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER,
                AppDialog.PRIMARY_PRESS, Color.WHITE, null, 10, 18);
        submitBtn.setPreferredSize(new Dimension(0, 44));
        submitBtn.addActionListener(event -> onSubmitClicked());
        objectsCard.add(submitBtn, BorderLayout.SOUTH);

        // การ์ด Objects ทั้งหมดทำหน้าที่เป็น Drop Zone สำหรับลากรูปออกจากช่องทายเพื่อลบออก
        drag.dropZone(objectsCard, () -> drag.fromBoard(), on -> {
            objectsCard.setBackground(on ? AppDialog.DANGER_BG : AppDialog.CARD_BG);
            objectsCard.setBorder(AppDialog.box(on ? AppDialog.DANGER_BORDER : AppDialog.CARD_BORDER, 1, 16, 16));
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
    //  การวาดข้อมูลจากสถานะของ GuesserScreen (Render state)
    // =====================================================================

    /** อัปเดตคอมโพเนนต์ทั้งหมดที่ขึ้นอยู่กับจำนวนรูปทรงที่ทาย */
    protected void renderSelection(List<String> guessedShapes, int requiredPicks) {
        renderGuessSlots(guessedShapes, requiredPicks);
        pickCounterLabel.setText(picksCountText(guessedShapes.size(), requiredPicks));
        for (ShapeImages.Picker picker : pickers) {
            picker.setSelected(guessedShapes.contains(picker.shapeName));
        }
    }

    /** สร้างข้อความจำนวนรูปที่เลือก เช่น "2 / 4" */
    private static String picksCountText(int selected, int requiredPicks) {
        return selected + (requiredPicks == 0 ? " / ?" : " / " + requiredPicks);
    }

    /** อัปเดตป้ายจำนวนเป้าหมายรูปทรง */
    protected void renderTarget(int requiredPicks) {
        targetBadge.setText(AppDialog.targetText(requiredPicks));
    }

    /** อัปเดตข้อมูลรอบบนป้ายหมายเลขรอบ */
    protected void renderRoundLabel(int roundNumber, int totalRounds) {
        roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
    }

    /** เปิด/ปิดการใช้งานปุ่มส่งคำตอบ พร้อมเปลี่ยนสี */
    protected void renderSubmitEnabled(boolean enabled) {
        submitBtn.setEnabled(enabled);
        submitBtn.setBackground(enabled ? AppDialog.PRIMARY_BLUE : AppDialog.SLOT_BORDER);
    }

    /** อัปเดตป้ายสถานะการส่งคำตอบ */
    protected void renderSubmitted(boolean submitted) {
        AppDialog.paintTopBadge(submittedBadge, submitted ? "Submitted" : "Not submitted",
                submitted ? AppDialog.SUCCESS_BG : AppDialog.WARNING_BG,
                submitted ? AppDialog.SUCCESS_TEXT : AppDialog.WARNING_TEXT,
                submitted ? AppDialog.SUCCESS_BORDER : AppDialog.WARNING_BORDER, false);
    }

    /** แสดงเวลารอโดยยังไม่เริ่มนับถอยหลัง */
    protected void renderTimeWaiting() {
        AppDialog.showWaitingTime(timeBadge);
    }

    /** รีเฟรชสกอร์บอร์ดคะแนนสดฝั่งซ้าย */
    protected void renderScoreboard(String[] names, int[] scores, String myName) {
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            int score = (i < scores.length) ? scores[i] : 0;
            boolean me = names[i].equals(myName);
            if (me) {
                scoreBadge.setText("Score: " + score);
            }
            scoreRowsPanel.add(AppDialog.scoreRow(names[i], i, score, me, 5, 9, false, 0));
        }
        scoreRowsPanel.revalidate();
        scoreRowsPanel.repaint();
    }

    /** อัปเดตเวลานับถอยหลังตาม TICK ของเซิร์ฟเวอร์ (10 วิสุดท้ายเป็นสีแดง) */
    public void setTimeLeft(int seconds) {
        AppDialog.setTimeLeft(timeBadge, seconds);
    }

    /** อัปเดตข้อความแนะนำสถานะใต้การ์ด */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }
}
