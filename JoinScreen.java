import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * JoinScreen: screen 1 - host a room on this machine or join a friend's room over the same LAN.
 * The screen never opens a socket itself: it validates the input and reports through {@link JoinListener}.
 */
public class JoinScreen extends JPanel {

    /** Called when the player chooses to host or to join. Runs on the event thread; do networking off it. */
    public interface JoinListener {
        void onCreateGame(String playerName, int port);

        void onJoinGame(String playerName, String serverIp, int port);
    }

    // Colours and fonts that are particular to this screen (the rest comes from AppDialog).
    private static final Color BG_LIGHT = new Color(244, 246, 250), TEXT_DARK = new Color(17, 24, 39);
    private static final Color TEXT_MUTED = new Color(107, 114, 128), DANGER_HOVER = new Color(220, 38, 38);
    private static final Color WARNING_BG = new Color(255, 247, 230), WARNING_TEXT = new Color(146, 84, 0);
    private static final Color WARNING_BORDER = new Color(250, 219, 150);
    private static final Color NAVY = new Color(15, 28, 55), BRAND_TEXT = new Color(186, 203, 236);
    private static final Color BRAND_FOOT = new Color(147, 171, 217);

    private static final int DEFAULT_PORT = GameServer.DEFAULT_PORT;

    private final JoinListener listener;
    private JTextField nameField;
    private JTextField ipField;
    private JTextField portField;
    private JButton createBtn;
    private JButton joinBtn;
    private JButton exitBtn;
    private JLabel hostInfoLabel;
    private JLabel statusLabel;
    private JLabel statusTagLabel;
    private JPanel statusBanner;

    /** Deep navy background with one faint decorative circle, painted with plain Graphics. */
    private static class BrandPanel extends JPanel {
        BrandPanel() {
            super(new BorderLayout());
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(NAVY);
            g.fillRect(0, 0, getWidth(), getHeight());
            int circle = (int) (getHeight() * 1.6);
            g.setColor(new Color(255, 255, 255, 8));
            g.fillOval(getWidth() - circle / 2, -circle / 4, circle, circle);
        }
    }

    public JoinScreen(JoinListener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        // Split layout: branding on the left, the connection form on the right.
        var split = new JPanel(new GridLayout(1, 2));
        split.add(buildLeftPanel());
        split.add(buildRightPanel());
        add(split, BorderLayout.CENTER);
    }

    private JPanel buildLeftPanel() {
        var brand = new BrandPanel();
        brand.setBorder(new EmptyBorder(30, 40, 40, 40));

        var header = AppDialog.panel();
        header.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        hostInfoLabel = AppDialog.label("", AppDialog.SMALL_12, BRAND_TEXT);
        header.add(hostInfoLabel);
        refreshHostInfo(DEFAULT_PORT);
        brand.add(header, BorderLayout.NORTH);

        // Empty first and last rows keep the group vertically centered.
        var center = AppDialog.panel();
        center.setLayout(new GridLayout(5, 1, 0, 8));
        var title = AppDialog.label("<html>Guess What<br>It Looks Like?</html>", AppDialog.f(Font.BOLD, 36), Color.WHITE);
        var desc = AppDialog.label("Pick the shapes. Read your friends. Win the round.", AppDialog.SCREEN_SUB, BRAND_TEXT);
        center.add(AppDialog.panel());
        center.add(leftAligned(ShapeImages.shape("logo", 100)));
        center.add(leftAligned(title));
        center.add(leftAligned(desc));
        center.add(AppDialog.panel());
        brand.add(center, BorderLayout.CENTER);

        var footer = AppDialog.panel();
        footer.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (String name : new String[] {"Triangle", "Circle", ShapeImages.NAMES[2]}) {
            footer.add(ShapeImages.shape(name, 22));
        }
        footer.add(AppDialog.label("A quick LAN party game for 2-8 players on one Wi-Fi", AppDialog.SMALL_12, BRAND_FOOT));
        brand.add(footer, BorderLayout.SOUTH);
        return brand;
    }

