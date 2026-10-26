import java.awt.*;
import java.awt.event.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/**
 * JoinScreen_UI: ส่วนหน้าตาของหน้าจอสร้าง/เข้าร่วมห้อง
 * เก็บคอมโพเนนต์และเมธอดสร้าง UI ทั้งหมด โดยไม่ถือสถานะของเกม
 */
public class JoinScreen_UI extends JPanel {

    // สีและค่าคงที่เฉพาะของหน้าจอนี้ (ส่วนอื่นดึงมาจาก AppDialog)
    private static final Color BG_LIGHT = new Color(244, 246, 250), TEXT_DARK = new Color(17, 24, 39);
    private static final Color TEXT_MUTED = new Color(107, 114, 128), DANGER_HOVER = new Color(220, 38, 38);
    private static final Color WARNING_BG = new Color(255, 247, 230), WARNING_TEXT = new Color(146, 84, 0);
    private static final Color WARNING_BORDER = new Color(250, 219, 150);

    // พอร์ตเริ่มต้นที่ใช้เชื่อมต่อ
    protected static final int DEFAULT_PORT = GameServer.DEFAULT_PORT;

    protected JTextField nameField;         // ช่องกรอกชื่อผู้เล่น
    protected JTextField ipField;           // ช่องกรอก IP เซิร์ฟเวอร์
    protected JTextField portField;         // ช่องกรอกพอร์ต
    protected JButton createBtn;            // ปุ่มสร้างห้อง (Host)
    protected JButton joinBtn;              // ปุ่มเข้าร่วมห้อง (Join)
    protected JButton exitBtn;              // ปุ่มออกจากเกม
    protected JLabel hostInfoLabel;         // ป้ายแสดง IP เครื่องนี้สำหรับบอกเพื่อน
    protected JLabel statusLabel;           // ข้อความแสดงสถานะในแถบสถานะ
    protected JLabel statusTagLabel;        // ป้ายกำกับคำว่า "STATUS"
    protected JPanel statusBanner;          // แถบกล่องแสดงสถานะ

    /**
     * คอนสตรักเตอร์สำหรับสร้างหน้าจอ JoinScreen แบ่งการจัดวางออกเป็น 2 ฝั่ง (ซ้าย-ขวา)
     */
    public JoinScreen_UI() {
        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        // แบ่งครึ่งหน้าจอ: ฝั่งซ้ายคือข้อมูลแบรนด์/โลโก้ ฝั่งขวาคือแบบฟอร์มการเชื่อมต่อ
        var split = new JPanel(new GridLayout(1, 2));
        split.add(buildLeftPanel());
        split.add(buildRightPanel());
        add(split, BorderLayout.CENTER);
    }

    /**
     * สร้างส่วนแสดงผลฝั่งซ้าย: IP แนะนำของเครื่องนี้, โลโก้เกม และชื่อเกม
     */
    private JPanel buildLeftPanel() {
        var brand = new JPanel(new BorderLayout());
        brand.setBackground(BG_LIGHT);
        brand.setBorder(new EmptyBorder(30, 40, 40, 40));

        hostInfoLabel = AppDialog.label("", AppDialog.SMALL_12, TEXT_MUTED);
        var header = AppDialog.group(FlowLayout.LEFT, 0, 0, hostInfoLabel);
        brand.add(header, BorderLayout.NORTH);

        // จัดกลุ่มโลโก้และชื่อเกมให้อยู่ตรงกลางตามแนวตั้ง
        var center = AppDialog.panel(new GridLayout(4, 1, 0, 8));
        var title = AppDialog.label("<html>Guess What<br>It Looks Like?</html>", AppDialog.f(Font.BOLD, 36), TEXT_DARK);
        center.add(AppDialog.panel());
        center.add(AppDialog.group(FlowLayout.LEFT, 0, 0, ShapeImages.shape("logo", 100)));
        center.add(AppDialog.group(FlowLayout.LEFT, 0, 0, title));
        center.add(AppDialog.panel());
        brand.add(center, BorderLayout.CENTER);

        return brand;
    }

