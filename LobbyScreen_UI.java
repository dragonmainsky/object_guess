import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.*;

/**
 * LobbyScreen_UI: ส่วนหน้าตาของหน้าจอห้องพักคอย
 * เก็บคอมโพเนนต์และเมธอดสร้าง/วาด UI ทั้งหมด โดยไม่ถือสถานะของห้อง
 */
public class LobbyScreen_UI extends JPanel {

    private static final int SIDE_WIDTH = 320;   // ความกว้างของคอลัมน์ซ้ายและขวา
    private static final int PREVIEW_ICON = 80;  // ขนาดรูปทรงในแถบพรีวิวตรงกลาง

    // รูปทรงตัวอย่าง 3 รูปที่แสดงบนการ์ดพรีวิวตรงกลาง (รูปที่ 3 คือสี่เหลี่ยมผืนผ้า)
    private static final String[] PREVIEW_NAMES = {"Triangle", "Circle", ShapeImages.NAMES[2]};

    // ===== คอมโพเนนต์ UI ที่ถูกอัปเดตตามสถานะห้อง =====
    protected JPanel playerRowsPanel;                 // พาเนลแสดงรายการผู้เล่น
    protected JLabel playersCountLabel;               // ป้ายแสดงจำนวนผู้เล่นในห้อง
    protected JLabel addressValueLabel;               // ป้ายแสดง IP ที่อยู่ของห้อง
    protected JLabel hostValueLabel;                  // ป้ายแสดงชื่อโฮสต์
    protected JLabel readyCountLabel;                 // ป้ายแสดงจำนวนคนที่พร้อม (เช่น "2 of 3")
    protected JPanel trackPanel;                      // แถบหลอดความพร้อมของผู้เล่น
    protected JLabel subHeaderStatusLabel;            // ป้ายข้อความสถานะย่อย
    protected JPanel chatListPanel;                   // พาเนลแสดงรายการข้อความแชต
    protected JTextField chatField;                   // ช่องพิมพ์ข้อความแชต
    protected JButton readyBtn;                       // ปุ่มกดพร้อม (READY)
    protected JButton startBtn;                       // ปุ่มเริ่มเกม (START GAME)

    /**
     * คอนสตรักเตอร์สร้างหน้าจอล็อบบี้ จัดวางแบบ 3 คอลัมน์ (ซ้าย, กลาง, ขวา)
     */
    public LobbyScreen_UI() {
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        JPanel mainContent = AppDialog.panel(new BorderLayout(14, 14));
        mainContent.setBorder(new EmptyBorder(18, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);

        // แบ่งออกเป็น 3 คอลัมน์: ซ้ายความกว้างคงที่, กลางยืดหยุ่น, ขวาความกว้างคงที่
        JPanel left = createLeftPanel();
        left.setPreferredSize(new Dimension(SIDE_WIDTH, 0));
        JPanel right = createRightPanel();
        right.setPreferredSize(new Dimension(SIDE_WIDTH, 0));

        JPanel columns = AppDialog.panel(new BorderLayout(16, 0));
        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);

        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
    }

    // =====================================================================
    //  การสร้างแต่ละส่วนของหน้าจอ (Sections)
    // =====================================================================

    /**
     * สร้างส่วนหัวด้านบน: แสดงชื่อล็อบบี้และคำอธิบายสถานะ
     */
    private JPanel createSubHeader() {
        subHeaderStatusLabel = AppDialog.label("Waiting for players to join...", AppDialog.SCREEN_SUB, AppDialog.TEXT_MUTED);
        JPanel titles = AppDialog.titleStack(2,
                AppDialog.label("GAME LOBBY", AppDialog.SUBTITLE, AppDialog.PRIMARY_BLUE),
                AppDialog.label("Room", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK),
                subHeaderStatusLabel);
        return AppDialog.pageHeader(titles, null, BorderLayout.EAST, 0, 0, 0, 0);
    }

