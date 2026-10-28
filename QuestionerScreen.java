import java.awt.Point;
import java.awt.event.*;
import java.util.*;

/** หน้าจอผู้ตั้งโจทย์ */
public class QuestionerScreen extends QuestionerScreen_UI {

    // ===== ข้อมูลสถานะการเลือกรูปทรง (State) =====
    private final Set<String> selectedShapes = new LinkedHashSet<>();   // เซ็ตเก็บชื่อรูปทรงที่เลือก (เริ่มใหม่ทุกรอบ)
    private final Map<String, double[]> shapeSpots = new HashMap<>();   // เก็บพิกัด {x, y} ของแต่ละรูปทรงเป็นสัดส่วนบน Canvas (0.0 - 1.0)
    private final ActionListener onConfirmAction;                       // ตัวรับเหตุการณ์เมื่อกดปุ่มยืนยัน (Confirm)
    private int requiredShapes = GameServer.MIN_SHAPES;  // จำนวนรูปทรงที่เซิร์ฟเวอร์สุ่มขึ้นมาสำหรับรอบนี้
    private boolean selectionLocked;                     // สถานะว่าล็อกตัวเลือกแล้วหรือไม่ (หลังกดยืนยัน)
    private boolean roundActive;                         // สถานะว่ารอบกำลังดำเนินอยู่หรือไม่
    private String myName = "";                          // ชื่อของผู้เล่นเครื่องนี้

    /** สร้างหน้าจอผู้ตั้งโจทย์ */
    public QuestionerScreen(ActionListener onConfirmAction) {
        super();
        this.onConfirmAction = onConfirmAction;
        refreshUI();
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จาก UI (Event hooks)
    // =====================================================================

    /** ปุ่มยืนยันชุดรูปทรง */
    @Override
    public void onConfirmClicked() {
        confirmSelection();
    }

    /** ปุ่มล้าง Canvas */
    @Override
    public void onClearClicked() {
        if (selectionLocked) {
            return;
        }
        selectedShapes.clear();
        shapeSpots.clear();
        refreshUI();
    }

    /** วางรูปทรงบน Canvas */
    @Override
    public void onShapeDroppedToCanvas(String shapeName, Point where) {
        if (selectionLocked) {
            return; // หากล็อกแล้ว ไม่อนุญาตให้แก้ไข
        }
        if (selectedShapes.size() >= requiredShapes && !selectedShapes.contains(shapeName)) {
            return; // ไม่ให้เลือกเกินจำนวนที่กำหนด
        }
        selectedShapes.add(shapeName);
        // พิกัดกึ่งกลางจากจุดวาง
        double fx = where.x / (double) Math.max(1, canvas.getWidth());
        double fy = where.y / (double) Math.max(1, canvas.getHeight());
        shapeSpots.put(shapeName, new double[] {clamp(fx), clamp(fy)});
        refreshUI();
    }

    /** ลากรูปทรงออกจาก Canvas */
    @Override
    public void onShapeRemoved(String shapeName) {
        selectedShapes.remove(shapeName);
        shapeSpots.remove(shapeName);
        refreshUI();
    }

    /** เงื่อนไขการรับรูปทรงบน Canvas */
    @Override
    public boolean canAcceptDrop(String shapeName) {
        return selectedShapes.contains(shapeName) || selectedShapes.size() < requiredShapes;
    }

    /** สถานะล็อกหลังยืนยัน */
    @Override
    public boolean isInputLocked() {
        return selectionLocked;
    }

    /** ตำแหน่งรูปทรงบน Canvas */
    @Override
    public double[] shapeSpot(String shapeName) {
        return shapeSpots.get(shapeName);
    }

    // =====================================================================
    // การอัปเดตการแสดงผล
    // =====================================================================

    /** วาด Canvas ใหม่ */
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
    // Multiplayer hooks (เรียกโดย MainApp)
    // =====================================================================

    // กำหนดชื่อของผู้เล่นเครื่องนี้
    public void setMyName(String name) {
        myName = name;
    }

    /** เริ่มรอบใหม่ */
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
        String shapeWord;
        if (this.requiredShapes == 1) {
            shapeWord = "";
        } else {
            shapeWord = "s";
        }
        setStatus("Pick exactly " + this.requiredShapes + " shape" + shapeWord
                + " for the others to guess, then press CONFIRM.");
        renderTimeWaiting();
        refreshUI();
        renderStep(1);
    }

    /** จำนวนรูปทรงเป้าหมาย */
    public int getRequiredPicks() {
        return requiredShapes;
    }

    /** รายการรูปทรงที่เลือก */
    public List<String> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    /** ล็อกหลังส่งข้อมูลแล้ว */
    public void lockSelection() {
        selectionLocked = true;
        renderConfirmEnabled(false);
        setStatus("Secret locked. Waiting for the other players to submit guesses...");
        renderStep(3);
    }

    /** อัปเดตสกอร์บอร์ด */
    public void setScoreboard(String[] names, int[] scores) {
        renderScoreboard(names, scores, myName);
    }

    /** ตรวจสอบก่อนยืนยัน */
    private void confirmSelection() {
        if (selectionLocked) {
            return;
        }
        int count = selectedShapes.size();
        if (count != requiredShapes) {
            String shapeWord;
            if (requiredShapes == 1) {
                shapeWord = "";
            } else {
                shapeWord = "s";
            }
            AppDialog.showWarning(this, "Not ready yet", "Please select exactly " + requiredShapes + " shape"
                    + shapeWord + " before confirming.");
            return;
        }
        onConfirmAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "confirm"));
    }

    // จำกัดช่วง 0.0-1.0
    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

}