    /**
     * สร้างส่วนแสดงผลฝั่งขวา: แบบฟอร์มกรอกชื่อผู้เล่น, IP เซิร์ฟเวอร์, พอร์ต และปุ่มกด
     */
    private JPanel buildRightPanel() {
        var wrapper = new JPanel(new GridLayout(1, 1));
        wrapper.setBackground(BG_LIGHT);
        wrapper.setBorder(new EmptyBorder(40, 60, 40, 60));

        var form = AppDialog.panel(new BorderLayout());

        var header = AppDialog.titleStack(4,
                AppDialog.label("HOST OR JOIN", AppDialog.SMALL_BOLD_12, AppDialog.PRIMARY_BLUE),
                AppDialog.label("Find your party", AppDialog.TITLE_LARGE, TEXT_DARK),
                AppDialog.label("<html>Host a room on this machine, or join a friend on the same Wi-Fi.</html>",
                        AppDialog.PLAIN_14, TEXT_MUTED));
        header.setBorder(new EmptyBorder(0, 0, 24, 0));
        form.add(header, BorderLayout.NORTH);

        // รายการช่องกรอกข้อมูล 3 ช่อง: ชื่อ, IP เซิร์ฟเวอร์, พอร์ต
        String[] labels = {"Name", "Server IP (host)", "Port"};
        String[] defaults = {"", "", String.valueOf(DEFAULT_PORT)};
        var inputs = AppDialog.panel(new GridLayout(labels.length, 1, 0, 16));
        var fields = new JTextField[labels.length];
        for (int i = 0; i < labels.length; i++) {
            var label = AppDialog.label(labels[i], AppDialog.SUBTITLE, TEXT_DARK);
            label.setVerticalAlignment(JLabel.BOTTOM);
            fields[i] = makeField(defaults[i]);
            var box = framed(fields[i], Color.WHITE, AppDialog.CARD_BORDER, 0, 12, 0, 12);
            box.setPreferredSize(new Dimension(100, 42));
            var block = AppDialog.panel(new GridLayout(2, 1, 0, 6));
            block.add(label);
            block.add(box);
            inputs.add(block);
        }
        nameField = fields[0];
        ipField = fields[1];
        portField = fields[2];
        form.add(inputs, BorderLayout.CENTER);

        // แถบแสดงสถานะพร้อมปุ่มการทำงาน 3 ปุ่ม
        statusTagLabel = AppDialog.label("STATUS", AppDialog.SMALL_BOLD_12, WARNING_TEXT);
        statusLabel = AppDialog.label("Ready. Host a game or enter a host address.", AppDialog.SMALL_BOLD_12, WARNING_TEXT);
        var bannerContent = AppDialog.panel(new BorderLayout());
        bannerContent.add(statusTagLabel, BorderLayout.WEST);
        bannerContent.add(statusLabel, BorderLayout.EAST);
        statusBanner = framed(bannerContent, WARNING_BG, WARNING_BORDER, 6, 12, 6, 12);
        statusBanner.setPreferredSize(new Dimension(100, 38));

        createBtn = makeButton("CREATE GAME (HOST)", AppDialog.PRIMARY_BLUE, Color.WHITE, AppDialog.PRIMARY_HOVER, false);
        createBtn.setPreferredSize(new Dimension(100, 44));
        createBtn.addActionListener(event -> onCreateClicked());

        joinBtn = makeButton("JOIN GAME", AppDialog.PRIMARY_BLUE, Color.WHITE, AppDialog.PRIMARY_HOVER, false);
        joinBtn.setPreferredSize(new Dimension(100, 44));
        joinBtn.addActionListener(event -> onJoinClicked());

        exitBtn = makeButton("EXIT", Color.WHITE, TEXT_DARK, DANGER_HOVER, true);
        exitBtn.setPreferredSize(new Dimension(100, 42));
        exitBtn.addActionListener(event -> onLeaveAppClicked());

        var actions = AppDialog.panel(new GridLayout(4, 1, 0, 10));
        actions.setBorder(new EmptyBorder(20, 0, 0, 0));
        actions.add(statusBanner);
        actions.add(createBtn);
        actions.add(joinBtn);
        actions.add(exitBtn);
        form.add(actions, BorderLayout.SOUTH);

        wrapper.add(framed(form, Color.WHITE, AppDialog.CARD_BORDER, 36, 36, 36, 36));
        return wrapper;
    }

    // =====================================================================
    //  การเชื่อมเหตุการณ์จากปุ่ม (Button hooks)
    // =====================================================================

    // เรียกเมื่อผู้เล่นกดปุ่ม CREATE GAME (HOST)
    protected void onCreateClicked() {
    }

    // เรียกเมื่อผู้เล่นกดปุ่ม JOIN GAME
    protected void onJoinClicked() {
    }

    // เรียกเมื่อผู้เล่นกดปุ่ม EXIT
    protected void onLeaveAppClicked() {
    }

    // =====================================================================
    //  เมธอดปรับสถานะและดึงข้อมูลที่ใช้งานโดย MainApp
    // =====================================================================

    // ดึงชื่อผู้เล่นที่กรอก
    public String getPlayerName() {
        return nameField.getText().trim();
    }

    // ดึง IP เซิร์ฟเวอร์ที่กรอก
    public String getServerIp() {
        return ipField.getText().trim();
    }

    // ดึงข้อความพอร์ตที่กรอก
    public String getServerPortText() {
        return portField.getText().trim();
    }

