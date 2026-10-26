import java.awt.Point;
import java.awt.event.*;
import java.util.*;

/**
 * QuestionerScreen: หน้าจอของผู้ตั้งโจทย์ (หน้าจอที่ 3)
 * ผู้ตั้งโจทย์จะเลือกลวดลายรูปทรงความลับสำหรับรอบนี้
 * โดยสามารถลากรูปทรงจากรายการ Objects ไปวางบนผืนผ้าใบ (Canvas),
 * ลากจัดตำแหน่งบน Canvas หรือลากรูปทรงกลับไปยังรายการ Objects เพื่อลบออกได้
 */
public class QuestionerScreen extends QuestionerScreen_UI {

    // ===== ข้อมูลสถานะการเลือกรูปทรง (State) =====
    private final Set<String> selectedShapes = new LinkedHashSet<>();   // เซ็ตเก็บชื่อรูปทรงที่เลือก (เริ่มใหม่ทุกรอบ)
    private final Map<String, double[]> shapeSpots = new HashMap<>();   // เก็บพิกัด {x, y} ของแต่ละรูปทรงเป็นสัดส่วนบน Canvas (0.0 - 1.0)
    private final ActionListener onConfirmAction;                       // ตัวรับเหตุการณ์เมื่อกดปุ่มยืนยัน (Confirm)
    private int requiredShapes = GameServer.MIN_SHAPES;  // จำนวนรูปทรงที่เซิร์ฟเวอร์สุ่มขึ้นมาสำหรับรอบนี้
    private boolean selectionLocked;                     // สถานะว่าล็อกตัวเลือกแล้วหรือไม่ (หลังกดยืนยัน)
    private boolean roundActive;                         // สถานะว่ารอบกำลังดำเนินอยู่หรือไม่
    private String myName = "";                          // ชื่อของผู้เล่นเครื่องนี้

    /**
     * คอนสตรักเตอร์สร้างหน้าจอผู้ตั้งโจทย์ จัดวาง 3 คอลัมน์ (ซ้าย: สกอร์บอร์ด & ขั้นตอน, กลาง: Canvas, ขวา: รายการ Objects)
     */
    public QuestionerScreen(ActionListener onConfirmAction) {
        super();
        this.onConfirmAction = onConfirmAction;
        refreshUI();
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จาก UI (Event hooks)
    // =====================================================================

    @Override
    protected void onConfirmClicked() {
        confirmSelection();
    }

    @Override
    protected void onClearClicked() {
        if (selectionLocked) {
            return;
        }
        selectedShapes.clear();
        shapeSpots.clear();
        refreshUI();
    }

    @Override
    protected void onShapeDroppedToCanvas(String shapeName, Point where) {
        if (selectionLocked) {
            return; // หากล็อกแล้ว ไม่อนุญาตให้แก้ไข
        }
        if (selectedShapes.size() >= requiredShapes && !selectedShapes.contains(shapeName)) {
            return; // ไม่ให้เลือกเกินจำนวนที่กำหนด
        }
        selectedShapes.add(shapeName);
        // คำนวณพิกัดกึ่งกลางตามจุดที่เมาส์ปล่อยวาง
        double fx = where.x / (double) Math.max(1, canvas.getWidth());
        double fy = where.y / (double) Math.max(1, canvas.getHeight());
        shapeSpots.put(shapeName, new double[] {clamp(fx), clamp(fy)});
        refreshUI();
    }

    @Override
    protected void onShapeRemoved(String shapeName) {
        selectedShapes.remove(shapeName);
        shapeSpots.remove(shapeName);
        refreshUI();
    }

    @Override
    protected boolean canAcceptDrop(String shapeName) {
        return selectedShapes.contains(shapeName) || selectedShapes.size() < requiredShapes;
    }

    @Override
    protected boolean isInputLocked() {
        return selectionLocked;
    }

    @Override
    protected double[] shapeSpot(String shapeName) {
        return shapeSpots.get(shapeName);
    }

    // =====================================================================
    //  การอัปเดตการแสดงผลเมื่อสถานะเปลี่ยน (State changes)
    // =====================================================================

    /**
     * วาดรูปทรงบน Canvas ใหม่ และรีเฟรชคอมโพเนนต์ทั้งหมดที่เกี่ยวข้องกับจำนวนที่เลือก
     */
    private void refreshUI() {
        int count = selectedShapes.size();
        int activeStep;
        if (!roundActive) {
            activeStep = 0;
        } else if (selectionLocked) {
            activeStep = 3;
        } else if (count == requiredShapes) {
            activeStep = 2;
        } else {
            activeStep = 1;
        }
        renderSelection(new ArrayList<>(selectedShapes), requiredShapes, activeStep);
    }

    // =====================================================================
    //  การเชื่อมต่อระบบมัลติเพลเยอร์ (Multiplayer hooks เรียกโดย MainApp)
    // =====================================================================

    // กำหนดชื่อของผู้เล่นเครื่องนี้
    public void setMyName(String name) {
        myName = (name == null) ? "" : name;
    }

    /**
     * เริ่มต้นรอบใหม่ ล้าง Canvas และตั้งค่าเป้าหมายจำนวนรูปทรงตามที่เซิร์ฟเวอร์สุ่มมา
     */
    public void startRound(int roundNumber, int totalRounds, int requiredShapes) {
        if (requiredShapes >= GameServer.MIN_SHAPES && requiredShapes <= GameServer.MAX_SHAPES) {
            this.requiredShapes = requiredShapes;
        }
        roundActive = true;
        selectionLocked = false;
        selectedShapes.clear();
        shapeSpots.clear();
        renderRoundLabel(roundNumber, totalRounds);
        renderConfirmEnabled(true);
        setStatus("Pick exactly " + this.requiredShapes + " shape" + (this.requiredShapes == 1 ? "" : "s")
                + " for the others to guess, then press CONFIRM.");
        renderTimeWaiting();
        refreshUI();
        renderStep(1);
    }

    /** ดึงจำนวนรูปทรงที่ต้องเลือกในรอบนี้ */
    public int getRequiredPicks() {
        return requiredShapes;
    }

    /** ดึงรายการรูปทรงที่เลือกไว้เรียงตามลำดับที่เลือก */
    public List<String> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    /**
     * ล็อกการเลือกหลังจากส่งข้อมูลไปยังเซิร์ฟเวอร์แล้ว และเข้าสู่สถานะรอคนอื่นทาย
     */
    public void lockSelection() {
        selectionLocked = true;
        renderConfirmEnabled(false);
        setStatus("Secret locked. Waiting for the other players to submit guesses...");
        renderStep(3);
    }

    /**
     * อัปเดตรายการคะแนนสดของผู้เล่นทุกคนจากเซิร์ฟเวอร์
     */
    public void setScoreboard(String[] names, int[] scores) {
        renderScoreboard(names, scores, myName);
    }

    /**
     * ตรวจสอบว่าเลือกรูปทรงครบตามเป้าหมายหรือไม่ ก่อนส่งเหตุการณ์ยืนยัน (Confirm)
     */
    private void confirmSelection() {
        if (selectionLocked) {
            return;
        }
        int count = selectedShapes.size();
        if (count != requiredShapes) {
            AppDialog.showWarning(this, "Not ready yet", "Please select exactly " + requiredShapes + " shape"
                    + (requiredShapes == 1 ? "" : "s") + " before confirming.");
            return;
        }
        onConfirmAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "confirm"));
    }

    // จำกัดค่าให้อยู่ในช่วง 0.0 ถึง 1.0
    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

}
