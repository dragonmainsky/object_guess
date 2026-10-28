import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** หน้าจอของผู้ทาย */
public class GuesserScreen extends GuesserScreen_UI {

    // ===== ข้อมูลสถานะการทาย (State) =====
    private final List<String> guessedShapes = new ArrayList<>();   // รายการรูปทรงที่ผู้เล่นทาย (เริ่มใหม่ทุกรอบ)
    private final ActionListener onSubmitAction;                       // ตัวรับเหตุการณ์เมื่อกดปุ่มส่งคำตอบทาย
    private int requiredPicks;          // จำนวนรูปทรงที่ต้องทายในรอบนี้ (กำหนดโดย SECRET_LOCKED)
    private boolean submitted;          // สถานะว่าส่งคำตอบในรอบนี้ไปแล้วหรือไม่
    private boolean secretLocked;       // สถานะว่า Questioner ได้ล็อกรูปทรงแล้วหรือไม่
    private String myName = "";         // ชื่อของผู้เล่นเครื่องนี้
    private String questionerName = ""; // ชื่อของ Questioner ในรอบนี้

    /** สร้างหน้าจอผู้ทาย */
    public GuesserScreen(ActionListener onSubmitAction) {
        super();
        this.onSubmitAction = onSubmitAction;
        refreshSelection();
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จาก UI (Event hooks)
    // =====================================================================

    /** ปุ่มส่งคำตอบ */
    @Override
    public void onSubmitClicked() {
        submitGuess();
    }

    /** วางรูปทรงในช่องทาย */
    @Override
    public void onShapeDroppedToSlot(String shapeName, int slotIndex) {
        placeShape(shapeName, slotIndex);
    }

    /** ลากรูปทรงออกจากช่องทาย */
    @Override
    public void onShapeRemoved(String shapeName) {
        removeShape(shapeName);
    }

    /** สถานะล็อกหลังส่งคำตอบ */
    @Override
    public boolean isInputLocked() {
        return submitted;
    }

    // =====================================================================
    //  การจัดการรายการรูปทรงที่ทาย (Guess handling)
    // =====================================================================

    /** จัดการรูปทรงที่วางในช่อง */
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

    /** อัปเดต UI ตามจำนวนรูปที่ทาย */
    private void refreshSelection() {
        renderSelection(new ArrayList<>(guessedShapes), requiredPicks);
    }

    // =====================================================================
    // Multiplayer hooks
    // =====================================================================

    // ตั้งชื่อผู้เล่นเครื่องนี้
    public void setMyName(String name) {
        myName = name;
    }

    /** เริ่มรอบใหม่ */
    public void startRound(int roundNumber, int totalRounds, String questioner, int requiredShapes) {
        submitted = false;
        secretLocked = false;
        if (requiredShapes >= GameServer.MIN_SHAPES && requiredShapes <= GameServer.MAX_SHAPES) {
            requiredPicks = requiredShapes;
        } else {
            requiredPicks = GameServer.MIN_SHAPES;
        }
        questionerName = questioner;
        guessedShapes.clear();
        renderRoundLabel(roundNumber, totalRounds);
        renderTarget(requiredPicks);
        renderTimeWaiting();
        setStatus("Waiting for questioner (" + questionerName + ") to lock in the shapes...");
        renderSubmitted(false);
        refreshSelection();
        renderSubmitEnabled(false);
    }

    /** ปลดล็อกเมื่อ Questioner ล็อกรูปทรง */
    public void onSecretLocked(String questioner, int count) {
        secretLocked = true;
        questionerName = questioner;
        requiredPicks = Math.max(1, count);
        guessedShapes.clear();
        renderTarget(requiredPicks);
        String shapeWord;
        if (requiredPicks == 1) {
            shapeWord = "";
        } else {
            shapeWord = "s";
        }
        setStatus("The questioner locked in " + requiredPicks + " shape" + shapeWord
                + ". Pick yours, then press SUBMIT GUESS.");
        renderSubmitEnabled(!submitted);
        // SECRET_LOCKED มาก่อนคำตอบแรก และ submitted = false แล้ว
        renderSubmitted(false);
        refreshSelection();
    }

    /** จำนวนรูปที่ต้องทาย */
    public int getRequiredPicks() {
        return requiredPicks;
    }

    /** ล็อกหลังส่งคำตอบ */
    public void markSubmitted() {
        submitted = true;
        renderSubmitEnabled(false);
        setStatus("Guess submitted. Waiting for the other players...");
        renderSubmitted(true);
    }

    /** สกอร์บอร์ดฝั่งซ้าย */
    public void setScoreboard(String[] names, int[] scores) {
        renderScoreboard(names, scores, myName);
    }

    /** รายการรูปที่ทาย */
    public List<String> getGuessedShapes() {
        return new ArrayList<>(guessedShapes);
    }

    /** ตรวจสอบก่อนส่งคำตอบ */
    private void submitGuess() {
        if (submitted) {
            AppDialog.showInfo(this, "Already submitted", "You already submitted a guess this round.");
            return;
        }
        if (!secretLocked) {
            AppDialog.showWarning(this, "Not yet", "Wait for the questioner to lock in the secret shapes first.");
            return;
        }
        if (guessedShapes.size() != requiredPicks) {
            String shapeWord;
            if (requiredPicks == 1) {
                shapeWord = "";
            } else {
                shapeWord = "s";
            }
            AppDialog.showWarning(this, "Not ready yet", "Pick exactly " + requiredPicks + " shape"
                    + shapeWord + " before submitting.");
            return;
        }
        onSubmitAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "submit"));
    }

}
