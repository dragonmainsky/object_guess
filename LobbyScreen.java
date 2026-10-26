import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.*;

/**
 * LobbyScreen: หน้าจอห้องพักคอย (หน้าจอที่ 2)
 * แสดงรายชื่อผู้เล่นที่อยู่ในห้อง สถานะความพร้อม (Ready) ข้อมูลการเชื่อมต่อ ระบบแชต และปุ่มเริ่มเกม
 * ข้อมูลทั้งหมดบนหน้าจอนี้จะถูกส่งมาจาก MainApp ตามข้อมูลที่ได้รับจาก GameServer
 */
public class LobbyScreen extends JPanel {

    private static final int SIDE_WIDTH = 320;   // ความกว้างของคอลัมน์ซ้ายและขวา
    private static final int PREVIEW_ICON = 80;  // ขนาดรูปทรงในแถบพรีวิวตรงกลาง

    // รูปทรงตัวอย่าง 3 รูปที่แสดงบนการ์ดพรีวิวตรงกลาง (รูปที่ 3 คือสี่เหลี่ยมผืนผ้า)
    private static final String[] PREVIEW_NAMES = {"Triangle", "Circle", ShapeImages.NAMES[2]};

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

    // ===== คอมโพเนนต์ UI ที่ถูกอัปเดตตามสถานะห้อง =====
    private JPanel playerRowsPanel;                 // พาเนลแสดงรายการผู้เล่น
    private JLabel playersCountLabel;               // ป้ายแสดงจำนวนผู้เล่นในห้อง
    private JLabel addressValueLabel;               // ป้ายแสดง IP ที่อยู่ของห้อง
    private JLabel hostValueLabel;                  // ป้ายแสดงชื่อโฮสต์
    private JLabel readyCountLabel;                 // ป้ายแสดงจำนวนคนที่พร้อม (เช่น "2 of 3")
    private JPanel trackPanel;                      // แถบหลอดความพร้อมของผู้เล่น
    private JLabel subHeaderStatusLabel;            // ป้ายข้อความสถานะย่อย
    private JPanel chatListPanel;                   // พาเนลแสดงรายการข้อความแชต
    private JTextField chatField;                   // ช่องพิมพ์ข้อความแชต
    private JButton readyBtn;                       // ปุ่มกดพร้อม (READY)
    private JButton startBtn;                       // ปุ่มเริ่มเกม (START GAME)

    /**
     * คอนสตรักเตอร์สร้างหน้าจอล็อบบี้ จัดวางแบบ 3 คอลัมน์ (ซ้าย, กลาง, ขวา)
     */
    public LobbyScreen(ActionListener onStartGameAction, ActionListener onLeaveAction) {
        this.onStartGameAction = onStartGameAction;
        this.onLeaveAction = onLeaveAction;
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        var mainContent = AppDialog.panel(new BorderLayout(14, 14));
        mainContent.setBorder(new EmptyBorder(18, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);

        // แบ่งออกเป็น 3 คอลัมน์: ซ้ายความกว้างคงที่, กลางยืดหยุ่น, ขวาความกว้างคงที่
        var left = createLeftPanel();
        left.setPreferredSize(new Dimension(SIDE_WIDTH, 0));
        var right = createRightPanel();
        right.setPreferredSize(new Dimension(SIDE_WIDTH, 0));

        var columns = AppDialog.panel(new BorderLayout(16, 0));
        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);

        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
        refreshPlayers();
    }

    // =====================================================================
    //  การสร้างแต่ละส่วนของหน้าจอ (Sections)
    // =====================================================================

    /**
     * สร้างส่วนหัวด้านบน: แสดงชื่อล็อบบี้และคำอธิบายสถานะ
     */
    private JPanel createSubHeader() {
        subHeaderStatusLabel = AppDialog.label("Waiting for players to join...", AppDialog.SCREEN_SUB, AppDialog.TEXT_MUTED);
        var titles = AppDialog.titleStack(2,
                AppDialog.label("GAME LOBBY", AppDialog.SUBTITLE, AppDialog.PRIMARY_BLUE),
                AppDialog.label("Room", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK),
                subHeaderStatusLabel);
        return AppDialog.pageHeader(titles, null, BorderLayout.EAST, 0, 0, 0, 0);
    }

