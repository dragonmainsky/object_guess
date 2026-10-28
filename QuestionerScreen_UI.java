import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/** ส่วนหน้าตาของหน้าจอผู้ตั้งโจทย์ */
public class QuestionerScreen_UI extends JPanel {

    private static final int SMALL_ICON = 36;    // ขนาดรูปภาพในช่องเลือกรูปทรงฝั่งขวา
    private static final int BIG_ICON = 100;     // ขนาดรูปภาพบน Canvas และขนาดรูปที่ลอยตามเมาส์ขณะลาก
    private static final int LEFT_WIDTH = 260;   // ความกว้างของคอลัมน์ซ้าย
    private static final int RIGHT_WIDTH = 330;  // ความกว้างของคอลัมน์ขวา
    private static final Color CANVAS_BG = new Color(250, 252, 255); // สีพื้นหลังของ Canvas

    protected final ShapeImages.DragBoard drag;                           // ตัวจัดการระบบลากและวาง (DragBoard)
    protected final List<ShapeImages.Picker> pickers = new ArrayList<>(); // รายการช่องเลือกรูปทรง
    protected DashedCanvas canvas;                         // ผืนผ้าใบแสดงรูปทรงที่มีเส้นประ
    protected JLabel selectedCounterLabel;                 // ป้ายนับจำนวนรูปทรงที่เลือก
    protected JPanel scoreRowsPanel;                       // พาเนลแสดงแถบคะแนนผู้เล่น
    protected JLabel roundLabel;                           // ป้ายบอกหมายเลขรอบ
    protected JLabel hintLabel;                            // ป้ายข้อความแนะนำใต้ Canvas
    protected JButton confirmButton;                       // ปุ่มกดยืนยันตัวเลือกรูปทรง
    protected JLabel timeBadge;                            // ป้ายเวลานับถอยหลัง
    protected JLabel targetBadge;                          // ป้ายบอกจำนวนเป้าหมายรูปทรงที่ต้องเลือก

    private final List<StepRow> stepRows = new ArrayList<>(); // รายการแถวแสดงขั้นตอนความคืบหน้า

