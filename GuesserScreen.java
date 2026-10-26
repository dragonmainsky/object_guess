import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * GuesserScreen: หน้าจอของผู้ทาย (หน้าจอที่ 4)
 * ผู้เล่นที่ทำหน้าที่ทายจะต้องเลือกรูปทรงให้ตรงกับที่ Questioner เลือกไว้
 * สามารถลากรูปทรงจากรายการ Objects มาวางในช่องทาย (Guess slots),
 * สลับตำแหน่งระหว่างช่อง หรือลากกลับไปยัง Objects เพื่อลบออกได้
 */
public class GuesserScreen extends GuesserScreen_UI {

    // ===== ข้อมูลสถานะการทาย (State) =====
    private final List<String> guessedShapes = new ArrayList<>();   // รายการรูปทรงที่ผู้เล่นทาย (เริ่มใหม่ทุกรอบ)
    private final ActionListener onSubmitAction;                       // ตัวรับเหตุการณ์เมื่อกดปุ่มส่งคำตอบทาย
    private int requiredPicks;          // จำนวนรูปทรงที่ต้องทายในรอบนี้ (กำหนดโดย SECRET_LOCKED)
    private boolean submitted;          // สถานะว่าส่งคำตอบในรอบนี้ไปแล้วหรือไม่
    private boolean secretLocked;       // สถานะว่า Questioner ได้ล็อกรูปทรงแล้วหรือไม่
    private String myName = "";         // ชื่อของผู้เล่นเครื่องนี้
    private String questionerName = ""; // ชื่อของ Questioner ในรอบนี้

    /**
     * คอนสตรักเตอร์สร้างหน้าจอผู้ทาย จัดวางแบบ 3 คอลัมน์ และผูกปุ่ม ENTER สำหรับส่งคำตอบ
     */
    public GuesserScreen(ActionListener onSubmitAction) {
        super();
        this.onSubmitAction = onSubmitAction;
        refreshSelection();
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จาก UI (Event hooks)
    // =====================================================================

    @Override
    protected void onSubmitClicked() {
        submitGuess();
    }

    @Override
    protected void onShapeDroppedToSlot(String shapeName, int slotIndex) {
        placeShape(shapeName, slotIndex);
    }

    @Override
    protected void onShapeRemoved(String shapeName) {
        removeShape(shapeName);
    }

    @Override
    protected boolean isInputLocked() {
        return submitted;
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
        renderSelection(new ArrayList<>(guessedShapes), requiredPicks);
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
        renderRoundLabel(roundNumber, totalRounds);
        renderTarget(requiredPicks);
        renderTimeWaiting();
        setStatus("Waiting for questioner (" + questionerName + ") to lock in the shapes...");
        renderSubmitted(false);
        refreshSelection();
        renderSubmitEnabled(false);
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
        renderTarget(requiredPicks);
        setStatus("The questioner locked in " + requiredPicks + " shape" + (requiredPicks == 1 ? "" : "s")
                + ". Pick yours, then press SUBMIT GUESS.");
        renderSubmitEnabled(!submitted);
        if (!submitted) {
            renderSubmitted(false);
        }
        refreshSelection();
    }

    /** ดึงจำนวนรูปทรงที่ต้องทายในรอบนี้ */
    public int getRequiredPicks() {
        return requiredPicks;
    }

    /** ล็อกการทายหลังจากเซิร์ฟเวอร์ตอบรับคำตอบแล้ว ป้องกันการส่งซ้ำ */
    public void markSubmitted() {
        submitted = true;
        renderSubmitEnabled(false);
        setStatus("Guess submitted. Waiting for the other players...");
        renderSubmitted(true);
    }

    /** รีเฟรชสกอร์บอร์ดคะแนนสดฝั่งซ้าย */
    public void setScoreboard(String[] names, int[] scores) {
        renderScoreboard(names, scores, myName);
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
        onSubmitAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "submit"));
    }

}
