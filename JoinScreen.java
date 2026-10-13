import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.InetAddress;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * JoinScreen: Screen 1 - host a room or join one, over the same LAN/Wi-Fi.
 *
 * Two entry points:
 *   CREATE GAME - starts a GameServer on this machine and shows the address others
 *                 should type to join. The host also plays, so the app connects a
 *                 GameClient back to its own server afterwards.
 *   JOIN GAME   - validates the name / IP / port and asks the parent to open a
 *                 GameClient to that address.
 *
 * This screen never opens sockets itself: it validates input and reports through a
 * {@link JoinListener}. The parent does the blocking network work on a worker thread
 * and calls back into showStatus()/setBusy() so the UI never freezes.
 *
 * Standard AWT/Swing implementation without Graphics2D or GradientPaint.
 * Layouts: BorderLayout, FlowLayout and GridLayout only.
 * Uses Toolkit to load images from the "shape" folder.
 */
public class JoinScreen extends JPanel {

    /** Called when the player chooses to host or to join. Runs on the EDT; do networking off it. */
    public interface JoinListener {
        void onCreateGame(String playerName, int port);

        void onJoinGame(String playerName, String serverIp, int port);
    }

    private static final Color BG_LIGHT       = new Color(244, 246, 250);
    private static final Color PRIMARY_BLUE   = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER  = new Color(29, 78, 216);
    private static final Color SECONDARY_HOVER = new Color(241, 245, 249);
    private static final Color DANGER_HOVER   = new Color(220, 38, 38);
    private static final Color TEXT_DARK      = new Color(17, 24, 39);
    private static final Color TEXT_MUTED     = new Color(107, 114, 128);
    private static final Color CARD_BORDER    = new Color(226, 232, 240);
    private static final Color WARNING_BG     = new Color(255, 247, 230);
    private static final Color WARNING_BORDER = new Color(250, 219, 150);
    private static final Color WARNING_TEXT   = new Color(146, 84, 0);
    private static final Color SUCCESS_BG     = new Color(220, 252, 231);
    private static final Color SUCCESS_BORDER = new Color(187, 247, 208);
    private static final Color SUCCESS_TEXT   = new Color(22, 101, 52);
    private static final Color DANGER_BG      = new Color(254, 226, 226);
    private static final Color DANGER_BORDER  = new Color(254, 202, 202);
    private static final Color DANGER_TEXT    = new Color(153, 27, 27);

    private static final Font FONT_SMALL       = new Font("Tahoma", Font.PLAIN, 12);
    private static final Font FONT_SMALL_BOLD  = new Font("Tahoma", Font.BOLD, 12);
    private static final Font FONT_BODY        = new Font("Tahoma", Font.PLAIN, 14);
    private static final Font FONT_BODY_BOLD   = new Font("Tahoma", Font.BOLD, 14);
    private static final Font FONT_TITLE_LARGE = new Font("Tahoma", Font.BOLD, 26);

    private static final String IMAGE_DIR = "shape";
    private static final int DEFAULT_PORT = GameServer.DEFAULT_PORT;

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
    private final JoinListener listener;

    /** Dark navy background with faint decorative arc using standard Graphics (no Graphics2D or GradientPaint). */
    private static class GradientPanel extends JPanel {
        GradientPanel() {
            super(new BorderLayout());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int w = getWidth();
            int h = getHeight();

            // Solid deep navy background (eliminates horizontal scanline artifacts)
            g.setColor(new Color(15, 28, 55));
            g.fillRect(0, 0, w, h);

            // Large faint decorative circle on the right side
            g.setColor(new Color(255, 255, 255, 8));
            int circleSize = (int) (h * 1.6);
            g.fillOval(w - circleSize / 2, -circleSize / 4, circleSize, circleSize);
        }
    }

    public JoinScreen() {
        this(null);
    }

    public JoinScreen(JoinListener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        // Split layout: left branding panel and right connection form panel
        JPanel splitPanel = new JPanel(new GridLayout(1, 2));
        splitPanel.add(buildLeftPanel());
        splitPanel.add(buildRightPanel());

        add(splitPanel, BorderLayout.CENTER);
    }