    private JPanel buildRightPanel() {
        var wrapper = new JPanel(new GridLayout(1, 1));
        wrapper.setBackground(BG_LIGHT);
        wrapper.setBorder(new EmptyBorder(40, 60, 40, 60));

        var form = AppDialog.panel();
        form.setLayout(new BorderLayout());

        var header = AppDialog.panel();
        header.setLayout(new GridLayout(3, 1, 0, 4));
        header.setBorder(new EmptyBorder(0, 0, 24, 0));
        header.add(AppDialog.label("HOST OR JOIN", AppDialog.SMALL_BOLD_12, AppDialog.PRIMARY_BLUE));
        header.add(AppDialog.label("Find your party", AppDialog.TITLE_LARGE, TEXT_DARK));
        header.add(AppDialog.label("<html>Host a room on this machine, or join a friend on the same Wi-Fi.</html>",
                AppDialog.PLAIN_14, TEXT_MUTED));
        form.add(header, BorderLayout.NORTH);

        // Name, server IP and port, one framed text field each.
        String[] labels = {"Name", "Server IP (host)", "Port"};
        String[] defaults = {"", "", String.valueOf(DEFAULT_PORT)};
        var inputs = AppDialog.panel();
        inputs.setLayout(new GridLayout(labels.length, 1, 0, 16));
        var fields = new JTextField[labels.length];
        for (int i = 0; i < labels.length; i++) {
            var label = AppDialog.label(labels[i], AppDialog.SUBTITLE, TEXT_DARK);
            label.setVerticalAlignment(JLabel.BOTTOM);
            fields[i] = makeField(defaults[i]);
            var box = framed(fields[i], Color.WHITE, AppDialog.CARD_BORDER, 0, 12, 0, 12);
            box.setPreferredSize(new Dimension(100, 42));
            var block = AppDialog.panel();
            block.setLayout(new GridLayout(2, 1, 0, 6));
            block.add(label);
            block.add(box);
            inputs.add(block);
        }
        nameField = fields[0];
        ipField = fields[1];
        portField = fields[2];
        form.add(inputs, BorderLayout.CENTER);

        // Status banner plus the three action buttons.
        statusTagLabel = AppDialog.label("STATUS", AppDialog.SMALL_BOLD_12, WARNING_TEXT);
        statusLabel = AppDialog.label("Ready. Host a game or enter a host address.", AppDialog.SMALL_BOLD_12, WARNING_TEXT);
        var bannerContent = AppDialog.panel();
        bannerContent.setLayout(new BorderLayout());
        bannerContent.add(statusTagLabel, BorderLayout.WEST);
        bannerContent.add(statusLabel, BorderLayout.EAST);
        statusBanner = framed(bannerContent, WARNING_BG, WARNING_BORDER, 6, 12, 6, 12);
        statusBanner.setPreferredSize(new Dimension(100, 38));

        createBtn = makeButton("CREATE GAME (HOST)", AppDialog.PRIMARY_BLUE, Color.WHITE, AppDialog.PRIMARY_HOVER, false);
        createBtn.setPreferredSize(new Dimension(100, 44));
        createBtn.addActionListener(event -> onCreateGame());

        joinBtn = makeButton("JOIN GAME", AppDialog.PRIMARY_BLUE, Color.WHITE, AppDialog.PRIMARY_HOVER, false);
        joinBtn.setPreferredSize(new Dimension(100, 44));
        joinBtn.addActionListener(event -> onJoinGame());

        exitBtn = makeButton("EXIT", Color.WHITE, TEXT_DARK, DANGER_HOVER, true);
        exitBtn.setPreferredSize(new Dimension(100, 42));
        exitBtn.addActionListener(event -> {
            boolean confirmed = AppDialog.showConfirm(this, "Exit Game", "Are you sure you want to exit?", "Yes", "No");
            if (confirmed) {
                System.exit(0);
            }
        });

        var actions = AppDialog.panel();
        actions.setLayout(new GridLayout(4, 1, 0, 10));
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
    //  Actions and validation
    // =====================================================================

    private void onCreateGame() {
        String name = getPlayerName();
        if (name.isEmpty()) {
            showStatus("Enter your player name first.", true);
            AppDialog.showWarning(this, "Name required", "Please enter your player name.");
            return;
        }
        if (name.length() > GameServer.MAX_NAME_LENGTH) {
            showStatus("Name must be at most " + GameServer.MAX_NAME_LENGTH + " characters.", true);
            return;
        }
        int port = parsePort();
        if (port >= 0) {
            listener.onCreateGame(name, port);
        }
    }

    private void onJoinGame() {
        String name = getPlayerName();
        String ip = getServerIp();
        if (name.isEmpty()) {
            fail("Please enter your player name.", "Name required");
        } else if (name.length() > GameServer.MAX_NAME_LENGTH) {
            fail("Name must be at most " + GameServer.MAX_NAME_LENGTH + " characters.", "Name too long");
        } else if (ip.isEmpty()) {
            fail("Please enter the host's IP address.", "Server IP required");
        } else if (!isValidAddress(ip)) {
            fail("\"" + ip + "\" does not look like a valid IPv4 address or host name.", "Invalid address");
        } else {
            int port = parsePort();
            if (port >= 0) {
                listener.onJoinGame(name, ip, port);
            }
        }
    }

    private int parsePort() {
        String text = getServerPortText();
        if (text.isEmpty()) {
            fail("Please enter the port number (default " + DEFAULT_PORT + ").", "Port required");
            return -1;
        }
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            fail("\"" + text + "\" is not a number. Use " + DEFAULT_PORT + " unless told otherwise.", "Invalid port");
            return -1;
        }
        if (port < 1 || port > 65535) {
            fail("Port must be between 1 and 65535.", "Invalid port");
            return -1;
        }
        return port;
    }

