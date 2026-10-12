import java.awt.*;
import java.awt.event.ActionListener;
import java.net.InetAddress;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * JoinScreen: Screen 1 - Server connection and player join screen.
 */
public class JoinScreen extends JPanel {
    private JTextField nameField;
    private JTextField ipField;
    private UIStyle.ModernButton joinBtn;
    private UIStyle.ModernButton exitBtn;

    public JoinScreen() {
        this(null);
    }

    public JoinScreen(ActionListener onJoinAction) {
        setLayout(new BorderLayout());
        setBackground(UIStyle.BG_LIGHT);

        // Split Layout: Left Branding Panel, Right Form Panel
        JPanel splitPanel = new JPanel(new GridLayout(1, 2));

        // ================= LEFT BRAND PANEL =================
        JPanel leftBrandPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Deep blue gradient
                GradientPaint gp = new GradientPaint(
                    0, 0, new Color(20, 36, 75),
                    getWidth(), getHeight(), new Color(13, 24, 52)
                );
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Subtle circular decorative background shape
                g2.setColor(new Color(255, 255, 255, 8));
                g2.fillOval(getWidth() / 2, -100, getWidth(), getWidth());
                g2.dispose();
            }
        };
        leftBrandPanel.setBorder(new EmptyBorder(30, 40, 40, 40));

        // Top Left: Make own server ? IP
        String localIp = "127.0.0.1";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            localIp = "192.168.1.24"; // ค่าเริ่มต้นกรณีดึง IP ไม่สำเร็จ
        }

        JPanel brandHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        brandHeader.setOpaque(false);

        JLabel hostInfoLabel = new JLabel("Make own server ?  IP : " + localIp);
        hostInfoLabel.setFont(UIStyle.FONT_SMALL);
        hostInfoLabel.setForeground(new Color(186, 203, 236));

        brandHeader.add(hostInfoLabel);
        leftBrandPanel.add(brandHeader, BorderLayout.NORTH);

        // Center: Big Logo and Game Slogan
        JPanel brandCenter = new JPanel();
        brandCenter.setOpaque(false);
        brandCenter.setLayout(new BoxLayout(brandCenter, BoxLayout.Y_AXIS));

        ImageIcon logoIcon = TextureManager.getIcon("logo.png", 140, 140);
        JLabel brandLogo = new JLabel(logoIcon);
        brandLogo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brandTitle = new JLabel("<html>Guess What<br>It Looks Like?</html>");
        brandTitle.setFont(new Font("Segoe UI", Font.BOLD, 36));
        brandTitle.setForeground(Color.WHITE);
        brandTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brandDesc = new JLabel("Pick the shapes. Read your friends. Win the round.");
        brandDesc.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        brandDesc.setForeground(new Color(186, 203, 236));
        brandDesc.setAlignmentX(Component.LEFT_ALIGNMENT);

        brandCenter.add(Box.createVerticalGlue());
        brandCenter.add(brandLogo);
        brandCenter.add(Box.createVerticalStrut(24));
        brandCenter.add(brandTitle);
        brandCenter.add(Box.createVerticalStrut(12));
        brandCenter.add(brandDesc);
        brandCenter.add(Box.createVerticalGlue());

        leftBrandPanel.add(brandCenter, BorderLayout.CENTER);

        // Bottom Left: Shape icons banner
        JPanel brandFooter = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandFooter.setOpaque(false);
        brandFooter.add(new JLabel(TextureManager.getIcon("Triangle.png", 22, 22)));
        brandFooter.add(new JLabel(TextureManager.getIcon("Circle.png", 22, 22)));
        brandFooter.add(new JLabel(TextureManager.getIcon("Rectangle.png", 22, 22)));

        JLabel footerText = new JLabel("A quick online party game for 3-8 players");
        footerText.setFont(UIStyle.FONT_SMALL);
        footerText.setForeground(new Color(147, 171, 217));
        brandFooter.add(footerText);

        leftBrandPanel.add(brandFooter, BorderLayout.SOUTH);
        splitPanel.add(leftBrandPanel);

        // ================= RIGHT FORM PANEL =================
        JPanel rightWrapper = new JPanel(new GridBagLayout());
        rightWrapper.setBackground(UIStyle.BG_LIGHT);

        UIStyle.CardPanel formCard = new UIStyle.CardPanel(20, Color.WHITE, UIStyle.CARD_BORDER);
        formCard.setPreferredSize(new Dimension(460, 520));
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(new EmptyBorder(36, 36, 36, 36));

        JLabel joinServerTag = new JLabel("JOIN SERVER");
        joinServerTag.setFont(UIStyle.FONT_SMALL_BOLD);
        joinServerTag.setForeground(UIStyle.PRIMARY_BLUE);
        joinServerTag.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel formTitle = new JLabel("Find your party");
        formTitle.setFont(UIStyle.FONT_TITLE_LARGE);
        formTitle.setForeground(UIStyle.TEXT_DARK);
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel formSub = new JLabel("Enter your name and the host address to join a game.");
        formSub.setFont(UIStyle.FONT_BODY);
        formSub.setForeground(UIStyle.TEXT_MUTED);
        formSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        formCard.add(joinServerTag);
        formCard.add(Box.createVerticalStrut(6));
        formCard.add(formTitle);
        formCard.add(Box.createVerticalStrut(4));
        formCard.add(formSub);
        formCard.add(Box.createVerticalStrut(24));

        // Name input field
        JLabel nameLabel = new JLabel("Name");
        nameLabel.setFont(UIStyle.FONT_BODY_BOLD);
        nameLabel.setForeground(UIStyle.TEXT_DARK);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        nameField = UIStyle.createStyledTextField("Aim");
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);

        formCard.add(nameLabel);
        formCard.add(Box.createVerticalStrut(6));
        formCard.add(nameField);
        formCard.add(Box.createVerticalStrut(16));

        // Server IP input field
        JLabel ipLabel = new JLabel("Server IP");
        ipLabel.setFont(UIStyle.FONT_BODY_BOLD);
        ipLabel.setForeground(UIStyle.TEXT_DARK);
        ipLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ipField = UIStyle.createStyledTextField("192.168.1.24");
        ipField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        ipField.setAlignmentX(Component.LEFT_ALIGNMENT);

        formCard.add(ipLabel);
        formCard.add(Box.createVerticalStrut(6));
        formCard.add(ipField);
        formCard.add(Box.createVerticalStrut(18));

        // Status banner
        UIStyle.CardPanel statusBanner = new UIStyle.CardPanel(10, UIStyle.WARNING_BG, UIStyle.WARNING_BORDER);
        statusBanner.setLayout(new BorderLayout());
        statusBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        statusBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusBanner.setBorder(new EmptyBorder(6, 12, 6, 12));

        JLabel playersCount = new JLabel("Players: 0 / 3 minimum");
        playersCount.setFont(UIStyle.FONT_SMALL_BOLD);
        playersCount.setForeground(UIStyle.WARNING_TEXT);

        JLabel waitingBadge = new JLabel("WAITING FOR SERVER");
        waitingBadge.setFont(UIStyle.FONT_SMALL_BOLD);
        waitingBadge.setForeground(UIStyle.WARNING_TEXT);

        statusBanner.add(playersCount, BorderLayout.WEST);
        statusBanner.add(waitingBadge, BorderLayout.EAST);

        formCard.add(statusBanner);
        formCard.add(Box.createVerticalStrut(24));

        // Action Buttons
        joinBtn = UIStyle.ModernButton.primary("JOIN GAME");
        joinBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        joinBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (onJoinAction != null) {
            joinBtn.addActionListener(onJoinAction);
        }

        exitBtn = UIStyle.ModernButton.secondary("EXIT");
        exitBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        exitBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        exitBtn.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit?", "Exit Game", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        });

        formCard.add(joinBtn);
        formCard.add(Box.createVerticalStrut(10));
        formCard.add(exitBtn);

        rightWrapper.add(formCard);
        splitPanel.add(rightWrapper);

        add(splitPanel, BorderLayout.CENTER);
    }

    public String getPlayerName() {
        return nameField.getText().trim();
    }

    public String getServerIp() {
        return ipField.getText().trim();
    }

    public void setOnJoinAction(ActionListener listener) {
        joinBtn.addActionListener(listener);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Guess What It Looks Like? - Join Server");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 720);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new JoinScreen());
            frame.setVisible(true);
        });
    }
}