import java.awt.event.*;

/**
 * ScoreboardScreen: หน้าจอตารางคะแนนรวมสุดท้าย (หน้าจอที่ 5)
 * แสดงอันดับและผลคะแนนรวมทั้งหมดเมื่อจบทัวร์นาเมนต์
 * MainApp จะส่งข้อมูลคะแนนเข้ามาผ่านเมธอด setResults()
 */
public class ScoreboardScreen extends ScoreboardScreen_UI {

    private final ActionListener onNewGameAction; // ตัวจัดการเมื่อกดปุ่มเริ่มเกมใหม่
    private final ActionListener onQuitAction;    // ตัวจัดการเมื่อกดปุ่มออกจากห้อง

    // ข้อมูลคะแนนที่รับมาจากเซิร์ฟเวอร์
    private String[] playerNames = new String[0];
    private int[] playerScores = new int[0];
    private int roundsPlayed;            // จำนวนรอบที่เล่นไปแล้ว
    private int totalRounds;             // จำนวนรอบทั้งหมด (จำนวนผู้เล่น x 2)

    /**
     * คอนสตรักเตอร์สร้างหน้าจอสกอร์บอร์ด จัดวางแบบ 2 คอลัมน์ (ซ้าย: ถ้วยรางวัลและผู้ชนะ, ขวา: ตารางอันดับ)
     */
    public ScoreboardScreen(ActionListener onNewGameAction, ActionListener onQuitAction) {
        super();
        this.onNewGameAction = onNewGameAction;
        this.onQuitAction = onQuitAction;
        renderResults(playerNames, playerScores, roundsPlayed, totalRounds);
    }

    // =====================================================================
    //  การรับข้อมูลผลการแข่งขันจากเซิร์ฟเวอร์ (Results from Server)
    // =====================================================================

    /**
     * รับข้อมูลคะแนนล่าสุดจากเซิร์ฟเวอร์ จัดเรียงคะแนนจากมากไปน้อย (Insertion Sort) แล้วรีเฟรชหน้าจอ
     */
    public void setResults(String[] names, int[] scores, int rounds, int totalRounds) {
        // MainApp สร้าง names และ scores พร้อมกันจาก playerCount เดียวกัน
        // และผู้ที่ได้รับ ROUND_RESULT ต้องเป็นผู้เล่นในห้องอย่างน้อย 1 คน
        // จึงไม่ต้องเช็ก null, อาเรย์ว่าง, หรือความยาวของ scores อีก
        // ใช้ Insertion Sort เพื่อให้ผู้เล่นที่คะแนนเท่ากันคงลำดับเดิมตามที่เซิร์ฟเวอร์ส่งมา
        String[] sortedNames = new String[names.length];
        int[] sortedScores = new int[names.length];
        for (int i = 0; i < names.length; i++) {
            sortedNames[i] = names[i];
            sortedScores[i] = scores[i];
        }
        for (int i = 1; i < sortedNames.length; i++) {
            String name = sortedNames[i];
            int score = sortedScores[i];
            int j = i - 1;
            while (j >= 0 && sortedScores[j] < score) {
                sortedNames[j + 1] = sortedNames[j];
                sortedScores[j + 1] = sortedScores[j];
                j--;
            }
            sortedNames[j + 1] = name;
            sortedScores[j + 1] = score;
        }
        playerNames = sortedNames;
        playerScores = sortedScores;
        roundsPlayed = rounds;
        this.totalRounds = totalRounds;
        renderResults(playerNames, playerScores, roundsPlayed, this.totalRounds);
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จากปุ่ม (Button hooks)
    // =====================================================================

    /** โฮสต์กดปุ่มเริ่มเกมใหม่ */
    @Override
    protected void onNewGameClicked(ActionEvent event) {
        onNewGameAction.actionPerformed(event);
    }

    /** ผู้เล่นกดปุ่มออกจากห้อง */
    @Override
    protected void onQuitClicked(ActionEvent event) {
        onQuitAction.actionPerformed(event);
    }

}
