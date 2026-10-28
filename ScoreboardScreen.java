import java.awt.event.*;

/** หน้าจอตารางคะแนนรวมสุดท้าย */
public class ScoreboardScreen extends ScoreboardScreen_UI {

    private final ActionListener onNewGameAction; // ตัวจัดการเมื่อกดปุ่มเริ่มเกมใหม่
    private final ActionListener onQuitAction;    // ตัวจัดการเมื่อกดปุ่มออกจากห้อง

    // ข้อมูลคะแนนที่รับมาจากเซิร์ฟเวอร์
    private String[] playerNames = new String[0];
    private int[] playerScores = new int[0];
    private int roundsPlayed;            // จำนวนรอบที่เล่นไปแล้ว
    private int totalRounds;             // จำนวนรอบทั้งหมด (จำนวนผู้เล่น x 2)

    /** สร้างหน้าจอสกอร์บอร์ด */
    public ScoreboardScreen(ActionListener onNewGameAction, ActionListener onQuitAction) {
        super();
        this.onNewGameAction = onNewGameAction;
        this.onQuitAction = onQuitAction;
        renderResults(playerNames, playerScores, roundsPlayed, totalRounds);
    }

    // =====================================================================
    // ข้อมูลผลการแข่งขัน
    // =====================================================================

    /** รับผลคะแนนและเรียงอันดับ */
    public void setResults(String[] names, int[] scores, int rounds, int totalRounds) {
        // names/scores สร้างคู่กันและไม่เป็น null; เรียงแบบคงลำดับเมื่อคะแนนเท่ากัน
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

    /** ปุ่ม NEW GAME */
    @Override
    public void onNewGameClicked(ActionEvent event) {
        onNewGameAction.actionPerformed(event);
    }

    /** ปุ่ม QUIT */
    @Override
    public void onQuitClicked(ActionEvent event) {
        onQuitAction.actionPerformed(event);
    }

}
