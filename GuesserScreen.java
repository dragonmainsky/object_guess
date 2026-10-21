import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/**
 * GuesserScreen: หน้าจอของผู้ทาย (หน้าจอที่ 4)
 * ผู้เล่นที่ทำหน้าที่ทายจะต้องเลือกรูปทรงให้ตรงกับที่ Questioner เลือกไว้
 * สามารถลากรูปทรงจากรายการ Objects มาวางในช่องทาย (Guess slots),
 * สลับตำแหน่งระหว่างช่อง หรือลากกลับไปยัง Objects เพื่อลบออกได้
 */
public class GuesserScreen extends JPanel {

    private static final int GHOST_SIZE = 64;        // ขนาดรูปทรงที่ลอยตามเคอร์เซอร์เมาส์ขณะลาก
    private static final int SLOT_ICON_WIDE = 78;    // ขนาดรูปทรงในช่องทายเมื่อรอบนั้นต้องการ 1-3 รูป
    private static final int SLOT_ICON_TIGHT = 54;   // ขนาดรูปทรงในช่องทายเมื่อรอบนั้นต้องการ 4-5 รูป (ย่อให้พอดีกับแถว)
    private static final Color STAGE_BG = new Color(247, 250, 255); // สีพื้นหลังของพื้นที่สเตจตรงกลาง

    // ===== ข้อมูลสถานะการทาย (State) =====
    private final List<String> guessedShapes = new ArrayList<>();   // รายการรูปทรงที่ผู้เล่นทาย (เริ่มใหม่ทุกรอบ)
    private final List<ShapeImages.Picker> pickers = new ArrayList<>(); // รายการช่องเลือกรูปทรง
    private final ShapeImages.DragBoard drag;                           // ตัวจัดการระบบลากและวาง (DragBoard)
    private final ActionListener onSubmitAction;                       // ตัวรับเหตุการณ์เมื่อกดปุ่มส่งคำตอบทาย

    private JPanel guessSlotsPanel;     // พาเนลแสดงช่องทายรูปทรง
    private JLabel selectedBadge;       // ป้ายแสดงจำนวนรูปที่เลือก
    private JLabel pickCounterLabel;    // ป้ายนับจำนวนที่เลือกบนการ์ด Objects
    private JButton submitBtn;          // ปุ่มกดยืนยันส่งคำตอบ (SUBMIT GUESS)
    private int requiredPicks;          // จำนวนรูปทรงที่ต้องทายในรอบนี้ (กำหนดโดย SECRET_LOCKED)
    private JPanel scoreRowsPanel;      // พาเนลแสดงรายการคะแนนสด
    private JLabel roundLabel;          // ป้ายบอกหมายเลขรอบ
    private JLabel scoreBadge;          // ป้ายแสดงคะแนนปัจจุบันของผู้เล่น
    private JLabel submittedBadge;      // ป้ายแสดงสถานะว่าส่งคำตอบแล้วหรือยัง
    private JLabel statusLabel;         // ป้ายข้อความแนะนำสถานะใต้การ์ด
    private JLabel targetBadge;         // ป้ายแสดงจำนวนเป้าหมายรูปทรง
    private JLabel timeBadge;           // ป้ายเวลานับถอยหลัง
    private boolean submitted;          // สถานะว่าส่งคำตอบในรอบนี้ไปแล้วหรือไม่
    private boolean secretLocked;       // สถานะว่า Questioner ได้ล็อกรูปทรงแล้วหรือไม่
    private String myName = "";         // ชื่อของผู้เล่นเครื่องนี้
    private String questionerName = ""; // ชื่อของ Questioner ในรอบนี้