    private JPanel buildLeftPanel() {
        JPanel leftBrandPanel = new GradientPanel();
        leftBrandPanel.setBorder(new EmptyBorder(30, 40, 40, 40));

        // Header: the LAN address other machines should use to reach this host
        JPanel brandHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brandHeader.setOpaque(false);

        hostInfoLabel = new JLabel();
        hostInfoLabel.setFont(FONT_SMALL);
        hostInfoLabel.setForeground(new Color(186, 203, 236));
        brandHeader.add(hostInfoLabel);
        refreshHostInfo(DEFAULT_PORT);

        leftBrandPanel.add(brandHeader, BorderLayout.NORTH);

        // Center: logo, title, description (empty first/last rows keep the group vertically centered)
        JPanel brandCenter = new JPanel(new GridLayout(5, 1, 0, 8));
        brandCenter.setOpaque(false);

        JLabel brandTitle = new JLabel("<html>Guess What<br>It Looks Like?</html>");
        brandTitle.setFont(new Font("Tahoma", Font.BOLD, 36));
        brandTitle.setForeground(Color.WHITE);

        JLabel brandDesc = new JLabel("Pick the shapes. Read your friends. Win the round.");
        brandDesc.setFont(new Font("Tahoma", Font.PLAIN, 15));
        brandDesc.setForeground(new Color(186, 203, 236));

        JPanel brandLogo = makeImageView("logo.png", 100, 100);

        brandCenter.add(makeSpacer());
        brandCenter.add(leftAligned(brandLogo));
        brandCenter.add(leftAligned(brandTitle));
        brandCenter.add(leftAligned(brandDesc));
        brandCenter.add(makeSpacer());

        leftBrandPanel.add(brandCenter, BorderLayout.CENTER);

        // Footer: shape icons + small text
        JPanel brandFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandFooter.setOpaque(false);

        String[] iconNames = {"Triangle.png", "Circle.png", "Rectangle.png"};
        for (String iconName : iconNames) {
            brandFooter.add(makeImageView(iconName, 22, 22));
        }

        JLabel footerText = new JLabel("A quick LAN party game for 2-8 players on one Wi-Fi");
        footerText.setFont(FONT_SMALL);
        footerText.setForeground(new Color(147, 171, 217));
        brandFooter.add(footerText);

        leftBrandPanel.add(brandFooter, BorderLayout.SOUTH);

        return leftBrandPanel;
    }

