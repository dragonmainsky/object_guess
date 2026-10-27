import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

/**
 * RoundResultScreen: หน้าจอแสดงผลคะแนนและเฉลยของแต่ละรอบ (หน้าจอคั่นระหว่างรอบ)
 * สร้างขึ้นจากข้อมูล ROUND_RESULT ที่ได้รับจากเซิร์ฟเวอร์เท่านั้น
 * ทำให้ทุกเครื่องในห้องแสดงผลรูปทรงเฉลยความลับและตัวเลขคะแนนตรงกันทั้งหมด
 */
public class RoundResultScreen extends RoundResultScreen_UI {

    private static final int COUNTDOWN_SECONDS = GameServer.INTERMISSION_SECONDS; // เวลานับถอยหลังช่วงพักรอบ (10 วินาที)

    private Timer countdown;            // ตัวนับถอยหลัง 1 ตัวต่อรอบ จะหยุดเมื่อเริ่มรอบใหม่
    private int secondsLeft;            // จำนวนวินาทีที่เหลือก่อนเปลี่ยนหน้าจอ

    /**
     * คอนสตรักเตอร์สร้างหน้าจอสรุปผลของรอบ แบ่งเป็น ส่วนหัว (Header), เนื้อหา (Body), และส่วนท้าย (Footer)
     */
    public RoundResultScreen() {
        super();
        renderCountdown(COUNTDOWN_SECONDS, false);
    }

    // =====================================================================
    //  เมธอดแสดงผลข้อมูลที่เรียกโดย MainApp
    // =====================================================================

    /**
     * วาดเนื้อหาทั้งหมดของหน้าจอจากชุดข้อมูล ROUND_RESULT ที่ได้รับจากเซิร์ฟเวอร์
     * ข้อมูลทุกอาเรย์มีความยาวเท่ากัน โดยแต่ละดัชนีแทนผู้เล่น 1 คน
     */
    public void showResult(int round, int totalRounds, boolean isFinal, String[] secret, String reason,
                           String questioner, String[] names, int[] deltas, int[] totals,
                           String[][] guesses, int[] correctCounts, boolean[] bonuses) {
        String questionerName = (questioner == null) ? "" : questioner;
        renderHeader(round, totalRounds, isFinal, questionerName);

        // ปรับข้อความและสีของป้ายเหตุผลที่จบรอบ
        if ("time".equals(reason)) {
            renderReason("Time is up", true);
        } else {
            String badgeText = "all".equals(reason) ? "All guesses in" : "Round complete";
            renderReason(badgeText, false);
        }

        // แสดงรูปทรงเฉลยความลับ
        renderSecret(secret);

        // สร้างแถวแสดงผลสำหรับผู้เล่นแต่ละคน
        renderResults(names, deltas, totals, guesses, correctCounts, bonuses, questionerName, secret);

        // เริ่มเวลานับถอยหลังก่อนเปิดรอบใหม่หรือหน้าตารางคะแนนสุดท้าย
        startCountdown(isFinal);
        revalidate();
        repaint();
    }

    // =====================================================================
    //  การนับถอยหลังระหว่างพักรอบ (Countdown)
    // =====================================================================

    /**
     * เริ่มนับถอยหลังจาก INTERMISSION_SECONDS ลงไปจนถึง 0
     */
    private void startCountdown(boolean isFinal) {
        stopCountdown();
        secondsLeft = COUNTDOWN_SECONDS;
        renderCountdown(secondsLeft, isFinal);
        countdown = new Timer(1000, new ActionListener() {
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

    /**
     * สั่งหยุดการนับถอยหลัง
     */
    public void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

}