    private void fail(String status, String dialogTitle) {
        showStatus(status, true);
        AppDialog.showWarning(this, dialogTitle, status);
    }

    /** Accepts a dotted IPv4 literal or a plain host name, so "localhost" works for local tests too. */
    private static boolean isValidAddress(String value) {
        if (value.isEmpty() || value.length() > 253 || value.indexOf(' ') >= 0) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length == 4 && isNumeric(parts[0]) && isNumeric(parts[1]) && isNumeric(parts[2]) && isNumeric(parts[3])) {
            try {
                for (String part : parts) {
                    int octet = Integer.parseInt(part);
                    if (octet < 0 || octet > 255) {
                        return false;
                    }
                }
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumeric(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    // =====================================================================
    //  Public state updates used by MainApp
    // =====================================================================

    public String getPlayerName() {
        return nameField.getText().trim();
    }

    public String getServerIp() {
        return ipField.getText().trim();
    }

    public String getServerPortText() {
        return portField.getText().trim();
    }

    /** Disables the action buttons while a connect attempt is running. */
    public void setBusy(boolean busy) {
        createBtn.setEnabled(!busy);
        joinBtn.setEnabled(!busy);
        exitBtn.setEnabled(!busy);
        setCursor(new Cursor(busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    /** Neutral informational line in the status banner. */
    public void showStatus(String text) {
        applyStatus(text, WARNING_BG, WARNING_BORDER, WARNING_TEXT);
    }

    /** Error line in the status banner. */
    public void showStatus(String text, boolean error) {
        if (error) {
            applyStatus(text, AppDialog.DANGER_BG, AppDialog.DANGER_BORDER, AppDialog.DANGER_TEXT);
        } else {
            showStatus(text);
        }
    }

    public void showSuccess(String text) {
        applyStatus(text, AppDialog.SUCCESS_BG, AppDialog.SUCCESS_BORDER, AppDialog.SUCCESS_TEXT);
    }

    private void applyStatus(String text, Color bg, Color border, Color fg) {
        statusLabel.setText(text);
        statusLabel.setForeground(fg);
        statusTagLabel.setForeground(fg);
        statusBanner.setBackground(bg);
        statusBanner.setBorder(new LineBorder(border, 1));
        statusBanner.revalidate();
        statusBanner.repaint();
    }

    /** Called after leaving a room so the form is clean for the next attempt. */
    public void reset() {
        setBusy(false);
        showStatus("Ready. Host a game or enter a host address.");
    }

    /** Suggests the port field and rebuilds the left-hand "connect here" hint. */
    public void setHostInfo(int port) {
        portField.setText(String.valueOf(port));
        refreshHostInfo(port);
    }

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
    //  Small factories
    // =====================================================================

    /** Filled box with a 1px line border and inner padding. */
    private static JPanel framed(Container content, Color fill, Color line, int top, int left, int bottom, int right) {
        var inner = AppDialog.panel();
        inner.setLayout(new BorderLayout());
        inner.setBorder(new EmptyBorder(top, left, bottom, right));
        inner.add(content, BorderLayout.CENTER);

        var outer = new JPanel(new BorderLayout());
        outer.setBackground(fill);
        outer.setBorder(new LineBorder(line, 1));
        outer.add(inner, BorderLayout.CENTER);
        return outer;
    }

    /** Keeps a component at its preferred size, aligned left, inside a GridLayout cell. */
    private static JPanel leftAligned(Container c) {
        var holder = AppDialog.panel();
        holder.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.add(c);
        return holder;
    }

    /** Borderless text field; the box and the padding come from framed(). */
    private static JTextField makeField(String initialText) {
        var field = new JTextField(initialText);
        field.setFont(AppDialog.PLAIN_14);
        field.setForeground(TEXT_DARK);
        field.setBackground(Color.WHITE);
        field.setBorder(new EmptyBorder(0, 0, 0, 0));
        return field;
    }

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
