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
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * JoinScreen: Screen 1 - Server connection and player join screen.
 *
 * Standard AWT/Swing implementation without Graphics2D or GradientPaint.
 * Layouts: BorderLayout, FlowLayout and GridLayout only.
 * Uses Toolkit to load images from the "shape" folder.
 */
public class JoinScreen extends JPanel {

    private static final Color BG_LIGHT       = new Color(244, 246, 250);
    private static final Color PRIMARY_BLUE   = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER  = new Color(29, 78, 216);
    private static final Color SECONDARY_HOVER = new Color(241, 245, 249);
    private static final Color TEXT_DARK      = new Color(17, 24, 39);
    private static final Color TEXT_MUTED     = new Color(107, 114, 128);
    private static final Color CARD_BORDER    = new Color(226, 232, 240);
    private static final Color WARNING_BG     = new Color(255, 247, 230);
    private static final Color WARNING_BORDER = new Color(250, 219, 150);
    private static final Color WARNING_TEXT   = new Color(146, 84, 0);

    private static final Font FONT_SMALL       = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_SMALL_BOLD  = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font FONT_BODY        = new Font("Segoe UI", Font.PLAIN, 14);
    private static final Font FONT_BODY_BOLD   = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_TITLE_LARGE = new Font("Segoe UI", Font.BOLD, 26);

    private static final String IMAGE_DIR = "shape";
    private static final String FALLBACK_IP = "192.168.1.24";

    private JTextField nameField;
    private JTextField ipField;
    private JButton joinBtn;
    private JButton exitBtn;

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

    public JoinScreen(ActionListener onJoinAction) {
        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        // Split layout: left branding panel and right connection form panel
        JPanel splitPanel = new JPanel(new GridLayout(1, 2));
        splitPanel.add(buildLeftPanel());
        splitPanel.add(buildRightPanel(onJoinAction));

        add(splitPanel, BorderLayout.CENTER);
    }

    private JPanel buildLeftPanel() {
        JPanel leftBrandPanel = new GradientPanel();
        leftBrandPanel.setBorder(new EmptyBorder(30, 40, 40, 40));

        // Header: Host info text
        JPanel brandHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brandHeader.setOpaque(false);

        JLabel hostInfoLabel = new JLabel("Make own server ?  IP : " + getLocalIp());
        hostInfoLabel.setFont(FONT_SMALL);
        hostInfoLabel.setForeground(new Color(186, 203, 236));
        brandHeader.add(hostInfoLabel);

        leftBrandPanel.add(brandHeader, BorderLayout.NORTH);

        // Center: logo, title, description (empty first/last rows keep the group vertically centered)
        JPanel brandCenter = new JPanel(new GridLayout(5, 1, 0, 8));
        brandCenter.setOpaque(false);

        JLabel brandTitle = new JLabel("<html>Guess What<br>It Looks Like?</html>");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 36));
        brandTitle.setForeground(Color.WHITE);

        JLabel brandDesc = new JLabel("Pick the shapes. Read your friends. Win the round.");
        brandDesc.setFont(new Font("Segoe UI", Font.PLAIN, 15));
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

        JLabel footerText = new JLabel("A quick online party game for 3-8 players");
        footerText.setFont(FONT_SMALL);
        footerText.setForeground(new Color(147, 171, 217));
        brandFooter.add(footerText);

        leftBrandPanel.add(brandFooter, BorderLayout.SOUTH);

        return leftBrandPanel;
    }

    private JPanel buildRightPanel(ActionListener onJoinAction) {
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

        JLabel joinServerTag = new JLabel("JOIN SERVER");
        joinServerTag.setFont(FONT_SMALL_BOLD);
        joinServerTag.setForeground(PRIMARY_BLUE);
        header.add(joinServerTag);

        JLabel formTitle = new JLabel("Find your party");
        formTitle.setFont(FONT_TITLE_LARGE);
        formTitle.setForeground(TEXT_DARK);
        header.add(formTitle);

        JLabel formSub = new JLabel("Enter your name and the host address to join a game.");
        formSub.setFont(FONT_BODY);
        formSub.setForeground(TEXT_MUTED);
        header.add(formSub);

        formContent.add(header, BorderLayout.NORTH);

        // CENTER: inputs (Name, Server IP)
        String[] labels = {"Name", "Server IP"};
        String[] defaults = {"Aim", FALLBACK_IP};
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

        formContent.add(inputs, BorderLayout.CENTER);

        // SOUTH: status banner + action buttons
        JPanel bannerContent = new JPanel(new BorderLayout());
        bannerContent.setOpaque(false);

        JLabel playersCount = new JLabel("Players: 0 / 3 minimum");
        playersCount.setFont(FONT_SMALL_BOLD);
        playersCount.setForeground(WARNING_TEXT);

        JLabel waitingBadge = new JLabel("WAITING FOR SERVER");
        waitingBadge.setFont(FONT_SMALL_BOLD);
        waitingBadge.setForeground(WARNING_TEXT);

        bannerContent.add(playersCount, BorderLayout.WEST);
        bannerContent.add(waitingBadge, BorderLayout.EAST);

        JPanel statusBanner = framed(bannerContent, WARNING_BG, WARNING_BORDER, 6, 12, 6, 12);
        statusBanner.setPreferredSize(new Dimension(100, 38));

        joinBtn = makeButton("JOIN GAME", PRIMARY_BLUE, Color.WHITE, PRIMARY_HOVER, false);
        joinBtn.setPreferredSize(new Dimension(100, 44));

        if (onJoinAction != null) {
            joinBtn.addActionListener(onJoinAction);
        }

        exitBtn = makeButton("EXIT", Color.WHITE, TEXT_DARK, SECONDARY_HOVER, true);
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

        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 10));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(20, 0, 0, 0));
        actions.add(statusBanner);
        actions.add(joinBtn);
        actions.add(exitBtn);

        formContent.add(actions, BorderLayout.SOUTH);

        rightWrapper.add(framed(formContent, Color.WHITE, CARD_BORDER, 36, 36, 36, 36));
        return rightWrapper;
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
        b.setBackground(inside ? hover : normal);
    }

    private static String getLocalIp() {
        try {
            String found = InetAddress.getLocalHost().getHostAddress();
            return (found != null && !found.isEmpty()) ? found : FALLBACK_IP;
        } catch (Exception e) {
            return FALLBACK_IP;
        }
    }

    public String getPlayerName() {
        return nameField.getText().trim();
    }

    public String getServerIp() {
        return ipField.getText().trim();
    }

    public void setOnJoinAction(ActionListener listener) {
        if (listener != null) {
            joinBtn.addActionListener(listener);
        }
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