    /**
     * คอลัมน์ฝั่งซ้าย: แสดงรายชื่อผู้เล่นและข้อมูลเกี่ยวกับเซิร์ฟเวอร์
     */
    private JPanel createLeftPanel() {
        var playersCard = AppDialog.card(new BorderLayout(0, 12));
        playersCountLabel = AppDialog.label("Players in room: 0", AppDialog.SUBTITLE, AppDialog.TEXT_DARK);
        playersCard.add(playersCountLabel, BorderLayout.NORTH);

        playerRowsPanel = AppDialog.panel(new GridLayout(0, 1, 0, 8));
        var playersBody = AppDialog.panel(new BorderLayout(0, 8));
        playersBody.add(playerRowsPanel, BorderLayout.NORTH);
        playersBody.add(createEmptySlot(), BorderLayout.CENTER);
        playersCard.add(playersBody, BorderLayout.CENTER);

        var serverCard = AppDialog.card(new BorderLayout(0, 10));
        serverCard.add(AppDialog.label("Server information", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.NORTH);

        var serverRows = AppDialog.panel(new GridLayout(0, 1, 0, 6));
        addressValueLabel = AppDialog.label("-", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        hostValueLabel = AppDialog.label("-", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        serverRows.add(createInfoRow("Address", addressValueLabel));
        serverRows.add(createInfoRow("Host", hostValueLabel));

        var serverBody = AppDialog.panel(new BorderLayout(0, 12));
        serverBody.add(serverRows, BorderLayout.NORTH);
        serverCard.add(serverBody, BorderLayout.CENTER);

        // รวมการ์ดผู้เล่นและการ์ดข้อมูลเซิร์ฟเวอร์เข้าด้วยกัน
        var left = AppDialog.panel(new BorderLayout(0, 12));
        left.add(playersCard, BorderLayout.NORTH);
        left.add(AppDialog.pin(serverCard, BorderLayout.NORTH), BorderLayout.CENTER);
        return left;
    }

    /**
     * คอลัมน์ตรงกลาง: แสดงรูปภาพพรีวิวตัวอย่าง หลอดแสดงความพร้อม และปุ่ม READY / START GAME
     */
    private JPanel createCenterPanel() {
        var center = AppDialog.panel(new BorderLayout(0, 14));

        var previewCard = AppDialog.card(new BorderLayout(0, 20));

        var canvas = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 30));
        canvas.setBackground(AppDialog.BLUE_LIGHT_BG);
        canvas.setBorder(new LineBorder(AppDialog.BLUE_BORDER, 1));
        for (String name : PREVIEW_NAMES) {
            canvas.add(ShapeImages.shape(name, PREVIEW_ICON));
        }
        previewCard.add(canvas, BorderLayout.NORTH);

        var headings = AppDialog.titleStack(4,
                AppDialog.label("Almost ready!", AppDialog.TITLE, AppDialog.TEXT_DARK, JLabel.CENTER),
                AppDialog.label("Every player must press READY", AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER),
                AppDialog.label("before the host can start the game.", AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER));

        // หลอดแสดงความพร้อม: แถวข้อความด้านบนและแท่งแสดงสถานะผู้เล่น
        var progressHeader = AppDialog.panel(new BorderLayout());
        progressHeader.add(AppDialog.label("Players ready", AppDialog.SMALL_BOLD, AppDialog.TEXT_DARK), BorderLayout.WEST);
        readyCountLabel = AppDialog.label("0 of 0", AppDialog.SMALL_BOLD, AppDialog.PRIMARY_BLUE);
        progressHeader.add(readyCountLabel, BorderLayout.EAST);

        trackPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        trackPanel.setBackground(AppDialog.CARD_BORDER);
        trackPanel.setPreferredSize(new Dimension(0, 8));
        var trackSlot = AppDialog.panel(new BorderLayout());
        trackSlot.add(trackPanel, BorderLayout.NORTH);

        var progress = AppDialog.panel(new BorderLayout(0, 6));
        progress.add(progressHeader, BorderLayout.NORTH);
        progress.add(trackSlot, BorderLayout.CENTER);

        var info = AppDialog.panel(new BorderLayout(0, 18));
        info.add(headings, BorderLayout.NORTH);
        info.add(progress, BorderLayout.CENTER);

        var infoSlot = AppDialog.panel(new BorderLayout());
        infoSlot.add(info, BorderLayout.NORTH);
        previewCard.add(infoSlot, BorderLayout.CENTER);
        center.add(previewCard, BorderLayout.CENTER);

        // ปุ่ม READY สำหรับสลับสถานะพร้อม
        readyBtn = AppDialog.button("READY", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER, AppDialog.PRIMARY_PRESS,
                Color.WHITE, AppDialog.PRIMARY_BLUE, 12, 18);
        readyBtn.setPreferredSize(new Dimension(160, 44));
        readyBtn.addActionListener(event -> {
            if (readyAction != null) {
                readyAction.actionPerformed(event);
            }
        });

        // ปุ่ม START GAME สำหรับโฮสต์เริ่มเกม
        startBtn = AppDialog.button("START GAME", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 12, 18);
        startBtn.setPreferredSize(new Dimension(160, 44));
        startBtn.setToolTipText("Only the host can start, once every other player is ready.");
        startBtn.addActionListener(onStartGameAction);

        var buttonRow = AppDialog.panel(new GridLayout(1, 2, 12, 0));
        buttonRow.add(readyBtn);
        buttonRow.add(startBtn);
        center.add(buttonRow, BorderLayout.SOUTH);
        return center;
    }

    /**
     * คอลัมน์ฝั่งขวา: ระบบแชต ข้อมูลกติกาของรอบ และปุ่มออกจากห้อง
     */
    private JPanel createRightPanel() {
        var right = AppDialog.panel(new BorderLayout(0, 12));

        var chatCard = AppDialog.card(new BorderLayout(0, 10));
        chatCard.add(AppDialog.label("Chat", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.NORTH);

        chatListPanel = AppDialog.panel(new GridLayout(0, 1, 0, 6));
        var chatSlot = AppDialog.panel(new BorderLayout());
        chatSlot.add(chatListPanel, BorderLayout.NORTH);

        var scroll = new JScrollPane(chatSlot);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppDialog.CARD_BG);
        scroll.setPreferredSize(new Dimension(220, 130));
        chatCard.add(scroll, BorderLayout.CENTER);

        chatField = makeTextField();
        var sendBtn = AppDialog.button("Send", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER, AppDialog.PRIMARY_PRESS,
                Color.WHITE, AppDialog.PRIMARY_BLUE, 8, 14);
        ActionListener sendChat = event -> {
            String text = chatField.getText().trim();
            if (text.isEmpty()) {
                return;
            }
            if (chatAction != null) {
                chatAction.accept(text);
            }
            chatField.setText("");
        };
        sendBtn.addActionListener(sendChat);
        chatField.addActionListener(sendChat);

        var inputRow = AppDialog.panel(new BorderLayout(6, 0));
        inputRow.add(chatField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        chatCard.add(inputRow, BorderLayout.SOUTH);

        // การตั้งค่าของแต่ละรอบ อ่านมาจากค่าคงที่ของ GameServer
        var settingsCard = AppDialog.card(new BorderLayout(0, 10));
        settingsCard.add(AppDialog.label("Round settings", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.NORTH);
        var settingRows = AppDialog.panel(new GridLayout(0, 1, 0, 6));
        settingRows.add(createInfoRow("Target", GameServer.MIN_SHAPES + "-" + GameServer.MAX_SHAPES + " shapes"));
        settingRows.add(createInfoRow("Round time", GameServer.ROUND_SECONDS + " sec"));
        settingRows.add(createInfoRow("Rounds", "players x " + GameServer.ROUNDS_PER_PLAYER));
        settingsCard.add(settingRows, BorderLayout.CENTER);

        var leaveBtn = AppDialog.button("LEAVE ROOM", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 10, 18);
        leaveBtn.addActionListener(onLeaveAction);

        var rest = AppDialog.panel(new BorderLayout(0, 12));
        rest.add(settingsCard, BorderLayout.NORTH);
        rest.add(AppDialog.pin(leaveBtn, BorderLayout.NORTH), BorderLayout.CENTER);

        right.add(chatCard, BorderLayout.NORTH);
        right.add(rest, BorderLayout.CENTER);
        return right;
    }

    // =====================================================================
    //  การอัปเดตข้อมูลห้องสด (Live room data)
    // =====================================================================

    // ตั้งค่าชื่อผู้เล่นเครื่องนี้
    public void setMyName(String name) {
        myName = (name == null) ? "" : name;
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
        return Boolean.TRUE.equals(readyStates.get(myName));
    }

    /** แสดงที่อยู่ IP และพอร์ตของโฮสต์ */
    public void setAddress(String address) {
        addressValueLabel.setText((address == null || address.isEmpty()) ? "-" : address);
    }

    // อัปเดตข้อความสถานะย่อย
    public void setStatusText(String text) {
        subHeaderStatusLabel.setText(text);
    }

    /**
     * อัปเดตรายชื่อผู้เล่น สถานะความพร้อม และชื่อโฮสต์จากข้อมูลล่าสุดของเซิร์ฟเวอร์
     */
    public void setPlayers(List<String> names, Map<String, Boolean> ready, String host) {
        playerNames.clear();
        readyStates.clear();
        if (names != null) {
            playerNames.addAll(names);
        }
        if (ready != null) {
            readyStates.putAll(ready);
        }
        hostName = (host == null) ? "" : host;
        if (playerNames.isEmpty()) {
            hostName = "";
        }
        refreshPlayers();
    }

    /**
     * รีเฟรชและสร้างคอมโพเนนต์แสดงผลใหม่ตามรายชื่อผู้เล่นล่าสุด
     */
    private void refreshPlayers() {
        int total = playerNames.size();

        // สร้างแถวรายชื่อผู้เล่นแต่ละคน
        playerRowsPanel.removeAll();
        for (int i = 0; i < total; i++) {
            String name = playerNames.get(i);
            boolean ready = Boolean.TRUE.equals(readyStates.get(name));
            boolean rowIsHost = name.equals(hostName);
            String status;
            Color bg;
            Color fg;
            Color border;
            if (rowIsHost) {
                status = "Host";
                bg = AppDialog.BLUE_LIGHT_BG;
                fg = AppDialog.PRIMARY_BLUE;
                border = AppDialog.BLUE_BORDER;
            } else if (ready) {
                status = "Ready";
                bg = AppDialog.SUCCESS_BG;
                fg = AppDialog.SUCCESS_TEXT;
                border = AppDialog.SUCCESS_BORDER;
            } else {
                status = "Waiting";
                bg = AppDialog.WARNING_BG;
                fg = AppDialog.WARNING_TEXT;
                border = AppDialog.WARNING_BORDER;
            }
            String display = myName.equals(name) ? name + " (you)" : name;
            var row = AppDialog.panel(new BorderLayout(8, 0));
            row.add(AppDialog.playerWho(AppDialog.initialOf(name), AppDialog.avatarColor(i), display, 26, 8, null),
                    BorderLayout.WEST);
            row.add(AppDialog.badge(status, bg, fg, border, 4, 10), BorderLayout.EAST);
            playerRowsPanel.add(row);
        }
        playerRowsPanel.revalidate();
        playerRowsPanel.repaint();

        playersCountLabel.setText("Players in room: " + total);
        hostValueLabel.setText(hostName.isEmpty() ? "-" : hostName);

        // นับจำนวนผู้เล่นที่กดพร้อม
        int readyCount = 0;
        for (String name : playerNames) {
            if (Boolean.TRUE.equals(readyStates.get(name))) {
                readyCount++;
            }
        }
        readyCountLabel.setText(readyCount + " of " + total);

        // สร้างช่องในหลอดความพร้อมตามจำนวนผู้เล่น
        trackPanel.removeAll();
        trackPanel.setLayout(new GridLayout(1, Math.max(1, total), 0, 0));
        for (int i = 0; i < Math.max(1, total); i++) {
            var segment = new JPanel();
            segment.setBackground(i < readyCount ? AppDialog.PRIMARY_BLUE : AppDialog.CARD_BORDER);
            trackPanel.add(segment);
        }
        trackPanel.revalidate();
        trackPanel.repaint();

        // ตรวจสอบว่าผู้เล่นคนอื่นทุกคนพร้อมแล้วหรือไม่
        boolean allOthersReady = total >= GameServer.MIN_PLAYERS;
        for (String name : playerNames) {
            if (!name.equals(myName) && !Boolean.TRUE.equals(readyStates.get(name))) {
                allOthersReady = false;
            }
        }
        boolean canStart = isHost && allOthersReady;

        // ปรับแต่งปุ่มเริ่มเกมตามสิทธิ์และสถานะ
        startBtn.setEnabled(canStart);
        startBtn.setText(isHost ? "START GAME" : "HOST STARTS");
        startBtn.setBackground(canStart ? Color.WHITE : AppDialog.CARD_BORDER);
        startBtn.setForeground(canStart ? AppDialog.TEXT_DARK : AppDialog.TEXT_LIGHT);
        if (!isHost) {
            startBtn.setToolTipText("Only the host can start the game.");
        } else if (!allOthersReady) {
            startBtn.setToolTipText("Every other player must press READY first.");
        } else {
            startBtn.setToolTipText("All players are ready - start the round.");
        }

        // สลับข้อความบนปุ่ม Ready
        readyBtn.setText(isReady() ? "CANCEL READY" : "READY");

        setStatusText(total < GameServer.MIN_PLAYERS
                ? "Waiting for more players to join..."
                : "Get ready while the host prepares the round.");
    }

    // =====================================================================
    //  ระบบข้อความแชต (Chat)
    // =====================================================================

    /** ล้างประวัติข้อความแชตทั้งหมด */
    public void clearChat() {
        chatListPanel.removeAll();
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    /** เพิ่มข้อความใหม่ลงในกล่องแชต */
    public void addChatMessage(String sender, String text) {
        boolean isUser = sender != null && sender.equals(myName);
        boolean isSystem = "System".equalsIgnoreCase(sender);
        Color senderColor = isUser ? AppDialog.PRIMARY_BLUE : (isSystem ? AppDialog.WARNING_TEXT : AppDialog.TEXT_MUTED);

        var message = AppDialog.panel(new GridLayout(2, 1, 0, 0));
        message.add(AppDialog.label(sender, AppDialog.SMALL_BOLD, senderColor));
        message.add(AppDialog.label(text, AppDialog.SMALL, AppDialog.TEXT_DARK));
        chatListPanel.add(message);
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    // =====================================================================
    //  คอมโพเนนต์ย่อยและตัวช่วยสร้าง (Rows and Small Factories)
    // =====================================================================

    /** ช่องสำหรับแสดงสถานะรอผู้เล่นคนถัดไป */
    private JPanel createEmptySlot() {
        var slot = new JPanel(new BorderLayout());
        slot.setBackground(AppDialog.BG_LIGHT);
        slot.setBorder(new CompoundBorder(new LineBorder(AppDialog.SLOT_BORDER, 1), new EmptyBorder(8, 8, 8, 8)));
        slot.add(AppDialog.label("Waiting for player...", AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER),
                BorderLayout.CENTER);
        return slot;
    }

    private JPanel createInfoRow(String label, String value) {
        return createInfoRow(label, AppDialog.label(value, AppDialog.BODY_BOLD, AppDialog.TEXT_DARK));
    }

    private JPanel createInfoRow(String label, JLabel valueLabel) {
        var row = AppDialog.panel(new BorderLayout());
        row.add(AppDialog.label(label, AppDialog.BODY, AppDialog.TEXT_MUTED), BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    /** สร้างช่องกรอกข้อความแชต */
    private static JTextField makeTextField() {
        var field = new JTextField("");
        field.setBackground(Color.WHITE);
        field.setForeground(AppDialog.TEXT_DARK);
        field.setFont(AppDialog.BODY);
        field.setBorder(new CompoundBorder(new LineBorder(AppDialog.CARD_BORDER, 1), new EmptyBorder(8, 12, 8, 12)));
        return field;
    }
}