    /**
     * คอนสตรักเตอร์สร้างหน้าจอผู้ทาย จัดวางแบบ 3 คอลัมน์ และผูกปุ่ม ENTER สำหรับส่งคำตอบ
     */
    public GuesserScreen(ActionListener onSubmitAction) {
        this.onSubmitAction = onSubmitAction;
        drag = new ShapeImages.DragBoard(this, GHOST_SIZE);
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        var mainContent = AppDialog.panel();
        mainContent.setLayout(new BorderLayout(12, 12));
        mainContent.setBorder(new EmptyBorder(18, 24, 14, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(createColumns(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        // รองรับการกดปุ่ม ENTER บนคีย์บอร์ดเพื่อส่งคำตอบทันที หากหน้าจอนี้แสดงอยู่และไม่มี Dialog บัง
        var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
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
    //  ส่วนหัวและคอลัมน์ (Sub header and Columns)
    // =====================================================================

    /**
     * สร้างแถบส่วนหัวด้านบน: แสดงคำแนะนำการเล่น พร้อมป้ายเป้าหมาย เวลา คะแนน และสถานะส่งคำตอบ
     */
    private JPanel createSubHeader() {
        var titles = AppDialog.panel();
        titles.setLayout(new GridLayout(2, 1, 0, 2));
        titles.add(AppDialog.label("Guess what the Questioner selected!", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK));
        titles.add(AppDialog.label("Choose the same shapes before time runs out.", AppDialog.SCREEN_SUB, AppDialog.TEXT_MUTED));

        targetBadge = AppDialog.topBadge("Target: ?", AppDialog.BLUE_LIGHT_BG, AppDialog.PRIMARY_BLUE,
                AppDialog.BLUE_BORDER, false);
        timeBadge = AppDialog.topBadge("Time: --:--", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, true);
        scoreBadge = AppDialog.topBadge("Score: 0", AppDialog.BG_LIGHT, AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, false);
        submittedBadge = AppDialog.topBadge("Not submitted", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, false);
        var badges = AppDialog.panel();
        badges.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        badges.add(targetBadge);
        badges.add(timeBadge);
        badges.add(scoreBadge);
        badges.add(submittedBadge);

        var subHeader = AppDialog.panel();
        subHeader.setLayout(new BorderLayout());
        subHeader.setBorder(new EmptyBorder(4, 0, 6, 0));
        subHeader.add(titles, BorderLayout.WEST);
        // CENTER ช่วยให้แถบป้ายสามารถตัดขึ้นบรรทัดใหม่ได้เมื่อหน้าต่างแคบ
        subHeader.add(badges, BorderLayout.CENTER);
        return subHeader;
    }

    /**
     * รวมคอลัมน์ซ้าย, กลาง, ขวา เข้าด้วยกัน
     */
    private JPanel createColumns() {
        var left = createLeftPanel();
        left.setPreferredSize(new Dimension(240, 0));
        var right = createRightPanel();
        right.setPreferredSize(new Dimension(300, 0));

        var columns = AppDialog.panel();
        columns.setLayout(new BorderLayout(14, 0));
        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);
        return columns;
    }

    // =====================================================================
    //  คอลัมน์ซ้าย: สกอร์บอร์ดและกติกาการให้คะแนน (Left Column)
    // =====================================================================

    private JPanel createLeftPanel() {
        scoreRowsPanel = AppDialog.panel();
        scoreRowsPanel.setLayout(new GridLayout(0, 1, 0, 8));
        roundLabel = AppDialog.label("Waiting to start", AppDialog.SMALL, AppDialog.TEXT_MUTED);
        var scoreHeader = AppDialog.panel();
        scoreHeader.setLayout(new BorderLayout());
        scoreHeader.add(AppDialog.label("Scoreboard", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.WEST);
        scoreHeader.add(roundLabel, BorderLayout.EAST);

        var scoreCard = AppDialog.card();
        scoreCard.setLayout(new BorderLayout(0, 12));
        scoreCard.add(scoreHeader, BorderLayout.NORTH);
        scoreCard.add(scoreRowsPanel, BorderLayout.CENTER);

        // การ์ดแสดงกติกาคะแนน: +1 ถูกต้อง, +2 ถูกหมดใน 10 วิ, 0 ผิด
        var ruleRows = AppDialog.panel();
        ruleRows.setLayout(new GridLayout(0, 1, 0, 8));
        ruleRows.add(createRuleRow("+1", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT, AppDialog.SUCCESS_BORDER,
                "for each correct shape"));
        ruleRows.add(createRuleRow("+2", AppDialog.GOLD_BG, AppDialog.GOLD_TEXT, AppDialog.GOLD_BORDER,
                "all correct under 10 sec"));
        ruleRows.add(createRuleRow("0", AppDialog.DANGER_BG, AppDialog.DANGER_TEXT, AppDialog.DANGER_BORDER,
                "for an incorrect shape"));
        var guideCard = AppDialog.card();
        guideCard.setLayout(new BorderLayout(0, 12));
        guideCard.add(AppDialog.header("Scoring", AppDialog.SUBTITLE, null, null, null), BorderLayout.NORTH);
        guideCard.add(ruleRows, BorderLayout.CENTER);

        var left = AppDialog.panel();
        left.setLayout(new BorderLayout(0, 12));
        left.add(scoreCard, BorderLayout.NORTH);
        left.add(AppDialog.pin(guideCard, BorderLayout.NORTH), BorderLayout.CENTER);
        return left;
    }

    // สร้างแถวคะแนนของผู้เล่นแต่ละคน
    private JPanel createScoreRow(String initial, Color avatarColor, String name, int score, boolean highlight) {
        var row = AppDialog.panel();
        row.setLayout(new BorderLayout());
        row.setOpaque(highlight);
        row.setBackground(AppDialog.BLUE_LIGHT_BG);
        row.setBorder(highlight ? AppDialog.box(AppDialog.BLUE_BORDER, 1, 4, 8) : new EmptyBorder(5, 9, 5, 9));

        var who = AppDialog.panel();
        who.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.add(AppDialog.avatar(initial, avatarColor, 24));
        who.add(AppDialog.label(name, AppDialog.BODY_BOLD, AppDialog.TEXT_DARK));

        row.add(who, BorderLayout.WEST);
        row.add(AppDialog.label(String.valueOf(score), AppDialog.BODY_BOLD, AppDialog.TEXT_DARK, JLabel.RIGHT),
                BorderLayout.EAST);
        return row;
    }

    // สร้างแถวแสดงกติกาคะแนน
    private JPanel createRuleRow(String badgeText, Color bg, Color fg, Color border, String ruleText) {
        var badge = AppDialog.badge(badgeText, bg, fg, border, 2, 6);
        badge.setPreferredSize(new Dimension(34, 22));

        var row = AppDialog.panel();
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(badge);
        row.add(AppDialog.label(ruleText, AppDialog.SMALL, AppDialog.TEXT_DARK));
        return row;
    }

    // =====================================================================
    //  คอลัมน์กลาง: สเตจและช่องวางรูปทาย (Center Column)
    // =====================================================================

    private JPanel createCenterPanel() {
        selectedBadge = AppDialog.badge(picksText(), AppDialog.BLUE_LIGHT_BG, AppDialog.PRIMARY_BLUE,
                AppDialog.BLUE_BORDER, 4, 10);
        var centerCard = AppDialog.card();
        centerCard.setLayout(new BorderLayout(0, 12));
        centerCard.add(AppDialog.header("Your guess", AppDialog.TITLE, "Drag shapes here from Objects",
                AppDialog.BODY, selectedBadge), BorderLayout.NORTH);
        centerCard.add(createStagePanel(), BorderLayout.CENTER);

        // แถบคำแนะนำด้านล่างการ์ดทำหน้าที่เป็นป้ายแสดงสถานะรอบด้วย
        var hintRow = AppDialog.panel();
        hintRow.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hintRow.add(AppDialog.label("TIP", AppDialog.SMALL_BOLD, AppDialog.WARNING_TEXT));
        statusLabel = AppDialog.label("Waiting for the questioner to lock in the secret shapes...",
                AppDialog.SMALL, AppDialog.TEXT_MUTED);
        hintRow.add(statusLabel);

        var center = AppDialog.panel();
        center.setLayout(new BorderLayout(0, 10));
        center.add(centerCard, BorderLayout.CENTER);
        center.add(hintRow, BorderLayout.SOUTH);
        return center;
    }

    /**
     * สเตจ 3 ชั้น: แบนเนอร์วิธีเล่น, แถวช่องวางรูปทาย, แบนเนอร์กติกาคะแนน
     */
    private JPanel createStagePanel() {
        guessSlotsPanel = AppDialog.panel();
        updateGuessSlots();
        var middleRow = AppDialog.panel();
        middleRow.setLayout(new BorderLayout());
        middleRow.setBorder(new EmptyBorder(6, 24, 6, 24));
        middleRow.add(guessSlotsPanel, BorderLayout.CENTER);

        var stage = new JPanel(new GridLayout(3, 1));
        stage.setBackground(STAGE_BG);
        stage.setBorder(AppDialog.box(AppDialog.CARD_BORDER, 1, 14, 14));
        stage.add(AppDialog.pin(createAlertBanner("How to play", "Pick the same shapes the questioner chose.",
                AppDialog.BLUE_LIGHT_BG, AppDialog.BLUE_BORDER, AppDialog.PRIMARY_BLUE, FlowLayout.RIGHT),
                BorderLayout.NORTH));
        stage.add(middleRow);
        stage.add(AppDialog.pin(createAlertBanner("Scoring",
                "+1 per correct shape, +2 if every shape is right within 10 seconds.",
                AppDialog.SUCCESS_BG, AppDialog.SUCCESS_BORDER, AppDialog.SUCCESS_TEXT, FlowLayout.LEFT),
                BorderLayout.SOUTH));
        return stage;
    }

    /** แบนเนอร์แสดงข้อความเตือน/แนะนำ */
    private static JPanel createAlertBanner(String title, String desc, Color bg, Color border, Color fg, int align) {
        var banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        banner.setBackground(bg);
        banner.setBorder(AppDialog.box(border, 1, 6, 8));
        banner.add(AppDialog.label(title, AppDialog.BODY_BOLD, fg));
        banner.add(AppDialog.label(desc, AppDialog.SMALL, fg));

        var slot = AppDialog.panel();
        slot.setLayout(new FlowLayout(align, 0, 0));
        slot.add(banner);
        return slot;
    }

    /**
     * สร้างช่องทายตามจำนวนเป้าหมายของรอบ
     */
    private void updateGuessSlots() {
        guessSlotsPanel.removeAll();
        guessSlotsPanel.setLayout(new GridLayout(1, Math.max(1, requiredPicks), 14, 0));
        if (requiredPicks == 0) {
            guessSlotsPanel.add(AppDialog.label("Waiting for the questioner to lock in the shapes...",
                    AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER));
        } else {
            for (int i = 0; i < requiredPicks; i++) {
                guessSlotsPanel.add(createSlot(i));
            }
        }
        guessSlotsPanel.revalidate();
        guessSlotsPanel.repaint();
    }

    // กำหนดขนาดรูปภาพในช่องทาย ถ้าต้องการเกิน 3 รูป จะย่อขนาดลงเพื่อให้พอดีแถว
    private int slotIconSize() {
        return requiredPicks > 3 ? SLOT_ICON_TIGHT : SLOT_ICON_WIDE;
    }

    /**
     * สร้างแต่ละช่องสำหรับใส่รูปทาย: หากมีรูปแล้วจะเป็นการ์ดพร้อมรูปและลากได้ หากว่างจะเป็นกล่องคำว่า "Empty"
     * ทั้งสองแบบรองรับการปล่อยวางรูปทรง (Drop zone)
     */
    private JPanel createSlot(int index) {
        boolean filled = index < guessedShapes.size();
        var slot = new JPanel(new BorderLayout(0, filled ? 8 : 0));
        if (filled) {
            String name = guessedShapes.get(index);
            int icon = slotIconSize();
            slot.setBackground(Color.WHITE);
            slot.setBorder(AppDialog.box(AppDialog.PRIMARY_BLUE, 2, requiredPicks > 3 ? 8 : 14, 6));
            slot.add(ShapeImages.shape(name, icon), BorderLayout.CENTER);
            slot.add(AppDialog.label(name, requiredPicks > 3 ? AppDialog.SMALL_BOLD : AppDialog.BODY_BOLD,
                    AppDialog.TEXT_DARK, JLabel.CENTER), BorderLayout.SOUTH);
            drag.draggable(slot, name, true, () -> submitted);   // ลากกลับไปที่ Objects เพื่อลบออก
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
        }, (shapeName, where) -> placeShape(shapeName, index));
        return slot;
    }

    // =====================================================================
    //  คอลัมน์ขวา: รายการ Objects และปุ่มส่งคำตอบ (Right Column)
    // =====================================================================

    private JPanel createRightPanel() {
        pickCounterLabel = AppDialog.label(picksCountText(), AppDialog.BODY_BOLD, AppDialog.PRIMARY_BLUE);
        var objectsCard = AppDialog.card();
        objectsCard.setLayout(new BorderLayout(0, 12));
        objectsCard.add(AppDialog.header("Objects", AppDialog.SUBTITLE, "Drag your shapes into the guess",
                AppDialog.BODY, pickCounterLabel), BorderLayout.NORTH);

        var grid = AppDialog.panel();
        grid.setLayout(new GridLayout(4, 2, 8, 8));
        grid.setPreferredSize(new Dimension(0, 316));   // ล็อกความสูงคงที่เพื่อให้ขนาดการ์ดไม่เปลี่ยน
        for (String name : ShapeImages.NAMES) {
            var picker = new ShapeImages.Picker(name, 34, 8, 6, false);
            pickers.add(picker);
            drag.draggable(picker, name, false, () -> submitted);
            grid.add(picker);
        }
        objectsCard.add(grid, BorderLayout.CENTER);

        // ปุ่มกดยืนยันส่งคำตอบ (SUBMIT GUESS)
        submitBtn = AppDialog.button("SUBMIT GUESS", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER,
                AppDialog.PRIMARY_PRESS, Color.WHITE, null, 10, 18);
        submitBtn.setPreferredSize(new Dimension(0, 44));
        submitBtn.addActionListener(event -> submitGuess());
        objectsCard.add(submitBtn, BorderLayout.SOUTH);

        // การ์ด Objects ทั้งหมดทำหน้าที่เป็น Drop Zone สำหรับลากรูปออกจากช่องทายเพื่อลบออก
        drag.dropZone(objectsCard, () -> drag.fromBoard(), on -> {
            objectsCard.setBackground(on ? AppDialog.DANGER_BG : AppDialog.CARD_BG);
            objectsCard.setBorder(AppDialog.box(on ? AppDialog.DANGER_BORDER : AppDialog.CARD_BORDER, 1, 16, 16));
        }, (shapeName, where) -> removeShape(shapeName));

        var hintRow = AppDialog.panel();
        hintRow.setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
        hintRow.add(AppDialog.keyCap("ENTER"));
        hintRow.add(AppDialog.label("to submit your guess", AppDialog.SMALL, AppDialog.TEXT_MUTED));

        var right = AppDialog.panel();
        right.setLayout(new BorderLayout(0, 12));
        right.add(objectsCard, BorderLayout.NORTH);
        right.add(AppDialog.pin(hintRow, BorderLayout.NORTH), BorderLayout.CENTER);
        return right;
    }

    // =====================================================================
    //  การจัดการรายการรูปทรงที่ทาย (Guess handling)
    // =====================================================================

    /**
     * วางรูปทรงลงในช่องตาม slotIndex หากรูปทรงมีอยู่แล้วจะสลับตำแหน่ง หากเป็นช่องว่างจะต่อท้าย
     */
    private void placeShape(String shapeName, int slotIndex) {
        if (submitted) {
            return;   // ส่งคำตอบแล้ว ไม่อนุญาตให้แก้ไข
        }
        int from = guessedShapes.indexOf(shapeName);
        if (from >= 0) {
            if (slotIndex >= guessedShapes.size()) {
                guessedShapes.remove(from);
                guessedShapes.add(shapeName);
            } else {
                Collections.swap(guessedShapes, from, slotIndex);
            }
        } else if (slotIndex < guessedShapes.size()) {
            guessedShapes.set(slotIndex, shapeName);
        } else if (guessedShapes.size() < requiredPicks) {
            guessedShapes.add(shapeName);
        }
        refreshSelection();
    }

    /** ลบรูปทรงออกจากการทาย */
    private void removeShape(String shapeName) {
        if (!submitted && guessedShapes.remove(shapeName)) {
            refreshSelection();
        }
    }

    /** อัปเดตคอมโพเนนต์ทั้งหมดที่ขึ้นอยู่กับจำนวนรูปทรงที่ทาย */
    private void refreshSelection() {
        updateGuessSlots();
        selectedBadge.setText(picksText());
        pickCounterLabel.setText(picksCountText());
        for (var picker : pickers) {
            picker.setSelected(guessedShapes.contains(picker.shapeName));
        }
    }

    private String picksText() {
        return picksCountText() + " selected";
    }

    private String picksCountText() {
        return guessedShapes.size() + (requiredPicks == 0 ? " / ?" : " / " + requiredPicks);
    }

    // =====================================================================
    //  การเชื่อมต่อระบบมัลติเพลเยอร์ (Multiplayer hooks เรียกโดย MainApp)
    // =====================================================================

    // ตั้งชื่อผู้เล่นเครื่องนี้
    public void setMyName(String name) {
        myName = (name == null) ? "" : name;
    }

    /**
     * เริ่มต้นรอบใหม่ ล้างคำตอบเดิม และรอให้ Questioner ล็อกรูปทรงก่อนเริ่มนับเวลา
     */
    public void startRound(int roundNumber, int totalRounds, String questioner, int requiredShapes) {
        submitted = false;
        secretLocked = false;
        requiredPicks = (requiredShapes >= GameServer.MIN_SHAPES && requiredShapes <= GameServer.MAX_SHAPES)
                ? requiredShapes : GameServer.MIN_SHAPES;
        questionerName = (questioner == null) ? "" : questioner;
        guessedShapes.clear();
        roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
        setTargetBadge(requiredPicks);
        showWaitingTime();
        setStatus("Waiting for questioner (" + questionerName + ") to lock in the shapes...");
        updateSubmittedBadge();
        refreshSelection();
        setSubmitEnabled(false);
    }

    /**
     * ทำงานเมื่อเซิร์ฟเวอร์แจ้งว่า Questioner ได้ล็อกรูปทรงแล้ว
     * ปลดล็อกปุ่มส่งคำตอบ และเริ่มเปิดให้ทายได้
     */
    public void onSecretLocked(String questioner, int count) {
        secretLocked = true;
        questionerName = (questioner == null) ? "" : questioner;
        requiredPicks = Math.max(1, count);
        guessedShapes.clear();
        setTargetBadge(requiredPicks);
        setStatus("The questioner locked in " + requiredPicks + " shape" + (requiredPicks == 1 ? "" : "s")
                + ". Pick yours, then press SUBMIT GUESS.");
        setSubmitEnabled(!submitted);
        if (!submitted) {
            updateSubmittedBadge();
        }
        refreshSelection();
    }

    /** ดึงจำนวนรูปทรงที่ต้องทายในรอบนี้ */
    public int getRequiredPicks() {
        return requiredPicks;
    }

    /** อัปเดตเวลานับถอยหลังตาม TICK ของเซิร์ฟเวอร์ (10 วิสุดท้ายเป็นสีแดง) */
    public void setTimeLeft(int seconds) {
        boolean hurry = seconds <= GameServer.BONUS_WINDOW_SECONDS;
        AppDialog.paintTopBadge(timeBadge, AppDialog.timeText(seconds),
                hurry ? AppDialog.DANGER_BG : AppDialog.WARNING_BG,
                hurry ? AppDialog.DANGER_TEXT : AppDialog.WARNING_TEXT,
                hurry ? AppDialog.DANGER_BORDER : AppDialog.WARNING_BORDER, true);
    }

    // แสดงเวลารอคอยก่อนที่นาฬิกาจะเริ่มนับจริง
    private void showWaitingTime() {
        AppDialog.paintTopBadge(timeBadge, "Time: --:--", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, true);
    }

    private void setTargetBadge(int count) {
        targetBadge.setText(AppDialog.targetText(count));
    }

    /** ล็อกการทายหลังจากเซิร์ฟเวอร์ตอบรับคำตอบแล้ว ป้องกันการส่งซ้ำ */
    public void markSubmitted() {
        submitted = true;
        setSubmitEnabled(false);
        setStatus("Guess submitted. Waiting for the other players...");
        AppDialog.paintTopBadge(submittedBadge, "Submitted", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                AppDialog.SUCCESS_BORDER, false);
    }

    /** อัปเดตข้อความแนะนำสถานะใต้การ์ด */
    public void setStatus(String text) {
        statusLabel.setText(text);
    }

    /** รีเฟรชสกอร์บอร์ดคะแนนสดฝั่งซ้าย */
    public void setScoreboard(String[] names, int[] scores) {
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            int score = (i < scores.length) ? scores[i] : 0;
            boolean me = names[i].equals(myName);
            if (me) {
                scoreBadge.setText("Score: " + score);
            }
            scoreRowsPanel.add(createScoreRow(AppDialog.initialOf(names[i]), AppDialog.avatarColor(i), names[i],
                    score, me));
        }
        scoreRowsPanel.revalidate();
        scoreRowsPanel.repaint();
    }

    /** ดึงรายการรูปทรงที่ทาย */
    public List<String> getGuessedShapes() {
        return new ArrayList<>(guessedShapes);
    }

    /**
     * ตรวจสอบความถูกต้องก่อนส่งคำตอบทายไปยังเซิร์ฟเวอร์
     */
    private void submitGuess() {
        if (submitted) {
            AppDialog.showInfo(this, "Already submitted", "You already submitted a guess this round.");
            return;
        }
        if (!secretLocked || requiredPicks == 0) {
            AppDialog.showWarning(this, "Not yet", "Wait for the questioner to lock in the secret shapes first.");
            return;
        }
        if (guessedShapes.size() != requiredPicks) {
            AppDialog.showWarning(this, "Not ready yet", "Pick exactly " + requiredPicks + " shape"
                    + (requiredPicks == 1 ? "" : "s") + " before submitting.");
            return;
        }
        if (onSubmitAction == null) {
            markSubmitted();   // โหมดทดสอบพรีวิวโดยไม่ต้องต่อเน็ตเวิร์ก
            return;
        }
        onSubmitAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "submit"));
    }

    // เปิด/ปิดการใช้งานปุ่มส่งคำตอบ พร้อมเปลี่ยนสี
    private void setSubmitEnabled(boolean enabled) {
        submitBtn.setEnabled(enabled);
        submitBtn.setBackground(enabled ? AppDialog.PRIMARY_BLUE : AppDialog.SLOT_BORDER);
    }

    // อัปเดตป้ายสถานะการส่งคำตอบ
    private void updateSubmittedBadge() {
        AppDialog.paintTopBadge(submittedBadge, submitted ? "Submitted" : "Not submitted",
                submitted ? AppDialog.SUCCESS_BG : AppDialog.WARNING_BG,
                submitted ? AppDialog.SUCCESS_TEXT : AppDialog.WARNING_TEXT,
                submitted ? AppDialog.SUCCESS_BORDER : AppDialog.WARNING_BORDER, false);
    }
}