    /**
     * คอลัมน์ฝั่งซ้าย: แสดงรายชื่อผู้เล่นและข้อมูลเกี่ยวกับเซิร์ฟเวอร์
     */
    private JPanel createLeftPanel() {
        JPanel playersCard = AppDialog.card(new BorderLayout(0, 12));
        playersCountLabel = AppDialog.label("Players in room: 0", AppDialog.SUBTITLE, AppDialog.TEXT_DARK);
        playersCard.add(playersCountLabel, BorderLayout.NORTH);

        playerRowsPanel = AppDialog.panel(new GridLayout(0, 1, 0, 8));
        JPanel playersBody = AppDialog.panel(new BorderLayout(0, 8));
        playersBody.add(playerRowsPanel, BorderLayout.NORTH);
        playersBody.add(createEmptySlot(), BorderLayout.CENTER);
        playersCard.add(playersBody, BorderLayout.CENTER);

        JPanel serverCard = AppDialog.card(new BorderLayout(0, 10));
        serverCard.add(AppDialog.label("Server information", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.NORTH);

        JPanel serverRows = AppDialog.panel(new GridLayout(0, 1, 0, 6));
        addressValueLabel = AppDialog.label("-", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        hostValueLabel = AppDialog.label("-", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        serverRows.add(createInfoRow("Address", addressValueLabel));
        serverRows.add(createInfoRow("Host", hostValueLabel));

        JPanel serverBody = AppDialog.panel(new BorderLayout(0, 12));
        serverBody.add(serverRows, BorderLayout.NORTH);
        serverCard.add(serverBody, BorderLayout.CENTER);

        // รวมการ์ดผู้เล่นและการ์ดข้อมูลเซิร์ฟเวอร์เข้าด้วยกัน
        JPanel left = AppDialog.panel(new BorderLayout(0, 12));
        left.add(playersCard, BorderLayout.NORTH);
        left.add(AppDialog.pin(serverCard, BorderLayout.NORTH), BorderLayout.CENTER);
        return left;
    }

    /**
     * คอลัมน์ตรงกลาง: แสดงรูปภาพพรีวิวตัวอย่าง หลอดแสดงความพร้อม และปุ่ม READY / START GAME
     */
    private JPanel createCenterPanel() {
        JPanel center = AppDialog.panel(new BorderLayout(0, 14));

        JPanel previewCard = AppDialog.card(new BorderLayout(0, 20));

        JPanel canvas = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 30));
        canvas.setBackground(AppDialog.BLUE_LIGHT_BG);
        canvas.setBorder(new LineBorder(AppDialog.BLUE_BORDER, 1));
        for (String name : PREVIEW_NAMES) {
            canvas.add(ShapeImages.shape(name, PREVIEW_ICON));
        }
        previewCard.add(canvas, BorderLayout.NORTH);

        JPanel headings = AppDialog.titleStack(4,
                AppDialog.label("Almost ready!", AppDialog.TITLE, AppDialog.TEXT_DARK, JLabel.CENTER),
                AppDialog.label("Every player must press READY", AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER),
                AppDialog.label("before the host can start the game.", AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER));

        // หลอดแสดงความพร้อม: แถวข้อความด้านบนและแท่งแสดงสถานะผู้เล่น
        JPanel progressHeader = AppDialog.panel(new BorderLayout());
        progressHeader.add(AppDialog.label("Players ready", AppDialog.SMALL_BOLD, AppDialog.TEXT_DARK), BorderLayout.WEST);
        readyCountLabel = AppDialog.label("0 of 0", AppDialog.SMALL_BOLD, AppDialog.PRIMARY_BLUE);
        progressHeader.add(readyCountLabel, BorderLayout.EAST);

        trackPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        trackPanel.setBackground(AppDialog.CARD_BORDER);
        trackPanel.setPreferredSize(new Dimension(0, 8));
        JPanel trackSlot = AppDialog.panel(new BorderLayout());
        trackSlot.add(trackPanel, BorderLayout.NORTH);

        JPanel progress = AppDialog.panel(new BorderLayout(0, 6));
        progress.add(progressHeader, BorderLayout.NORTH);
        progress.add(trackSlot, BorderLayout.CENTER);

        JPanel info = AppDialog.panel(new BorderLayout(0, 18));
        info.add(headings, BorderLayout.NORTH);
        info.add(progress, BorderLayout.CENTER);

        JPanel infoSlot = AppDialog.panel(new BorderLayout());
        infoSlot.add(info, BorderLayout.NORTH);
        previewCard.add(infoSlot, BorderLayout.CENTER);
        center.add(previewCard, BorderLayout.CENTER);

        // ปุ่ม READY สำหรับสลับสถานะพร้อม
        readyBtn = AppDialog.button("READY", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER, AppDialog.PRIMARY_PRESS,
                Color.WHITE, AppDialog.PRIMARY_BLUE, 12, 18);
        readyBtn.setPreferredSize(new Dimension(160, 44));
        readyBtn.addActionListener(event -> onReadyClicked(event));

        // ปุ่ม START GAME สำหรับโฮสต์เริ่มเกม
        startBtn = AppDialog.button("START GAME", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 12, 18);
        startBtn.setPreferredSize(new Dimension(160, 44));
        startBtn.setToolTipText("Only the host can start, once every other player is ready.");
        startBtn.addActionListener(event -> onStartClicked(event));

        JPanel buttonRow = AppDialog.panel(new GridLayout(1, 2, 12, 0));
        buttonRow.add(readyBtn);
        buttonRow.add(startBtn);
        center.add(buttonRow, BorderLayout.SOUTH);
        return center;
    }

    /**
     * คอลัมน์ฝั่งขวา: ระบบแชต ข้อมูลกติกาของรอบ และปุ่มออกจากห้อง
     */
    private JPanel createRightPanel() {
        JPanel right = AppDialog.panel(new BorderLayout(0, 12));

        JPanel chatCard = AppDialog.card(new BorderLayout(0, 10));
        chatCard.add(AppDialog.label("Chat", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.NORTH);

        chatListPanel = AppDialog.panel(new GridLayout(0, 1, 0, 6));
        JPanel chatSlot = AppDialog.panel(new BorderLayout());
        chatSlot.add(chatListPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(chatSlot);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppDialog.CARD_BG);
        scroll.setPreferredSize(new Dimension(220, 130));
        chatCard.add(scroll, BorderLayout.CENTER);

        chatField = makeTextField();
        JButton sendBtn = AppDialog.button("Send", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER, AppDialog.PRIMARY_PRESS,
                Color.WHITE, AppDialog.PRIMARY_BLUE, 8, 14);
        Runnable sendChat = new Runnable() {
            /** ตรวจข้อความและส่งแชตเมื่อผู้เล่นกด Send หรือกด Enter */
            @Override
            public void run() {
                String text = chatField.getText().trim();
                if (text.isEmpty()) {
                    return;
                }
                onChatSend(text);
                chatField.setText("");
            }
        };
        sendBtn.addActionListener(event -> sendChat.run());
        chatField.addActionListener(event -> sendChat.run());

        JPanel inputRow = AppDialog.panel(new BorderLayout(6, 0));
        inputRow.add(chatField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        chatCard.add(inputRow, BorderLayout.SOUTH);

        // การตั้งค่าของแต่ละรอบ อ่านมาจากค่าคงที่ของ GameServer
        JPanel settingsCard = AppDialog.card(new BorderLayout(0, 10));
        settingsCard.add(AppDialog.label("Round settings", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.NORTH);
        JPanel settingRows = AppDialog.panel(new GridLayout(0, 1, 0, 6));
        settingRows.add(createInfoRow("Target", GameServer.MIN_SHAPES + "-" + GameServer.MAX_SHAPES + " shapes"));
        settingRows.add(createInfoRow("Round time", GameServer.ROUND_SECONDS + " sec"));
        settingRows.add(createInfoRow("Rounds", "players x " + GameServer.ROUNDS_PER_PLAYER));
        settingsCard.add(settingRows, BorderLayout.CENTER);

        JButton leaveBtn = AppDialog.button("LEAVE ROOM", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 10, 18);
        leaveBtn.addActionListener(event -> onLeaveClicked(event));

        JPanel rest = AppDialog.panel(new BorderLayout(0, 12));
        rest.add(settingsCard, BorderLayout.NORTH);
        rest.add(AppDialog.pin(leaveBtn, BorderLayout.NORTH), BorderLayout.CENTER);

        right.add(chatCard, BorderLayout.NORTH);
        right.add(rest, BorderLayout.CENTER);
        return right;
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จากปุ่ม (Button and chat hooks)
    // =====================================================================

    /** เรียกเมื่อผู้เล่นกดปุ่ม READY */
    protected void onReadyClicked(ActionEvent event) {
    }

    /** เรียกเมื่อผู้เล่นกดปุ่ม START GAME */
    protected void onStartClicked(ActionEvent event) {
    }

    /** เรียกเมื่อผู้เล่นกดปุ่ม LEAVE ROOM */
    protected void onLeaveClicked(ActionEvent event) {
    }

    /** เรียกเมื่อผู้เล่นส่งข้อความแชต */
    protected void onChatSend(String text) {
    }

    // =====================================================================
    //  การวาดข้อมูลห้องสด (Live room rendering)
    // =====================================================================

    /**
     * อัปเดตรายชื่อผู้เล่นและชื่อโฮสต์จากข้อมูลล่าสุดของเซิร์ฟเวอร์
     */
    protected void renderPlayers(List<String> playerNames, Map<String, Boolean> readyStates,
                                 String hostName, String myName) {
        int total = playerNames.size();

        // สร้างแถวรายชื่อผู้เล่นแต่ละคน
        playerRowsPanel.removeAll();
        for (int i = 0; i < total; i++) {
            String name = playerNames.get(i);
            Boolean readyValue = readyStates.get(name);
            boolean ready = readyValue != null && readyValue.booleanValue();
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
            String display;
            if (myName.equals(name)) {
                display = name + " (you)";
            } else {
                display = name;
            }
            JPanel row = AppDialog.panel(new BorderLayout(8, 0));
            row.add(AppDialog.playerWho(AppDialog.initialOf(name), AppDialog.avatarColor(i), display, 26, 8, null),
                    BorderLayout.WEST);
            row.add(AppDialog.badge(status, bg, fg, border, 4, 10), BorderLayout.EAST);
            playerRowsPanel.add(row);
        }
        playerRowsPanel.revalidate();
        playerRowsPanel.repaint();

        playersCountLabel.setText("Players in room: " + total);
        if (hostName.isEmpty()) {
            hostValueLabel.setText("-");
        } else {
            hostValueLabel.setText(hostName);
        }
    }

    /**
     * อัปเดตจำนวนคนที่พร้อมและหลอดแสดงความพร้อม
     */
    protected void renderReadyProgress(int readyCount, int total) {
        readyCountLabel.setText(readyCount + " of " + total);

        // สร้างช่องในหลอดความพร้อมตามจำนวนผู้เล่น
        trackPanel.removeAll();
        trackPanel.setLayout(new GridLayout(1, Math.max(1, total), 0, 0));
        for (int i = 0; i < Math.max(1, total); i++) {
            JPanel segment = new JPanel();
            if (i < readyCount) {
                segment.setBackground(AppDialog.PRIMARY_BLUE);
            } else {
                segment.setBackground(AppDialog.CARD_BORDER);
            }
            trackPanel.add(segment);
        }
        trackPanel.revalidate();
        trackPanel.repaint();
    }

    /**
     * ปรับแต่งปุ่มเริ่มเกมตามสิทธิ์และสถานะ
     */
    protected void renderStartButton(boolean canStart, boolean isHost, String tooltip) {
        startBtn.setEnabled(canStart);
        if (isHost) {
            startBtn.setText("START GAME");
        } else {
            startBtn.setText("HOST STARTS");
        }
        if (canStart) {
            startBtn.setBackground(Color.WHITE);
            startBtn.setForeground(AppDialog.TEXT_DARK);
        } else {
            startBtn.setBackground(AppDialog.CARD_BORDER);
            startBtn.setForeground(AppDialog.TEXT_LIGHT);
        }
        startBtn.setToolTipText(tooltip);
    }

    /**
     * สลับข้อความบนปุ่ม Ready
     */
    protected void renderReadyButton(boolean ready) {
        if (ready) {
            readyBtn.setText("CANCEL READY");
        } else {
            readyBtn.setText("READY");
        }
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
    protected void renderChatMessage(String sender, String text, boolean isUser, boolean isSystem) {
        Color senderColor;
        if (isUser) {
            senderColor = AppDialog.PRIMARY_BLUE;
        } else if (isSystem) {
            senderColor = AppDialog.WARNING_TEXT;
        } else {
            senderColor = AppDialog.TEXT_MUTED;
        }

        JPanel message = AppDialog.panel(new GridLayout(2, 1, 0, 0));
        message.add(AppDialog.label(sender, AppDialog.SMALL_BOLD, senderColor));
        message.add(AppDialog.label(text, AppDialog.SMALL, AppDialog.TEXT_DARK));
        chatListPanel.add(message);
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    // =====================================================================
    //  เมธอดปรับสถานะที่เรียกโดย MainApp
    // =====================================================================

    /** แสดงที่อยู่ IP และพอร์ตของโฮสต์ */
    public void setAddress(String address) {
        if (address == null || address.isEmpty()) {
            addressValueLabel.setText("-");
        } else {
            addressValueLabel.setText(address);
        }
    }

    // อัปเดตข้อความสถานะย่อย
    public void setStatusText(String text) {
        subHeaderStatusLabel.setText(text);
    }

    // =====================================================================
    //  คอมโพเนนต์ย่อยและตัวช่วยสร้าง (Rows and Small Factories)
    // =====================================================================

    /** ช่องสำหรับแสดงสถานะรอผู้เล่นคนถัดไป */
    private JPanel createEmptySlot() {
        JPanel slot = new JPanel(new BorderLayout());
        slot.setBackground(AppDialog.BG_LIGHT);
        slot.setBorder(new CompoundBorder(new LineBorder(AppDialog.SLOT_BORDER, 1), new EmptyBorder(8, 8, 8, 8)));
        slot.add(AppDialog.label("Waiting for player...", AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER),
                BorderLayout.CENTER);
        return slot;
    }

    /** สร้างแถวข้อมูล โดยสร้าง JLabel ของค่าจากข้อความที่กำหนด */
    private JPanel createInfoRow(String label, String value) {
        return createInfoRow(label, AppDialog.label(value, AppDialog.BODY_BOLD, AppDialog.TEXT_DARK));
    }

    /** สร้างแถวข้อมูลโดยใช้ JLabel ที่เตรียมไว้แล้ว */
    private JPanel createInfoRow(String label, JLabel valueLabel) {
        JPanel row = AppDialog.panel(new BorderLayout());
        row.add(AppDialog.label(label, AppDialog.BODY, AppDialog.TEXT_MUTED), BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    /** สร้างช่องกรอกข้อความแชต */
    private static JTextField makeTextField() {
        JTextField field = new JTextField("");
        field.setBackground(Color.WHITE);
        field.setForeground(AppDialog.TEXT_DARK);
        field.setFont(AppDialog.BODY);
        field.setBorder(new CompoundBorder(new LineBorder(AppDialog.CARD_BORDER, 1), new EmptyBorder(8, 12, 8, 12)));
        return field;
    }
}
