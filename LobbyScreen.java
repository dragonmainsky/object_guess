import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * LobbyScreen: Screen 2 - Game Lobby waiting room with players, server info, preview, and chat.
 */
public class LobbyScreen extends JPanel {
    private ActionListener onStartGameAction;
    private ActionListener onLeaveAction;

    public LobbyScreen() {
        this(null, null);
    }

    public LobbyScreen(ActionListener onStartGameAction, ActionListener onLeaveAction) {
        this.onStartGameAction = onStartGameAction;
        this.onLeaveAction = onLeaveAction;

        setLayout(new BorderLayout());
        setBackground(UIStyle.BG_LIGHT);

        // Main Body Container (no dark HeaderBar on this screen)
        JPanel mainContent = new JPanel(new BorderLayout(16, 16));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(16, 24, 16, 24));

        // Subheader Bar
        JPanel subHeader = new JPanel(new BorderLayout());
        subHeader.setOpaque(false);

        JPanel subTitles = new JPanel(new GridLayout(3, 1, 0, 2));
        subTitles.setOpaque(false);

        JLabel tag = new JLabel("GAME LOBBY");
        tag.setFont(UIStyle.FONT_SMALL_BOLD);
        tag.setForeground(UIStyle.PRIMARY_BLUE);

        JLabel roomTitle = new JLabel("Room #01");
        roomTitle.setFont(UIStyle.FONT_TITLE_LARGE);
        roomTitle.setForeground(UIStyle.TEXT_DARK);

        JLabel roomSub = new JLabel("Get ready while the host prepares the round.");
        roomSub.setFont(UIStyle.FONT_BODY);
        roomSub.setForeground(UIStyle.TEXT_MUTED);

        subTitles.add(tag);
        subTitles.add(roomTitle);
        subTitles.add(roomSub);
        subHeader.add(subTitles, BorderLayout.WEST);

        // Room Code Badge on Top Right (two lines)
        JPanel codeWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        codeWrapper.setOpaque(false);
        UIStyle.PillBadge roomCodeBadge = new UIStyle.PillBadge("<html><center>ROOM CODE<br>SHAPE-01</center></html>", UIStyle.BLUE_LIGHT_BG, UIStyle.PRIMARY_BLUE, UIStyle.BLUE_BORDER);
        roomCodeBadge.setFont(UIStyle.FONT_BODY_BOLD);
        roomCodeBadge.setBorder(new EmptyBorder(8, 16, 8, 16));
        codeWrapper.add(roomCodeBadge);
        subHeader.add(codeWrapper, BorderLayout.EAST);

        mainContent.add(subHeader, BorderLayout.NORTH);

        // 3-Column Layout: Left, Center, Right
        JPanel columnsPanel = new JPanel(new GridBagLayout());
        columnsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(8, 8, 8, 8);

        // --- Column 1: Left (Players & Server Info) ---
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.28;
        gbc.weighty = 1.0;
        columnsPanel.add(createLeftPanel(), gbc);

        // --- Column 2: Center (Ready Preview & Start) ---
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.44;
        gbc.weighty = 1.0;
        columnsPanel.add(createCenterPanel(), gbc);

        // --- Column 3: Right (Chat & Settings) ---
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0.28;
        gbc.weighty = 1.0;
        columnsPanel.add(createRightPanel(), gbc);

        mainContent.add(columnsPanel, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
    }

