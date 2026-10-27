import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.function.Consumer;

/**
 * LobbyScreen: หน้าจอห้องพักคอย (หน้าจอที่ 2)
 * แสดงรายชื่อผู้เล่นที่อยู่ในห้อง สถานะความพร้อม (Ready) ข้อมูลการเชื่อมต่อ ระบบแชต และปุ่มเริ่มเกม
 * ข้อมูลทั้งหมดบนหน้าจอนี้จะถูกส่งมาจาก MainApp ตามข้อมูลที่ได้รับจาก GameServer
 */
public class LobbyScreen extends LobbyScreen_UI {

    private final ActionListener onStartGameAction; // ตัวจัดการเมื่อกดปุ่มเริ่มเกม
    private final ActionListener onLeaveAction;     // ตัวจัดการเมื่อกดปุ่มออกจากห้อง

    // ===== ข้อมูลสถานะของห้องแบบเรียลไทม์ (อัปเดตมาจาก MainApp) =====
    private final List<String> playerNames = new ArrayList<>();         // รายชื่อผู้เล่นทั้งหมดในห้อง
    private final Map<String, Boolean> readyStates = new LinkedHashMap<>(); // สถานะความพร้อมของผู้เล่นแต่ละคน
    private String myName = "";                     // ชื่อของผู้เล่นเครื่องนี้
    private String hostName = "";                   // ชื่อของหัวหน้าห้อง (โฮสต์)
    private boolean isHost;                         // เครื่องนี้เป็นโฮสต์หรือไม่
    private ActionListener readyAction;             // การทำงานเมื่อกดปุ่มสลับสถานะพร้อม
    private Consumer<String> chatAction;            // การทำงานเมื่อส่งข้อความแชต

    /**
     * คอนสตรักเตอร์สร้างหน้าจอล็อบบี้ จัดวางแบบ 3 คอลัมน์ (ซ้าย, กลาง, ขวา)
     */
    public LobbyScreen(ActionListener onStartGameAction, ActionListener onLeaveAction) {
        super();
        this.onStartGameAction = onStartGameAction;
        this.onLeaveAction = onLeaveAction;
        refreshPlayers();
    }

    // =====================================================================
    //  การอัปเดตข้อมูลห้องสด (Live room data)
    // =====================================================================

    // ตั้งค่าชื่อผู้เล่นเครื่องนี้
    public void setMyName(String name) {
        myName = name;
    }

    // กำหนดสถานะว่าเป็นโฮสต์หรือไม่
    public void setHost(boolean host) {
        isHost = host;
        refreshPlayers();
    }

    // กำหนดตัวจัดการเมื่อกดปุ่ม Ready
    public void setReadyAction(ActionListener action) {
        readyAction = action;
    }

    // กำหนดตัวจัดการเมื่อส่งข้อความแชต
    public void setChatAction(Consumer<String> action) {
        chatAction = action;
    }

    // ตรวจสอบว่าผู้เล่นเครื่องนี้พร้อมแล้วหรือไม่
    public boolean isReady() {
        Boolean value = readyStates.get(myName);
        if (value == null) {
            return false;
        }
        return value.booleanValue();
    }

    /**
     * อัปเดตรายชื่อผู้เล่น สถานะความพร้อม และชื่อโฮสต์จากข้อมูลล่าสุดของเซิร์ฟเวอร์
     */
    public void setPlayers(List<String> names, Map<String, Boolean> ready, String host) {
        playerNames.clear();
        readyStates.clear();
        playerNames.addAll(names);
        readyStates.putAll(ready);
        hostName = host;
        // MainApp เริ่มต้น hostName เป็นค่าว่างก่อนอ่านรายชื่อ
        // ถ้าไม่มีผู้เล่นก็จะส่งค่าว่างมาให้อยู่แล้ว จึงไม่ต้องเช็กซ้ำ
        refreshPlayers();
    }

    /**
     * รีเฟรชและสร้างคอมโพเนนต์แสดงผลใหม่ตามรายชื่อผู้เล่นล่าสุด
     */
    private void refreshPlayers() {
        int total = playerNames.size();

        // สร้างแถวรายชื่อผู้เล่นแต่ละคน
        renderPlayers(playerNames, readyStates, hostName, myName);

        // นับจำนวนผู้เล่นที่กดพร้อม
        int readyCount = 0;
        for (String name : playerNames) {
            if (readyStates.get(name).booleanValue()) {
                readyCount++;
            }
        }
        renderReadyProgress(readyCount, total);

        // ตรวจสอบว่าผู้เล่นคนอื่นทุกคนพร้อมแล้วหรือไม่
        boolean allOthersReady = total >= GameServer.MIN_PLAYERS;
        for (String name : playerNames) {
            if (!name.equals(myName)) {
                if (!readyStates.get(name).booleanValue()) {
                    allOthersReady = false;
                }
            }
        }
        boolean canStart = isHost && allOthersReady;

        // ปรับแต่งปุ่มเริ่มเกมตามสิทธิ์และสถานะ
        String startTooltip;
        if (!isHost) {
            startTooltip = "Only the host can start the game.";
        } else if (!allOthersReady) {
            startTooltip = "Every other player must press READY first.";
        } else {
            startTooltip = "All players are ready - start the round.";
        }
        renderStartButton(canStart, isHost, startTooltip);

        // สลับข้อความบนปุ่ม Ready
        renderReadyButton(isReady());

        setStatusText(total < GameServer.MIN_PLAYERS
                ? "Waiting for more players to join..."
                : "Get ready while the host prepares the round.");
    }

    // =====================================================================
    //  ระบบข้อความแชต (Chat)
    // =====================================================================

    /** เพิ่มข้อความใหม่ลงในกล่องแชต */
    public void addChatMessage(String sender, String text) {
        boolean isUser = sender != null && sender.equals(myName);
        boolean isSystem = "System".equalsIgnoreCase(sender);
        renderChatMessage(sender, text, isUser, isSystem);
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จากปุ่มและแชต (Event hooks)
    // =====================================================================

    /** ผู้เล่นกดปุ่มสลับสถานะพร้อม */
    @Override
    protected void onReadyClicked(ActionEvent event) {
        readyAction.actionPerformed(event);
    }

    /** โฮสต์กดปุ่มเริ่มเกม */
    @Override
    protected void onStartClicked(ActionEvent event) {
        onStartGameAction.actionPerformed(event);
    }

    /** ผู้เล่นกดปุ่มออกจากห้อง */
    @Override
    protected void onLeaveClicked(ActionEvent event) {
        onLeaveAction.actionPerformed(event);
    }

    /** ส่งข้อความแชตที่ผู้เล่นพิมพ์ */
    @Override
    protected void onChatSend(String text) {
        chatAction.accept(text);
    }

}