    /** สร้างหน้าจอผู้ตั้งโจทย์ */
    public QuestionerScreen_UI() {
        drag = new ShapeImages.DragBoard(this, BIG_ICON);
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        JPanel leftColumn = createLeftPanel();
        JPanel rightColumn = createRightPanel();
        leftColumn.setPreferredSize(new Dimension(LEFT_WIDTH, leftColumn.getPreferredSize().height));
        rightColumn.setPreferredSize(new Dimension(RIGHT_WIDTH, rightColumn.getPreferredSize().height));

        JPanel columns = AppDialog.panel(new BorderLayout(12, 0));
        columns.add(leftColumn, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(rightColumn, BorderLayout.EAST);

        JPanel mainContent = AppDialog.panel(new BorderLayout(12, 12));
        mainContent.setBorder(new EmptyBorder(18, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
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

    /** ปุ่ม CONFIRM */
    protected void onConfirmClicked() {
    }

    /** ปุ่ม CLEAR */
    protected void onClearClicked() {
    }

    /** วางรูปทรงบน Canvas */
    protected void onShapeDroppedToCanvas(String shapeName, Point where) {
    }

    /** ลากรูปทรงกลับไปยัง Objects */
    protected void onShapeRemoved(String shapeName) {
    }

    /** เงื่อนไขการรับรูปทรงบน Canvas */
    protected boolean canAcceptDrop(String shapeName) {
        return false;
    }

    /** สถานะล็อกอินพุต */
    protected boolean isInputLocked() {
        return false;
    }

    /** พิกัดของรูปทรงบน Canvas */
    protected double[] shapeSpot(String shapeName) {
        return null;
    }

    // =====================================================================
    //  การสร้างแต่ละส่วนของหน้าจอ (Sections)
    // =====================================================================

    /** แถบหัวข้อด้านบน */
    private JPanel createSubHeader() {
        JPanel titles = AppDialog.titleStack(2,
                AppDialog.label("YOUR TURN TO CHOOSE", AppDialog.SUBTITLE, AppDialog.PRIMARY_BLUE),
                AppDialog.label("Select the shapes the others must guess.", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK));

        targetBadge = AppDialog.topBadge(AppDialog.targetText(GameServer.MIN_SHAPES), AppDialog.BLUE_LIGHT_BG,
                AppDialog.PRIMARY_BLUE, AppDialog.BLUE_BORDER, false);
        timeBadge = AppDialog.topBadge("Time: --:--", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, true);
        JPanel badges = AppDialog.group(FlowLayout.RIGHT, 12, 0, targetBadge, timeBadge);
        return AppDialog.pageHeader(titles, badges, BorderLayout.CENTER, 4, 0, 6, 0);
    }

    /** คอลัมน์ซ้าย: สกอร์บอร์ดและขั้นตอน */
    private JPanel createLeftPanel() {
        JPanel playersCard = AppDialog.stack(AppDialog.card());
        playersCard.add(alignLeft(AppDialog.label("Players", AppDialog.SUBTITLE, AppDialog.TEXT_DARK)));
        playersCard.add(alignLeft(AppDialog.label("Live round score", AppDialog.SMALL, AppDialog.TEXT_MUTED)));
        playersCard.add(AppDialog.strut(12));
        scoreRowsPanel = AppDialog.stack(AppDialog.panel());
        playersCard.add(alignLeft(scoreRowsPanel));
        playersCard.add(AppDialog.strut(14));

        // กติกาการให้คะแนน
        JPanel tipLines = AppDialog.panel(new GridLayout(2, 1));
        tipLines.add(AppDialog.label("Info: Guessers score +1 per correct shape,", AppDialog.SMALL, AppDialog.TEXT_MUTED));
        tipLines.add(AppDialog.label("and a wrong guess scores nothing for anyone.", AppDialog.SMALL, AppDialog.TEXT_MUTED));
        JPanel tipBox = AppDialog.pin(tipLines, BorderLayout.NORTH);
        tipBox.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppDialog.CARD_BORDER),
                new EmptyBorder(10, 0, 0, 0)));
        playersCard.add(alignLeft(tipBox));

        // การ์ดขั้นตอน 3 ขั้น
        JPanel roundCard = AppDialog.stack(AppDialog.card());
        roundLabel = AppDialog.label("Waiting to start", AppDialog.SUBTITLE, AppDialog.TEXT_DARK);
        roundCard.add(alignLeft(roundLabel));
        roundCard.add(AppDialog.strut(12));
        roundCard.add(createStepRow(1, "Choose your shapes").row);
        roundCard.add(AppDialog.strut(10));
        roundCard.add(createStepRow(2, "Confirm selection").row);
        roundCard.add(AppDialog.strut(10));
        roundCard.add(createStepRow(3, "Watch guesses").row);

        JPanel column = AppDialog.stack(AppDialog.panel());
        column.add(alignLeft(playersCard, 290));
        column.add(AppDialog.strut(12));
        column.add(alignLeft(roundCard, 200));
        column.add(AppDialog.glue());
        return column;
    }

    /** คอลัมน์กลาง: Canvas */
    private JPanel createCenterPanel() {
        JPanel titles = AppDialog.panel(new GridLayout(2, 1, 0, 2));
        titles.add(AppDialog.label("Your secret set", AppDialog.TITLE, AppDialog.TEXT_DARK));
        titles.add(AppDialog.label("Drag your shapes onto the canvas", AppDialog.BODY, AppDialog.TEXT_MUTED));

        // Canvas เป็นพื้นที่รับการวาง
        canvas = new DashedCanvas();
        drag.dropZone(canvas,
                () -> drag.fromBoard() || canAcceptDrop(drag.shape()),
                on -> canvas.setDropHighlight(on),
                (shapeName, where) -> onShapeDroppedToCanvas(shapeName, where));

        hintLabel = AppDialog.label("", AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER);
        JPanel centerCard = AppDialog.card(new BorderLayout(0, 12));
        centerCard.add(titles, BorderLayout.NORTH);
        centerCard.add(canvas, BorderLayout.CENTER);
        centerCard.add(hintLabel, BorderLayout.SOUTH);
        return centerCard;
    }

    /** คอลัมน์ขวา: รายการ Objects */
    private JPanel createRightPanel() {
        selectedCounterLabel = AppDialog.label("Selected: 0 / " + GameServer.MIN_SHAPES, AppDialog.SMALL_BOLD,
                AppDialog.PRIMARY_BLUE);
        JPanel objectsHeader = AppDialog.panel(new BorderLayout());
        objectsHeader.add(AppDialog.label("Objects", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.WEST);
        objectsHeader.add(selectedCounterLabel, BorderLayout.EAST);

        JPanel grid = AppDialog.panel(new GridLayout(4, 2, 8, 8));
        for (String name : ShapeImages.NAMES) {
            ShapeImages.Picker picker = new ShapeImages.Picker(name, SMALL_ICON, 7, 7, true);
            pickers.add(picker);
            drag.draggable(picker, name, false, () -> isInputLocked());
            grid.add(picker);
        }

        // ปุ่ม CLEAR
        JButton clearBtn = AppDialog.button("CLEAR", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 10, 18);
        clearBtn.addActionListener(event -> onClearClicked());
        // ปุ่ม CONFIRM
        confirmButton = AppDialog.button("CONFIRM", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER,
                AppDialog.PRIMARY_PRESS, Color.WHITE, AppDialog.PRIMARY_BLUE, 10, 18);
        confirmButton.addActionListener(event -> onConfirmClicked());

        JPanel buttonRow = AppDialog.panel(new GridLayout(1, 2, 8, 0));
        buttonRow.add(clearBtn);
        buttonRow.add(confirmButton);

        JPanel objectsCard = AppDialog.stack(AppDialog.card());
        objectsCard.add(alignLeft(objectsHeader, 24));
        objectsCard.add(alignLeft(AppDialog.label("Drag your shapes to the canvas", AppDialog.SMALL, AppDialog.TEXT_MUTED)));
        objectsCard.add(AppDialog.strut(10));
        objectsCard.add(alignLeft(grid));
        objectsCard.add(AppDialog.strut(14));
        objectsCard.add(alignLeft(buttonRow, 46));

        // การ์ดขวาเป็นพื้นที่ลบรูปทรง
        drag.dropZone(objectsCard, () -> drag.fromBoard(), on -> {
            if (on) {
                objectsCard.setBackground(AppDialog.DANGER_BG);
                objectsCard.setBorder(AppDialog.box(AppDialog.DANGER_BORDER, 1, 16, 16));
            } else {
                objectsCard.setBackground(AppDialog.CARD_BG);
                objectsCard.setBorder(AppDialog.box(AppDialog.CARD_BORDER, 1, 16, 16));
            }
        }, (shapeName, where) -> onShapeRemoved(shapeName));

        JPanel column = AppDialog.stack(AppDialog.panel());
        column.add(alignLeft(objectsCard, objectsCard.getPreferredSize().height));
        column.add(AppDialog.glue());
        return column;
    }

    // =====================================================================
    // การวาดข้อมูลจากสถานะ
    // =====================================================================

    /** วาด Canvas และรีเฟรชส่วนที่เกี่ยวข้อง */
    protected void renderSelection(List<String> selectedShapes, int requiredShapes, int activeStep) {
        canvas.removeAll();
        for (String shapeName : selectedShapes) {
            JPanel picture = ShapeImages.shape(shapeName, BIG_ICON);
            picture.setName(shapeName);   // ให้ Canvas ค้นหาพิกัดของรูปทรงได้จากชื่อนี้
            picture.setToolTipText("Drag to move " + shapeName + ", or back to Objects to remove it");
            drag.draggable(picture, shapeName, true, () -> isInputLocked());
            canvas.add(picture);
        }
        canvas.revalidate();
        canvas.repaint();

        int count = selectedShapes.size();
        selectedCounterLabel.setText("Selected: " + count + " / " + requiredShapes);
        targetBadge.setText(AppDialog.targetText(requiredShapes));

        // อัปเดตสถานะเลือกของช่อง
        for (ShapeImages.Picker picker : pickers) {
            picker.setSelected(selectedShapes.contains(picker.shapeName));
        }

        // ปรับขั้นตอนความคืบหน้า
        renderStep(activeStep);
    }

    /** แถบขั้นตอน 3 ขั้น */
    protected void renderStep(int activeStep) {
        for (int i = 0; i < stepRows.size(); i++) {
            StepRow step = stepRows.get(i);
            int number = i + 1;
            boolean done = number < activeStep;
            boolean current = number == activeStep;
            if (done) {
                step.circle.setBackground(AppDialog.SUCCESS_TEXT);
            } else if (current) {
                step.circle.setBackground(AppDialog.PRIMARY_BLUE);
            } else {
                step.circle.setBackground(AppDialog.SLOT_BORDER);
            }
            if (current) {
                step.text.setFont(AppDialog.BODY_BOLD);
            } else {
                step.text.setFont(AppDialog.BODY);
            }
            if (current) {
                step.text.setForeground(AppDialog.TEXT_DARK);
            } else {
                step.text.setForeground(AppDialog.TEXT_MUTED);
            }
        }
        revalidate();
        repaint();
    }

    /** ป้ายหมายเลขรอบ */
    protected void renderRoundLabel(int roundNumber, int totalRounds) {
        roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
    }

    /** ปุ่ม CONFIRM */
    protected void renderConfirmEnabled(boolean enabled) {
        confirmButton.setEnabled(enabled);
    }

    /** เวลารอก่อนเริ่มรอบ */
    protected void renderTimeWaiting() {
        AppDialog.showWaitingTime(timeBadge);
    }

    /** สกอร์บอร์ดฝั่งซ้าย */
    protected void renderScoreboard(String[] names, int[] scores, String myName) {
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                scoreRowsPanel.add(AppDialog.strut(8));
            }
            int score;
            if (i < scores.length) {
                score = scores[i];
            } else {
                score = 0;
            }
            scoreRowsPanel.add(AppDialog.scoreRow(names[i], i, score, names[i].equals(myName), 4, 8, true, 36));
        }
        scoreRowsPanel.revalidate();
        scoreRowsPanel.repaint();
    }

