import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.function.Consumer;

/** หน้าจอห้องพักคอย */
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

    /** สร้างหน้าจอ Lobby */
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

    /** อัปเดตข้อมูลห้อง */
    public void setPlayers(List<String> names, Map<String, Boolean> ready, String host) {
        playerNames.clear();
        readyStates.clear();
        playerNames.addAll(names);
        readyStates.putAll(ready);
        hostName = host;
        // hostName เริ่มเป็นค่าว่าง
        refreshPlayers();
    }

    /** รีเฟรชรายชื่อผู้เล่น */
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

        String statusText;
        if (total < GameServer.MIN_PLAYERS) {
            statusText = "Waiting for more players to join...";
        } else {
            statusText = "Get ready while the host prepares the round.";
        }
        setStatusText(statusText);
    }

    // =====================================================================
    //  ระบบข้อความแชต (Chat)
    // =====================================================================

    /** เพิ่มข้อความแชต */
    public void addChatMessage(String sender, String text) {
        boolean isUser = sender != null && sender.equals(myName);
        boolean isSystem = "System".equalsIgnoreCase(sender);
        renderChatMessage(sender, text, isUser, isSystem);
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จากปุ่มและแชต (Event hooks)
    // =====================================================================

    /** ปุ่ม Ready */
    @Override
    protected void onReadyClicked(ActionEvent event) {
        readyAction.actionPerformed(event);
    }

    /** ปุ่มเริ่มเกม */
    @Override
    protected void onStartClicked(ActionEvent event) {
        onStartGameAction.actionPerformed(event);
    }

    /** ปุ่มออกจากห้อง */
    @Override
    protected void onLeaveClicked(ActionEvent event) {
        onLeaveAction.actionPerformed(event);
    }

    /** ส่งข้อความแชต */
    @Override
    protected void onChatSend(String text) {
        chatAction.accept(text);
    }

}