    /**
     * เปิด/ปิดการทำงานของปุ่มคำสั่งขณะกำลังรอเชื่อมต่อ พร้อมเปลี่ยนเคอร์เซอร์เมาส์เป็นรูปนาฬิกาทราย
     */
    public void setBusy(boolean busy) {
        createBtn.setEnabled(!busy);
        joinBtn.setEnabled(!busy);
        exitBtn.setEnabled(!busy);
        setCursor(new Cursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** แสดงข้อความสถานะทั่วไป (สีเหลืองอ่อน) */
    public void showStatus(String text) {
        applyStatus(text, WARNING_BG, WARNING_BORDER, WARNING_TEXT);
    }

    /** แสดงข้อความสถานะข้อผิดพลาด (สีแดง) หรือสถานะทั่วไป */
    public void showStatus(String text, boolean error) {
        if (error) {
            applyStatus(text, AppDialog.DANGER_BG, AppDialog.DANGER_BORDER, AppDialog.DANGER_TEXT);
        } else {
            showStatus(text);
        }
    }

    /** แสดงข้อความสถานะสำเร็จ (สีเขียว) */
    public void showSuccess(String text) {
        applyStatus(text, AppDialog.SUCCESS_BG, AppDialog.SUCCESS_BORDER, AppDialog.SUCCESS_TEXT);
    }

    // ปรับสีและข้อความของแถบสถานะ
    private void applyStatus(String text, Color bg, Color border, Color fg) {
        statusLabel.setText(text);
        statusLabel.setForeground(fg);
        statusTagLabel.setForeground(fg);
        statusBanner.setBackground(bg);
        statusBanner.setBorder(new LineBorder(border, 1));
        statusBanner.revalidate();
        statusBanner.repaint();
    }

    /**
     * รีเซ็ตหน้าจอฟอร์มให้กลับสู่สถานะพร้อมใช้งานหลังจากออกจากห้อง
     */
    public void reset() {
        setBusy(false);
        showStatus("Ready. Host a game or enter a host address.");
    }

    /**
     * กำหนดพอร์ตและรีเฟรชข้อมูล IP ฝั่งซ้ายสำหรับบอกเพื่อนร่วมห้อง
     */
    public void setHostInfo(int port) {
        portField.setText(String.valueOf(port));
        refreshHostInfo(port);
    }

    // ค้นหาและแสดงรายการ IP ในเครือข่าย LAN สำหรับให้ผู้เล่นคนอื่นพิมพ์เข้าร่วม
    private void refreshHostInfo(int port) {
        List<String> addresses = GameServer.getLanAddresses();
        if (addresses.isEmpty()) {
            hostInfoLabel.setText("No LAN address found - check your Wi-Fi / Ethernet connection.");
            return;
        }
        var text = new StringBuilder("Others join at: ");
        for (int i = 0; i < addresses.size(); i++) {
            text.append(i > 0 ? "  or  " : "").append(addresses.get(i)).append(":").append(port);
        }
        hostInfoLabel.setText(text.toString());
    }

    // =====================================================================
    //  คอมโพเนนต์สำเร็จรูปสำหรับหน้าจอนี้ (Small factories)
    // =====================================================================

    /** สร้างกรอบสี่เหลี่ยมมีเส้นขอบและระยะเว้นด้านใน (Padding) */
    private static JPanel framed(Container content, Color fill, Color line, int top, int left, int bottom, int right) {
        var inner = AppDialog.panel(new BorderLayout());
        inner.setBorder(new EmptyBorder(top, left, bottom, right));
        inner.add(content, BorderLayout.CENTER);

        var outer = new JPanel(new BorderLayout());
        outer.setBackground(fill);
        outer.setBorder(new LineBorder(line, 1));
        outer.add(inner, BorderLayout.CENTER);
        return outer;
    }

    /** สร้างช่องกรอกข้อความ (JTextField) แบบไม่มีขอบในตัว */
    private static JTextField makeField(String initialText) {
        var field = new JTextField(initialText);
        field.setFont(AppDialog.PLAIN_14);
        field.setForeground(TEXT_DARK);
        field.setBackground(Color.WHITE);
        field.setBorder(new EmptyBorder(0, 0, 0, 0));
        return field;
    }

    // สร้างปุ่มกดพร้อมระบบเปลี่ยนสีเมื่อเมาส์ชี้
    private static JButton makeButton(String text, Color normal, Color fg, Color hover, boolean outlined) {
        var button = new JButton(text);
        button.setFont(AppDialog.SUBTITLE);
        button.setForeground(fg);
        button.setBackground(normal);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(outlined ? new LineBorder(AppDialog.CARD_BORDER, 1) : new EmptyBorder(0, 0, 0, 0));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                if (button.isEnabled()) {
                    button.setBackground(hover);
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                if (button.isEnabled()) {
                    button.setBackground(normal);
                }
            }
        });
        return button;
    }
}