    /** ข้อความสถานะใต้ Canvas */
    public void setStatus(String text) {
        hintLabel.setText(text);
    }

    /** เวลานับถอยหลัง */
    public void setTimeLeft(int seconds) {
        AppDialog.setTimeLeft(timeBadge, seconds);
    }

    // =====================================================================
    //  การจัดการขั้นตอนและแถวคะแนน (Rows and Steps)
    // =====================================================================

    /** แถวขั้นตอนหนึ่งแถว */
    private StepRow createStepRow(int number, String text) {
        JPanel row = AppDialog.panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        alignLeft(row, 28);
        JLabel circle = AppDialog.avatar(String.valueOf(number), AppDialog.SLOT_BORDER, 20);
        JLabel textLabel = AppDialog.label(text, AppDialog.BODY, AppDialog.TEXT_MUTED);
        row.add(circle);
        row.add(textLabel);
        StepRow step = new StepRow(row, circle, textLabel);
        stepRows.add(step);
        return step;
    }

    // =====================================================================
    // ฟังก์ชันช่วยและคลาสภายใน
    // =====================================================================

    /** จำกัดความสูงและจัดชิดซ้าย */
    private static JPanel alignLeft(JPanel c, int maxHeight) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        return alignLeft(c);
    }

    /** จัด JPanel ชิดซ้าย */
    private static JPanel alignLeft(JPanel c) {
        c.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        return c;
    }

    /** จัด JLabel ชิดซ้าย */
    private static JLabel alignLeft(JLabel c) {
        c.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return c;
    }

    /** ข้อมูลของแถวขั้นตอน */
    private static class StepRow {
        final JPanel row;
        final JLabel circle;
        final JLabel text;

        /** คอมโพเนนต์ของแถวขั้นตอน */
        StepRow(JPanel row, JLabel circle, JLabel text) {
            this.row = row;
            this.circle = circle;
            this.text = text;
        }
    }

    /** Canvas วาดเส้นประและจัดตำแหน่งรูปทรง */
    protected class DashedCanvas extends JPanel {
        private boolean dropHighlight; // สถานะไฮไลต์เมื่อมีรูปทรงกำลังลากเข้ามาวาง

        /** พื้นหลังโปร่งใส */
        DashedCanvas() {
            setOpaque(false);
            setLayout(null);   // วางตำแหน่งคอมโพเนนต์ด้วยพิกัดเจาะจงผ่าน doLayout() ด้านล่าง
        }

        /** จัดตำแหน่งรูปทรงบน Canvas */
        @Override
        public void doLayout() {
            for (int i = 0; i < getComponentCount(); i++) {
                Component shape = getComponent(i);
                double[] spot = shapeSpot(shape.getName());
                if (spot == null) {
                    continue;
                }
                Dimension size = shape.getPreferredSize();
                int x = (int) Math.round(spot[0] * getWidth() - size.width / 2.0);
                int y = (int) Math.round(spot[1] * getHeight() - size.height / 2.0);
                shape.setBounds(Math.max(0, Math.min(x, Math.max(0, getWidth() - size.width))),
                        Math.max(0, Math.min(y, Math.max(0, getHeight() - size.height))),
                        size.width, size.height);
            }
        }

        /** ไฮไลต์ Canvas เมื่อมีรูปทรงลากผ่าน */
        void setDropHighlight(boolean on) {
            dropHighlight = on;
            repaint();
        }

        /** วาดพื้นหลังและเส้นประ */
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            if (dropHighlight) {
                g.setColor(AppDialog.BLUE_LIGHT_BG);
            } else {
                g.setColor(CANVAS_BG);
            }
            g.fillRect(0, 0, w - 1, h - 1);
            if (dropHighlight) {
                g.setColor(AppDialog.PRIMARY_BLUE);
            } else {
                g.setColor(AppDialog.CARD_BORDER);
            }
            g.drawRect(0, 0, w - 1, h - 1);

            // เส้นประกลาง Canvas
            g.setColor(AppDialog.CARD_BORDER);
            int midY = h / 2 + 30;
            int midX = w / 2;
            for (int x = 20; x < w - 20; x += 10) {
                g.drawLine(x, midY, Math.min(x + 6, w - 20), midY);
            }
            for (int y = 20; y < h - 20; y += 10) {
                g.drawLine(midX, y, midX, Math.min(y + 6, h - 20));
            }
        }
    }
}
