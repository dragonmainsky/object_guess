import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/** หน้าจอสรุปผลแต่ละรอบ */
public class RoundResultScreen extends RoundResultScreen_UI {

    private static final int COUNTDOWN_SECONDS = GameServer.INTERMISSION_SECONDS; // เวลานับถอยหลังช่วงพักรอบ (10 วินาที)

    private Timer countdown;            // ตัวนับถอยหลัง 1 ตัวต่อรอบ จะหยุดเมื่อเริ่มรอบใหม่
    private int secondsLeft;            // จำนวนวินาทีที่เหลือก่อนเปลี่ยนหน้าจอ

    /** สร้างหน้าจอสรุปผลรอบ */
    public RoundResultScreen() {
        super();
        renderCountdown(COUNTDOWN_SECONDS, false);
    }

    // =====================================================================
    //  เมธอดแสดงผลข้อมูลที่เรียกโดย MainApp
    // =====================================================================

    /** วาดเนื้อหาจากข้อมูล ROUND_RESULT (อาเรย์ทุกตัวยาวเท่ากัน) */
    public void showResult(int round, int totalRounds, boolean isFinal, String[] secret, String reason,
                           String questioner, String[] names, int[] deltas, int[] totals,
                           String[][] guesses, int[] correctCounts, boolean[] bonuses) {
        // questioner จาก arg() เป็นค่าว่าง ไม่ใช่ null
        String questionerName = questioner;
        renderHeader(round, totalRounds, isFinal, questionerName);

        // ป้ายเหตุผลที่จบรอบ
        if ("time".equals(reason)) {
            renderReason("Time is up", true);
        } else {
            String badgeText;
            if ("all".equals(reason)) {
                badgeText = "All guesses in";
            } else {
                badgeText = "Round complete";
            }
            renderReason(badgeText, false);
        }

        // รูปทรงเฉลย
        renderSecret(secret);

        // แถวผลของผู้เล่น
        renderResults(names, deltas, totals, guesses, correctCounts, bonuses, questionerName, secret);

        // เริ่มนับถอยหลังก่อนไปหน้าถัดไป
        startCountdown(isFinal);
        revalidate();
        repaint();
    }

    // =====================================================================
    //  การนับถอยหลังระหว่างพักรอบ (Countdown)
    // =====================================================================

    /** เริ่มนับถอยหลัง */
    private void startCountdown(boolean isFinal) {
        stopCountdown();
        secondsLeft = COUNTDOWN_SECONDS;
        renderCountdown(secondsLeft, isFinal);
        countdown = new Timer(1000, new ActionListener() {
            /** ลดเวลาลงหนึ่งวินาที */
            @Override
            public void actionPerformed(ActionEvent event) {
                if (event.getSource() != countdown) {
                    return;   // Event ค้างจาก Timer ตัวเก่าที่ถูกสั่งหยุดไปแล้ว
                }

                secondsLeft--;

                if (secondsLeft <= 0) {
                    secondsLeft = 0;
                    renderCountdown(secondsLeft, isFinal);
                    stopCountdown();
                } else {
                    renderCountdown(secondsLeft, isFinal);
                }
            }
        });
        countdown.start();
    }

    /** หยุดนับถอยหลัง */
    public void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

}