    private JPanel buildRightPanel() {
        // Margin around the card keeps it centered; the card scales with the window
        JPanel rightWrapper = new JPanel(new GridLayout(1, 1));
        rightWrapper.setBackground(BG_LIGHT);
        rightWrapper.setBorder(new EmptyBorder(40, 60, 40, 60));

        JPanel formContent = new JPanel(new BorderLayout());
        formContent.setOpaque(false);

        // NORTH: header text
        JPanel header = new JPanel(new GridLayout(3, 1, 0, 4));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 24, 0));

        JLabel joinServerTag = new JLabel("HOST OR JOIN");
        joinServerTag.setFont(FONT_SMALL_BOLD);
        joinServerTag.setForeground(PRIMARY_BLUE);
        header.add(joinServerTag);

        JLabel formTitle = new JLabel("Find your party");
        formTitle.setFont(FONT_TITLE_LARGE);
        formTitle.setForeground(TEXT_DARK);
        header.add(formTitle);

        JLabel formSub = new JLabel("<html>Host a room on this machine, or join a friend on the same Wi-Fi.</html>");
        formSub.setFont(FONT_BODY);
        formSub.setForeground(TEXT_MUTED);
        header.add(formSub);

        formContent.add(header, BorderLayout.NORTH);

        // CENTER: inputs (Name, Server IP, Port)
        String[] labels = {"Name", "Server IP (host)", "Port"};
        String[] defaults = {"", "", String.valueOf(DEFAULT_PORT)};
        JTextField[] fields = new JTextField[labels.length];

        JPanel inputs = new JPanel(new GridLayout(labels.length, 1, 0, 16));
        inputs.setOpaque(false);

        for (int i = 0; i < labels.length; i++) {
            JLabel label = new JLabel(labels[i]);
            label.setFont(FONT_BODY_BOLD);
            label.setForeground(TEXT_DARK);
            label.setVerticalAlignment(JLabel.BOTTOM);

            fields[i] = makeField(defaults[i]);
            JPanel fieldBox = framed(fields[i], Color.WHITE, CARD_BORDER, 0, 12, 0, 12);
            fieldBox.setPreferredSize(new Dimension(100, 42));

            JPanel block = new JPanel(new GridLayout(2, 1, 0, 6));
            block.setOpaque(false);
            block.add(label);
            block.add(fieldBox);
            inputs.add(block);
        }
        nameField = fields[0];
        ipField = fields[1];
        portField = fields[2];

        formContent.add(inputs, BorderLayout.CENTER);

        // SOUTH: status banner + action buttons
        JPanel bannerContent = new JPanel(new BorderLayout());
        bannerContent.setOpaque(false);

        statusTagLabel = new JLabel("STATUS");
        statusTagLabel.setFont(FONT_SMALL_BOLD);
        statusTagLabel.setForeground(WARNING_TEXT);

        statusLabel = new JLabel("Ready. Host a game or enter a host address.");
        statusLabel.setFont(FONT_SMALL_BOLD);
        statusLabel.setForeground(WARNING_TEXT);

        bannerContent.add(statusTagLabel, BorderLayout.WEST);
        bannerContent.add(statusLabel, BorderLayout.EAST);

        statusBanner = framed(bannerContent, WARNING_BG, WARNING_BORDER, 6, 12, 6, 12);
        statusBanner.setPreferredSize(new Dimension(100, 38));

        createBtn = makeButton("CREATE GAME (HOST)", PRIMARY_BLUE, Color.WHITE, PRIMARY_HOVER, false);
        createBtn.setPreferredSize(new Dimension(100, 44));
        createBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCreateGame();
            }
        });

        joinBtn = makeButton("JOIN GAME", PRIMARY_BLUE, Color.WHITE, PRIMARY_HOVER, false);
        joinBtn.setPreferredSize(new Dimension(100, 44));
        joinBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onJoinGame();
            }
        });

        exitBtn = makeButton("EXIT", Color.WHITE, TEXT_DARK, DANGER_HOVER, true);
        exitBtn.setPreferredSize(new Dimension(100, 42));
        exitBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int res = JOptionPane.showConfirmDialog(
                        JoinScreen.this,
                        "Are you sure you want to exit?",
                        "Exit Game",
                        JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
        });

        JPanel actions = new JPanel(new GridLayout(4, 1, 0, 10));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(20, 0, 0, 0));
        actions.add(statusBanner);
        actions.add(createBtn);
        actions.add(joinBtn);
        actions.add(exitBtn);

        formContent.add(actions, BorderLayout.SOUTH);

        rightWrapper.add(framed(formContent, Color.WHITE, CARD_BORDER, 36, 36, 36, 36));
        return rightWrapper;
    }

    // =====================================================================
    //  Actions + validation
    // =====================================================================

    private void onCreateGame() {
<<<<<<< HEAD
=======
        if (listener == null) {
            showStatus("Preview mode - run MainApp to play.");
            return;
        }
>>>>>>> 0a6e9615cc10fd26bf03c5f0265a2d4473e67e1d
        String name = getPlayerName();
        if (name.isEmpty()) {
            showStatus("Enter your player name first.", true);
            JOptionPane.showMessageDialog(this, "Please enter your player name.", "Name required",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (name.length() > GameServer.MAX_NAME_LENGTH) {
            showStatus("Name must be at most " + GameServer.MAX_NAME_LENGTH + " characters.", true);
            return;
        }
        int port = parsePort();
        if (port < 0) {
            return;
        }
        listener.onCreateGame(name, port);
    }

    private void onJoinGame() {
<<<<<<< HEAD
=======
        if (listener == null) {
            showStatus("Preview mode - run MainApp to play.");
            return;
        }
>>>>>>> 0a6e9615cc10fd26bf03c5f0265a2d4473e67e1d
        String name = getPlayerName();
        String ip = getServerIp();

        if (name.isEmpty()) {
            fail("Please enter your player name.", "Name required");
            return;
        }
        if (name.length() > GameServer.MAX_NAME_LENGTH) {
            fail("Name must be at most " + GameServer.MAX_NAME_LENGTH + " characters.", "Name too long");
            return;
        }
        if (ip.isEmpty()) {
            fail("Please enter the host's IP address.", "Server IP required");
            return;
        }
        if (!isValidAddress(ip)) {
            fail("\"" + ip + "\" does not look like a valid IPv4 address or host name.", "Invalid address");
            return;
        }
        int port = parsePort();
        if (port < 0) {
            return;
        }
        listener.onJoinGame(name, ip, port);
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
        JOptionPane.showMessageDialog(this, status, dialogTitle, JOptionPane.WARNING_MESSAGE);
    }

    /** Accepts a dotted IPv4 literal or a plain host name (so "localhost" also works for local tests). */
    private static boolean isValidAddress(String value) {
        if (value.isEmpty() || value.length() > 253) {
            return false;
        }
        if (value.indexOf(' ') >= 0) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length == 4) {
            boolean allNumeric = true;
            for (String part : parts) {
                if (part.isEmpty() || !isNumeric(part)) {
                    allNumeric = false;
                    break;
                }
            }
            if (allNumeric) {
                for (String part : parts) {
                    int octet;
                    try {
                        octet = Integer.parseInt(part);
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (octet < 0 || octet > 255) {
                        return false;
                    }
                }
                return true;
            }
        }
        // Fall back to host-name rules.
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-';
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
    //  Public state updates used by the parent (MainApp)
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

    public int getServerPort() {
        try {
            return Integer.parseInt(getServerPortText());
        } catch (NumberFormatException e) {
            return -1;
        }
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

    /** Error line in the status banner; also shown in dialog form by callers when needed. */
    public void showStatus(String text, boolean error) {
        if (error) {
            applyStatus(text, DANGER_BG, DANGER_BORDER, DANGER_TEXT);
        } else {
            showStatus(text);
        }
    }

    public void showSuccess(String text) {
        applyStatus(text, SUCCESS_BG, SUCCESS_BORDER, SUCCESS_TEXT);
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
        String text;
        if (addresses.isEmpty()) {
            text = "No LAN address found - check your Wi-Fi / Ethernet connection.";
        } else {
            StringBuilder builder = new StringBuilder("Others join at: ");
            for (int i = 0; i < addresses.size(); i++) {
                if (i > 0) {
                    builder.append("  or  ");
                }
                builder.append(addresses.get(i)).append(":").append(port);
            }
            text = builder.toString();
        }
        if (hostInfoLabel != null) {
            hostInfoLabel.setText(text);
        }
    }

    /**
     * Wraps content in a filled box with a 1px line border and inner padding.
     * Uses two nested panels (LineBorder outside, EmptyBorder inside) so no compound border is needed.
     */
    private static JPanel framed(Component content, Color fill, Color line,
                                 int top, int left, int bottom, int right) {
        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(top, left, bottom, right));
        inner.add(content, BorderLayout.CENTER);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setBackground(fill);
        outer.setBorder(new LineBorder(line, 1));
        outer.add(inner, BorderLayout.CENTER);
        return outer;
    }

    /** Keeps a component at its preferred size, aligned left, inside a GridLayout cell. */
    private static JPanel leftAligned(Component c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        p.setOpaque(false);
        p.add(c);
        return p;
    }

    private static JPanel makeSpacer() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    /** Loads image from "shape/" directory using Toolkit without Graphics2D. */
    private static Image loadImage(String fileName) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        String path = System.getProperty("user.dir") + File.separator + IMAGE_DIR + File.separator + fileName;
        File file = new File(path);

        if (file.exists()) {
            return tk.createImage(file.getPath());
        } else {
            java.net.URL url = JoinScreen.class.getResource("/" + IMAGE_DIR + "/" + fileName);
            if (url != null) {
                return tk.createImage(url);
            } else {
                return tk.createImage(path);
            }
        }
    }

    /** Creates a JPanel that paints an Image using standard Graphics.drawImage. */
    private static JPanel makeImageView(String fileName, int width, int height) {
        final Image img = loadImage(fileName);

        JPanel view = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (img != null) {
                    g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
                }
            }
        };
        view.setOpaque(false);
        view.setPreferredSize(new Dimension(width, height));
        return view;
    }

    /** Borderless text field; the surrounding box and padding come from framed(). */
    private static JTextField makeField(String initialText) {
        JTextField f = new JTextField(initialText);
        f.setFont(FONT_BODY);
        f.setForeground(TEXT_DARK);
        f.setBackground(Color.WHITE);
        f.setBorder(new EmptyBorder(0, 0, 0, 0));
        return f;
    }

    private static JButton makeButton(String text, final Color normal, Color fg,
                                      final Color hover, boolean outlined) {
        final JButton b = new JButton(text);
        b.setFont(FONT_BODY_BOLD);
        b.setForeground(fg);
        b.setBackground(normal);
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));

        if (outlined) {
            b.setBorder(new LineBorder(CARD_BORDER, 1));
        } else {
            b.setBorder(new EmptyBorder(0, 0, 0, 0));
        }

        b.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                applyHover(b, true, normal, hover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                applyHover(b, false, normal, hover);
            }
        });
        return b;
    }

    private static void applyHover(JButton b, boolean inside, Color normal, Color hover) {
        if (b.isEnabled()) {
            b.setBackground(inside ? hover : normal);
        }
    }

    /** Kept for callers that still want the old helper; returns the best LAN address or "127.0.0.1". */
    public static String getLocalIp() {
        String address = GameServer.getPreferredLanAddress();
        if (address == null || address.isEmpty()) {
            try {
                String found = InetAddress.getLocalHost().getHostAddress();
                return (found != null && !found.isEmpty()) ? found : "127.0.0.1";
            } catch (Exception e) {
                return "127.0.0.1";
            }
        }
        return address;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Guess What It Looks Like? - Join Server");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 720);
        frame.setMinimumSize(new Dimension(1100, 650));
        frame.setLocationRelativeTo(null);
        frame.setContentPane(new JoinScreen());
        frame.setVisible(true);
    }
}