    /**
     * Left Column: Players List & Server Information
     */
    private JPanel createLeftPanel() {
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        // Card 1: 3 Players
        UIStyle.CardPanel playersCard = new UIStyle.CardPanel(16, UIStyle.CARD_BG, UIStyle.CARD_BORDER);
        playersCard.setLayout(new BoxLayout(playersCard, BoxLayout.Y_AXIS));
        playersCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        playersCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));

        JPanel pHeader = new JPanel(new BorderLayout());
        pHeader.setOpaque(false);
        pHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        pHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel pTitle = new JLabel("3 Players");
        pTitle.setFont(UIStyle.FONT_SUBTITLE);
        pTitle.setForeground(UIStyle.TEXT_DARK);

        UIStyle.PillBadge pBadge = new UIStyle.PillBadge("3 / 8", UIStyle.SUCCESS_BG, UIStyle.SUCCESS_TEXT);
        pHeader.add(pTitle, BorderLayout.WEST);
        pHeader.add(pBadge, BorderLayout.EAST);

        playersCard.add(pHeader);
        playersCard.add(Box.createVerticalStrut(12));

        // Player items
        playersCard.add(createPlayerRow("A", new Color(37, 99, 235), "Aim", "Ready", UIStyle.SUCCESS_BG, UIStyle.SUCCESS_TEXT));
        playersCard.add(Box.createVerticalStrut(8));
        playersCard.add(createPlayerRow("P", new Color(139, 92, 246), "Player 2", "Ready", UIStyle.SUCCESS_BG, UIStyle.SUCCESS_TEXT));
        playersCard.add(Box.createVerticalStrut(8));
        playersCard.add(createPlayerRow("P", new Color(236, 72, 153), "Player 3", "Waiting", UIStyle.WARNING_BG, UIStyle.WARNING_TEXT));
        playersCard.add(Box.createVerticalStrut(10));

        // Slot: Waiting for player...
        JPanel emptySlot = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(241, 245, 249));
                if (UIStyle.ENABLE_ROUNDED_CORNERS) {
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                } else {
                    g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
                Stroke dashed = new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{4.0f}, 0.0f);
                g2.setStroke(dashed);
                g2.setColor(new Color(203, 213, 225));
                if (UIStyle.ENABLE_ROUNDED_CORNERS) {
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                } else {
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        emptySlot.setOpaque(false);
        emptySlot.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        emptySlot.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel emptyLabel = new JLabel("Waiting for player...");
        emptyLabel.setFont(UIStyle.FONT_SMALL);
        emptyLabel.setForeground(UIStyle.TEXT_MUTED);
        emptySlot.add(emptyLabel);

        playersCard.add(emptySlot);
        left.add(playersCard);
        left.add(Box.createVerticalStrut(12));

        // Card 2: Server information
        UIStyle.CardPanel serverCard = new UIStyle.CardPanel(16, UIStyle.CARD_BG, UIStyle.CARD_BORDER);
        serverCard.setLayout(new BoxLayout(serverCard, BoxLayout.Y_AXIS));
        serverCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JLabel sTitle = new JLabel("Server information");
        sTitle.setFont(UIStyle.FONT_SUBTITLE);
        sTitle.setForeground(UIStyle.TEXT_DARK);
        sTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        serverCard.add(sTitle);
        serverCard.add(Box.createVerticalStrut(10));
        serverCard.add(createInfoRow("Address", "192.168.1.24"));
        serverCard.add(Box.createVerticalStrut(6));
        serverCard.add(createInfoRow("Ping", "24 ms"));
        serverCard.add(Box.createVerticalStrut(6));
        serverCard.add(createInfoRow("Host", "Aim"));
        serverCard.add(Box.createVerticalStrut(12));

        UIStyle.PillBadge minReached = new UIStyle.PillBadge("Minimum 3 players reached", UIStyle.SUCCESS_BG, UIStyle.SUCCESS_TEXT);
        minReached.setAlignmentX(Component.LEFT_ALIGNMENT);
        minReached.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        serverCard.add(minReached);

        left.add(serverCard);
        left.add(Box.createVerticalGlue());
        return left;
    }

    /**
     * Center Column: Shapes preview, ready status, and action buttons.
     */
    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        UIStyle.CardPanel previewCard = new UIStyle.CardPanel(20, UIStyle.CARD_BG, UIStyle.CARD_BORDER);
        previewCard.setLayout(new BoxLayout(previewCard, BoxLayout.Y_AXIS));

        // Preview canvas: light-blue panel with 3 flat vector shapes
        JPanel canvasBox = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                int s = 80;
                int cy = (h - s) / 2;
                int cx = w / 2;
                // Cyan triangle on the left
                g2.setColor(new Color(34, 211, 238));
                int tx = cx - 90;
                int[] xp = {tx - 40, tx, tx + 40};
                int[] yp = {cy + s, cy, cy + s};
                g2.fillPolygon(xp, yp, 3);
                // Yellow circle in the middle
                g2.setColor(new Color(250, 204, 21));
                g2.fillOval(cx - 40, cy, s, s);
                // Pink square on the right
                g2.setColor(new Color(244, 114, 182));
                g2.fillRect(cx + 50, cy, s, s);
                g2.dispose();
            }
        };
        canvasBox.setBackground(UIStyle.BLUE_LIGHT_BG);
        canvasBox.setOpaque(true);
        canvasBox.setBorder(BorderFactory.createLineBorder(UIStyle.BLUE_BORDER));
        canvasBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        canvasBox.setPreferredSize(new Dimension(380, 140));
        canvasBox.setAlignmentX(Component.CENTER_ALIGNMENT);

        previewCard.add(canvasBox);
        previewCard.add(Box.createVerticalStrut(20));

        // Headings (centered text)
        JLabel head = new JLabel("Almost ready!", SwingConstants.CENTER);
        head.setFont(UIStyle.FONT_TITLE);
        head.setForeground(UIStyle.TEXT_DARK);
        head.setAlignmentX(Component.CENTER_ALIGNMENT);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel sub = new JLabel("Every player must press READY before the host can start the game.", SwingConstants.CENTER);
        sub.setFont(UIStyle.FONT_BODY);
        sub.setForeground(UIStyle.TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        previewCard.add(head);
        previewCard.add(Box.createVerticalStrut(6));
        previewCard.add(sub);
        previewCard.add(Box.createVerticalStrut(20));

        // Progress bar container
        JPanel progressHeader = new JPanel(new BorderLayout());
        progressHeader.setOpaque(false);
        progressHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        progressHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel pReady = new JLabel("Players ready");
        pReady.setFont(UIStyle.FONT_SMALL_BOLD);
        pReady.setForeground(UIStyle.TEXT_DARK);

        JLabel pCount = new JLabel("2 of 3");
        pCount.setFont(UIStyle.FONT_SMALL_BOLD);
        pCount.setForeground(UIStyle.PRIMARY_BLUE);

        progressHeader.add(pReady, BorderLayout.WEST);
        progressHeader.add(pCount, BorderLayout.EAST);

        // Custom progress bar (rounded or sharp)
        JComponent progressBar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int h = getHeight();
                int w = getWidth();
                int fillW = (int) (w * 0.66f);
                g2.setColor(new Color(226, 232, 240));
                if (UIStyle.ENABLE_ROUNDED_CORNERS) {
                    g2.fillRoundRect(0, 0, w, h, h, h);
                    g2.setColor(UIStyle.PRIMARY_BLUE);
                    g2.fillRoundRect(0, 0, fillW, h, h, h);
                } else {
                    g2.fillRect(0, 0, w, h);
                    g2.setColor(UIStyle.PRIMARY_BLUE);
                    g2.fillRect(0, 0, fillW, h);
                }
                g2.dispose();
            }
        };
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        progressBar.setPreferredSize(new Dimension(380, 8));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        previewCard.add(progressHeader);
        previewCard.add(Box.createVerticalStrut(6));
        previewCard.add(progressBar);
        previewCard.add(Box.createVerticalGlue());

        center.add(previewCard, BorderLayout.CENTER);

        // Buttons at bottom
        JPanel btnPanel = new JPanel(new GridLayout(1, 2, 12, 0));
        btnPanel.setOpaque(false);

        UIStyle.ModernButton readyBtn = UIStyle.ModernButton.primary("READY");
        readyBtn.setPreferredSize(new Dimension(160, 44));

        UIStyle.ModernButton startBtn = UIStyle.ModernButton.secondary("START GAME");
        startBtn.setPreferredSize(new Dimension(160, 44));
        if (onStartGameAction != null) {
            startBtn.addActionListener(onStartGameAction);
        }

        btnPanel.add(readyBtn);
        btnPanel.add(startBtn);

        center.add(btnPanel, BorderLayout.SOUTH);
        return center;
    }

    /**
     * Right Column: Chat & Round Settings
     */
    private JPanel createRightPanel() {
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        // Card 1: Chat
        UIStyle.CardPanel chatCard = new UIStyle.CardPanel(16, UIStyle.CARD_BG, UIStyle.CARD_BORDER);
        chatCard.setLayout(new BorderLayout(0, 10));
        chatCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        chatCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setOpaque(false);
        chatHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel chatTitle = new JLabel("Chat");
        chatTitle.setFont(UIStyle.FONT_SUBTITLE);
        chatTitle.setForeground(UIStyle.TEXT_DARK);
        chatHeader.add(chatTitle, BorderLayout.WEST);

        chatCard.add(chatHeader, BorderLayout.NORTH);

        // Chat messages area
        JPanel chatList = new JPanel();
        chatList.setOpaque(false);
        chatList.setLayout(new BoxLayout(chatList, BoxLayout.Y_AXIS));

        chatList.add(createChatMessage("Aim", "Ready for a shape challenge?", true));
        chatList.add(Box.createVerticalStrut(6));
        chatList.add(createChatMessage("Player 2", "Let's go!", false));
        chatList.add(Box.createVerticalStrut(6));
        chatList.add(createChatMessage("System", "Player 3 joined the room.", false));

        JScrollPane scroll = new JScrollPane(chatList);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(220, 110));
        chatCard.add(scroll, BorderLayout.CENTER);

        // Chat input row
        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        inputRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JTextField chatInput = UIStyle.createStyledTextField("Type a message...");
        UIStyle.ModernButton sendBtn = UIStyle.ModernButton.primary("Send");
        sendBtn.setBorder(new EmptyBorder(6, 14, 6, 14));

        inputRow.add(chatInput, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        chatCard.add(inputRow, BorderLayout.SOUTH);

        right.add(chatCard);
        right.add(Box.createVerticalStrut(12));

        // Card 2: Round settings
        UIStyle.CardPanel settingsCard = new UIStyle.CardPanel(16, UIStyle.CARD_BG, UIStyle.CARD_BORDER);
        settingsCard.setLayout(new BoxLayout(settingsCard, BoxLayout.Y_AXIS));
        settingsCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        settingsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        JLabel sTitle = new JLabel("Round settings");
        sTitle.setFont(UIStyle.FONT_SUBTITLE);
        sTitle.setForeground(UIStyle.TEXT_DARK);
        sTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        settingsCard.add(sTitle);
        settingsCard.add(Box.createVerticalStrut(10));
        settingsCard.add(createInfoRow("Target", "3 shapes"));
        settingsCard.add(Box.createVerticalStrut(6));
        settingsCard.add(createInfoRow("Round time", "30 sec"));
        settingsCard.add(Box.createVerticalStrut(6));
        settingsCard.add(createInfoRow("Rounds", "4"));

        right.add(settingsCard);
        right.add(Box.createVerticalStrut(12));

        // Leave Room Button
        UIStyle.ModernButton leaveBtn = UIStyle.ModernButton.secondary("LEAVE ROOM");
        leaveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        leaveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (onLeaveAction != null) {
            leaveBtn.addActionListener(onLeaveAction);
        }

        right.add(leaveBtn);
        right.add(Box.createVerticalGlue());
        return right;
    }

    private JPanel createPlayerRow(String initial, Color avatarColor, String name, String status, Color bg, Color fg) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        left.setOpaque(false);
        left.add(new UIStyle.AvatarCircle(initial, avatarColor, 26));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(UIStyle.FONT_BODY_BOLD);
        nameLabel.setForeground(UIStyle.TEXT_DARK);
        left.add(nameLabel);

        row.add(left, BorderLayout.WEST);

        UIStyle.PillBadge statusBadge = new UIStyle.PillBadge(status, bg, fg);
        row.add(statusBadge, BorderLayout.EAST);
        return row;
    }

    private JPanel createInfoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = new JLabel(label);
        l.setFont(UIStyle.FONT_BODY);
        l.setForeground(UIStyle.TEXT_MUTED);

        JLabel v = new JLabel(value);
        v.setFont(UIStyle.FONT_BODY_BOLD);
        v.setForeground(UIStyle.TEXT_DARK);

        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private JPanel createChatMessage(String sender, String text, boolean isUser) {
        JPanel msg = new JPanel();
        msg.setOpaque(false);
        msg.setLayout(new BoxLayout(msg, BoxLayout.Y_AXIS));
        msg.setAlignmentX(Component.LEFT_ALIGNMENT);
        msg.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel s = new JLabel(sender);
        s.setFont(UIStyle.FONT_SMALL_BOLD);
        s.setForeground(isUser ? UIStyle.PRIMARY_BLUE : UIStyle.TEXT_MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel t = new JLabel(text);
        t.setFont(UIStyle.FONT_SMALL);
        t.setForeground(UIStyle.TEXT_DARK);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);

        msg.add(s);
        msg.add(t);
        return msg;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Guess What It Looks Like? - Game Lobby");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 750);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new LobbyScreen());
            frame.setVisible(true);
        });
    }
}
