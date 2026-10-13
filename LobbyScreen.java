import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * LobbyScreen: Screen 2 - waiting room. Every value on this screen now comes from the
 * server through {@link #setPlayers}/{@link #setAddress}/{@link #addChatMessage}; nothing
 * is hard-coded, so all machines show the same room.
 *
 * The layout, colors, fonts and helper factories are unchanged from the original design.
 * What changed is who fills them: the player rows, ready counter, progress track and chat
 * are rebuilt from the latest PlayerList that MainApp pushes in.
 *
 * Stacking trick used everywhere: a card placed at BorderLayout.NORTH keeps its
 * preferred height, and the leftover space below stays empty.
 */
public class LobbyScreen extends JPanel {

    // ===== Colors =====
    private static final Color BG_LIGHT          = new Color(241, 245, 249);
    private static final Color CARD_BG           = Color.WHITE;
    private static final Color CARD_BORDER       = new Color(226, 232, 240);

    private static final Color PRIMARY_BLUE      = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER     = new Color(29, 78, 216);
    private static final Color PRIMARY_PRESSED   = new Color(30, 64, 175);
    private static final Color SECONDARY_PRESSED = new Color(226, 232, 240);
    private static final Color BLUE_LIGHT_BG     = new Color(239, 246, 255);
    private static final Color BLUE_BORDER       = new Color(191, 219, 254);

    private static final Color SUCCESS_BG        = new Color(220, 252, 231);
    private static final Color SUCCESS_TEXT      = new Color(22, 101, 52);
    private static final Color SUCCESS_BORDER    = new Color(187, 247, 208);

    private static final Color WARNING_BG        = new Color(254, 249, 195);
    private static final Color WARNING_TEXT      = new Color(133, 77, 14);
    private static final Color WARNING_BORDER    = new Color(254, 240, 138);

    private static final Color TEXT_DARK         = new Color(15, 23, 42);
    private static final Color TEXT_MUTED        = new Color(100, 116, 139);
    private static final Color TEXT_DISABLED     = new Color(148, 163, 184);

    private static final Color EMPTY_SLOT_BG     = new Color(241, 245, 249);
    private static final Color EMPTY_SLOT_BORDER = new Color(203, 213, 225);
    private static final Color TRACK_GREY        = new Color(226, 232, 240);
    private static final Color PLACEHOLDER_BG    = new Color(220, 230, 242);
    private static final Color DISABLED_BG       = new Color(226, 232, 240);

    // ===== Fonts =====
    private static final Font FONT_TITLE_LARGE = new Font("Tahoma", Font.BOLD, 24);
    private static final Font FONT_TITLE       = new Font("Tahoma", Font.BOLD, 18);
    private static final Font FONT_SUBTITLE    = new Font("Tahoma", Font.BOLD, 14);
    private static final Font FONT_BODY        = new Font("Tahoma", Font.PLAIN, 13);
    private static final Font FONT_BODY_BOLD   = new Font("Tahoma", Font.BOLD, 13);
    private static final Font FONT_SMALL       = new Font("Tahoma", Font.PLAIN, 11);
    private static final Font FONT_SMALL_BOLD  = new Font("Tahoma", Font.BOLD, 11);

    // ===== Sizes =====
    private static final int SIDE_WIDTH = 320;
    private static final int PREVIEW_ICON = 80;

    /** Avatar colors cycled by join order. */
    private static final Color[] AVATAR_COLORS = {
        new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153),
        new Color(16, 185, 129), new Color(245, 158, 11), new Color(14, 165, 233),
        new Color(244, 63, 94), new Color(99, 102, 241)
    };

    // Folder that holds every PNG picture: <project folder>/shape
    private static final String SHAPE_DIR = System.getProperty("user.dir") + "/shape/";

    // Preview shapes: picture file + name shown when the picture is missing
    private static final String[] PREVIEW_FILES = {"Triangle.png", "Circle.png", "Rectangle.png"};
    private static final String[] PREVIEW_NAMES = {"Triangle", "Circle", "Rectangle"};

    private final Image[] previewImages = new Image[PREVIEW_FILES.length];
    private final ActionListener onStartGameAction;
    private final ActionListener onLeaveAction;

    // ===== Live room state (pushed in from MainApp) =====
    private final List<String> playerNames = new ArrayList<>();
    private final Map<String, Boolean> readyStates = new LinkedHashMap<>();
    private String myName = "";
    private String hostName = "";
    private boolean isHost = false;
    private ActionListener readyAction;
    private Consumer<String> chatAction;

    // ===== Widgets that are rebuilt from room state =====
    private JPanel playerRowsPanel;
    private JLabel playersCountLabel;
    private JLabel addressValueLabel;
    private JLabel hostValueLabel;
    private JLabel playersValueLabel;
    private JLabel minPlayersBadge;
    private JLabel readyCountLabel;
    private JPanel trackPanel;
    private JLabel roomCodeValueLabel;
    private JLabel subHeaderStatusLabel;
    private JPanel chatListPanel;
    private JTextField chatField;
    private JButton readyBtn;
    private JButton startBtn;
    private JButton leaveBtn;

    public LobbyScreen() {
        this(null, null);
    }

    public LobbyScreen(ActionListener onStartGameAction, ActionListener onLeaveAction) {
        this.onStartGameAction = onStartGameAction;
        this.onLeaveAction = onLeaveAction;
        loadPreviewImages();

        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        JPanel mainContent = new JPanel(new BorderLayout(16, 16));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(16, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);

        // 3 columns: fixed-width left, flexible center, fixed-width right
        JPanel left = createLeftPanel();
        left.setPreferredSize(new Dimension(SIDE_WIDTH, 0));
        JPanel right = createRightPanel();
        right.setPreferredSize(new Dimension(SIDE_WIDTH, 0));

        JPanel columns = new JPanel(new BorderLayout(16, 0));
        columns.setOpaque(false);
        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);

        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        refreshPlayers();
    }

    // =====================================================================
    //  Sections
    // =====================================================================

    private JPanel createSubHeader() {
        JPanel subHeader = new JPanel(new BorderLayout());
        subHeader.setOpaque(false);

        JPanel subTitles = new JPanel(new GridLayout(3, 1, 0, 2));
        subTitles.setOpaque(false);
        subTitles.add(makeLabel("GAME LOBBY", FONT_SMALL_BOLD, PRIMARY_BLUE));
        subTitles.add(makeLabel("Room", FONT_TITLE_LARGE, TEXT_DARK));
        subHeaderStatusLabel = makeLabel("Waiting for players to join...", FONT_BODY, TEXT_MUTED);
        subTitles.add(subHeaderStatusLabel);
        subHeader.add(subTitles, BorderLayout.WEST);

        // Room code badge: two plain labels stacked in one bordered box
        JPanel roomCodeBadge = new JPanel(new GridLayout(2, 1, 0, 0));
        roomCodeBadge.setOpaque(true);
        roomCodeBadge.setBackground(BLUE_LIGHT_BG);
        roomCodeBadge.setBorder(new CompoundBorder(
            new LineBorder(BLUE_BORDER, 1),
            new EmptyBorder(6, 16, 6, 16)));
        roomCodeBadge.add(makeCenteredLabel("PORT", FONT_SMALL_BOLD, PRIMARY_BLUE));
        roomCodeValueLabel = makeCenteredLabel("----", FONT_BODY_BOLD, PRIMARY_BLUE);
        roomCodeBadge.add(roomCodeValueLabel);

        // FlowLayout lets the badge keep its own size at the right edge
        JPanel codeSlot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        codeSlot.setOpaque(false);
        codeSlot.add(roomCodeBadge);
        subHeader.add(codeSlot, BorderLayout.EAST);

        return subHeader;
    }

    /** Left column: players list + server information. */
    private JPanel createLeftPanel() {
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);

        // --- Card 1: Players ---
        JPanel playersCard = makeCard();
        playersCard.setLayout(new BorderLayout(0, 12));

        JPanel playersHeader = new JPanel(new BorderLayout());
        playersHeader.setOpaque(false);
        playersCountLabel = makeLabel("Players in room: 0", FONT_SUBTITLE, TEXT_DARK);
        playersHeader.add(playersCountLabel, BorderLayout.WEST);
        playersCard.add(playersHeader, BorderLayout.NORTH);

        playerRowsPanel = new JPanel(new GridLayout(0, 1, 0, 8));
        playerRowsPanel.setOpaque(false);

        JPanel playersBody = new JPanel(new BorderLayout(0, 8));
        playersBody.setOpaque(false);
        playersBody.add(playerRowsPanel, BorderLayout.NORTH);
        playersBody.add(createEmptySlot(), BorderLayout.CENTER);
        playersCard.add(playersBody, BorderLayout.CENTER);

        // --- Card 2: Server information ---
        JPanel serverCard = makeCard();
        serverCard.setLayout(new BorderLayout(0, 10));
        serverCard.add(makeLabel("Server information", FONT_SUBTITLE, TEXT_DARK), BorderLayout.NORTH);

        JPanel serverRows = new JPanel(new GridLayout(0, 1, 0, 6));
        serverRows.setOpaque(false);
        addressValueLabel = makeLabel("-", FONT_BODY_BOLD, TEXT_DARK);
        hostValueLabel = makeLabel("-", FONT_BODY_BOLD, TEXT_DARK);
        playersValueLabel = makeLabel("-", FONT_BODY_BOLD, TEXT_DARK);
        serverRows.add(createInfoRow("Address", addressValueLabel));
        serverRows.add(createInfoRow("Host", hostValueLabel));
        serverRows.add(createInfoRow("Players", playersValueLabel));

        JPanel serverBody = new JPanel(new BorderLayout(0, 12));
        serverBody.setOpaque(false);
        serverBody.add(serverRows, BorderLayout.NORTH);
        minPlayersBadge = makeBadge("Waiting for players", WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        serverBody.add(minPlayersBadge, BorderLayout.SOUTH);
        serverCard.add(serverBody, BorderLayout.CENTER);

        // Both cards keep their natural height; leftover space stays empty at the bottom
        JPanel leftRest = new JPanel(new BorderLayout());
        leftRest.setOpaque(false);
        leftRest.add(serverCard, BorderLayout.NORTH);

        left.add(playersCard, BorderLayout.NORTH);
        left.add(leftRest, BorderLayout.CENTER);
        return left;
    }

    /** Center column: shapes preview, ready progress and the action buttons. */
    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 14));
        center.setOpaque(false);

        JPanel previewCard = makeCard();
        previewCard.setLayout(new BorderLayout(0, 20));

        // Preview canvas: light-blue panel with 3 shape pictures
        JPanel canvasBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 30));
        canvasBox.setOpaque(true);
        canvasBox.setBackground(BLUE_LIGHT_BG);
        canvasBox.setBorder(new LineBorder(BLUE_BORDER, 1));
        for (int i = 0; i < PREVIEW_FILES.length; i++) {
            canvasBox.add(createPreviewPicture(previewImages[i], PREVIEW_NAMES[i]));
        }
        previewCard.add(canvasBox, BorderLayout.NORTH);

        // Headings: one plain label per line
        JPanel headings = new JPanel(new GridLayout(3, 1, 0, 4));
        headings.setOpaque(false);
        headings.add(makeCenteredLabel("Almost ready!", FONT_TITLE, TEXT_DARK));
        headings.add(makeCenteredLabel("Every player must press READY", FONT_BODY, TEXT_MUTED));
        headings.add(makeCenteredLabel("before the host can start the game.", FONT_BODY, TEXT_MUTED));

        // Progress: label row + one segment per player (filled segments = ready players)
        JPanel progressHeader = new JPanel(new BorderLayout());
        progressHeader.setOpaque(false);
        progressHeader.add(makeLabel("Players ready", FONT_SMALL_BOLD, TEXT_DARK), BorderLayout.WEST);
        readyCountLabel = makeLabel("0 of 0", FONT_SMALL_BOLD, PRIMARY_BLUE);
        progressHeader.add(readyCountLabel, BorderLayout.EAST);

        trackPanel = new JPanel(new GridLayout(1, 1, 0, 0));
        trackPanel.setOpaque(true);
        trackPanel.setBackground(TRACK_GREY);
        trackPanel.setPreferredSize(new Dimension(0, 8));
        JPanel trackSlot = new JPanel(new BorderLayout());
        trackSlot.setOpaque(false);
        trackSlot.add(trackPanel, BorderLayout.NORTH);

        JPanel progress = new JPanel(new BorderLayout(0, 6));
        progress.setOpaque(false);
        progress.add(progressHeader, BorderLayout.NORTH);
        progress.add(trackSlot, BorderLayout.CENTER);

        JPanel info = new JPanel(new BorderLayout(0, 18));
        info.setOpaque(false);
        info.add(headings, BorderLayout.NORTH);
        info.add(progress, BorderLayout.CENTER);

        JPanel infoSlot = new JPanel(new BorderLayout());
        infoSlot.setOpaque(false);
        infoSlot.add(info, BorderLayout.NORTH);
        previewCard.add(infoSlot, BorderLayout.CENTER);

        center.add(previewCard, BorderLayout.CENTER);

        // Buttons at bottom
        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 12, 0));
        buttonRow.setOpaque(false);

        readyBtn = makeButton("READY", true, 12, 18);
        readyBtn.setPreferredSize(new Dimension(160, 44));
        readyBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (readyAction != null) {
                    readyAction.actionPerformed(e);
                }
            }
        });

        startBtn = makeButton("START GAME", false, 12, 18);
        startBtn.setPreferredSize(new Dimension(160, 44));
        startBtn.setToolTipText("Only the host can start, once every other player is ready.");
        if (onStartGameAction != null) {
            startBtn.addActionListener(onStartGameAction);
        }

        buttonRow.add(readyBtn);
        buttonRow.add(startBtn);
        center.add(buttonRow, BorderLayout.SOUTH);
        return center;
    }

    /** Right column: chat, round settings and the leave button. */
    private JPanel createRightPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);

        // --- Card 1: Chat ---
        JPanel chatCard = makeCard();
        chatCard.setLayout(new BorderLayout(0, 10));
        chatCard.add(makeLabel("Chat", FONT_SUBTITLE, TEXT_DARK), BorderLayout.NORTH);

        chatListPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        chatListPanel.setOpaque(false);

        JPanel chatSlot = new JPanel(new BorderLayout());
        chatSlot.setOpaque(false);
        chatSlot.add(chatListPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(chatSlot);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(CARD_BG);
        scroll.setPreferredSize(new Dimension(220, 130));
        chatCard.add(scroll, BorderLayout.CENTER);

        chatField = makeTextField("");
        final JButton sendBtn = makeButton("Send", true, 8, 14);
        ActionListener sendChat = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String text = chatField.getText().trim();
                if (text.isEmpty()) {
                    return;
                }
                if (chatAction != null) {
                    chatAction.accept(text);
                }
                chatField.setText("");
            }
        };
        sendBtn.addActionListener(sendChat);
        chatField.addActionListener(sendChat);

        JPanel inputRow = new JPanel(new BorderLayout(6, 0));
        inputRow.setOpaque(false);
        inputRow.add(chatField, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);
        chatCard.add(inputRow, BorderLayout.SOUTH);

        // --- Card 2: Round settings (values come from the server's constants) ---
        JPanel settingsCard = makeCard();
        settingsCard.setLayout(new BorderLayout(0, 10));
        settingsCard.add(makeLabel("Round settings", FONT_SUBTITLE, TEXT_DARK), BorderLayout.NORTH);

        JPanel settingRows = new JPanel(new GridLayout(0, 1, 0, 6));
        settingRows.setOpaque(false);
        settingRows.add(createInfoRow("Target", GameServer.MIN_SHAPES + "-" + GameServer.MAX_SHAPES + " shapes"));
        settingRows.add(createInfoRow("Round time", GameServer.ROUND_SECONDS + " sec"));
        settingRows.add(createInfoRow("Rounds", "players x " + GameServer.ROUNDS_PER_PLAYER));
        settingsCard.add(settingRows, BorderLayout.CENTER);

        // --- Leave button ---
        leaveBtn = makeButton("LEAVE ROOM", false, 10, 18);
        if (onLeaveAction != null) {
            leaveBtn.addActionListener(onLeaveAction);
        }

        // Each block keeps its natural height; leftover space stays empty at the bottom
        JPanel leaveSlot = new JPanel(new BorderLayout());
        leaveSlot.setOpaque(false);
        leaveSlot.add(leaveBtn, BorderLayout.NORTH);

        JPanel settingsAndLeave = new JPanel(new BorderLayout(0, 12));
        settingsAndLeave.setOpaque(false);
        settingsAndLeave.add(settingsCard, BorderLayout.NORTH);
        settingsAndLeave.add(leaveSlot, BorderLayout.CENTER);

        right.add(chatCard, BorderLayout.NORTH);
        right.add(settingsAndLeave, BorderLayout.CENTER);
        return right;
    }

    // =====================================================================
    //  Live room data
    // =====================================================================

    public void setMyName(String name) {
        this.myName = (name == null) ? "" : name;
    }

    public String getMyName() {
        return myName;
    }

    public void setHost(boolean host) {
        this.isHost = host;
        refreshPlayers();
    }

    public boolean isHost() {
        return isHost;
    }

    public void setReadyAction(ActionListener action) {
        this.readyAction = action;
    }

    public void setChatAction(Consumer<String> action) {
        this.chatAction = action;
    }

    public boolean isReady() {
        return Boolean.TRUE.equals(readyStates.get(myName));
    }

    /** Shows the host address players typed in (IP:port). */
    public void setAddress(String address) {
        addressValueLabel.setText(address == null || address.isEmpty() ? "-" : address);
    }

    /** Fills the port badge from the port actually in use. */
    public void setPort(int port) {
        roomCodeValueLabel.setText(port > 0 ? String.valueOf(port) : "----");
    }

    public void setStatusText(String text) {
        subHeaderStatusLabel.setText(text);
    }

    /** Replaces the whole player list and ready state with the server's latest snapshot. */
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

    /** Rebuilds every widget that depends on the room: rows, counters, track and buttons. */
    private void refreshPlayers() {
        int total = playerNames.size();

        playerRowsPanel.removeAll();
        for (int i = 0; i < total; i++) {
            String name = playerNames.get(i);
            boolean ready = Boolean.TRUE.equals(readyStates.get(name));
            boolean rowIsHost = name.equals(hostName);
            String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            String status;
            Color bg;
            Color fg;
            Color border;
            if (rowIsHost) {
                status = "Host";
                bg = BLUE_LIGHT_BG;
                fg = PRIMARY_BLUE;
                border = BLUE_BORDER;
            } else if (ready) {
                status = "Ready";
                bg = SUCCESS_BG;
                fg = SUCCESS_TEXT;
                border = SUCCESS_BORDER;
            } else {
                status = "Waiting";
                bg = WARNING_BG;
                fg = WARNING_TEXT;
                border = WARNING_BORDER;
            }
            playerRowsPanel.add(createPlayerRow(initial, AVATAR_COLORS[i % AVATAR_COLORS.length],
                    name, myName.equals(name) ? name + " (you)" : name, status, bg, fg, border));
        }
        playerRowsPanel.revalidate();
        playerRowsPanel.repaint();

        playersCountLabel.setText("Players in room: " + total);
        playersValueLabel.setText(String.valueOf(total));
        hostValueLabel.setText(hostName.isEmpty() ? "-" : hostName);

        int readyCount = 0;
        for (String name : playerNames) {
            if (Boolean.TRUE.equals(readyStates.get(name))) {
                readyCount++;
            }
        }
        readyCountLabel.setText(readyCount + " of " + total);

        // Progress track: one segment per player, filled while that player is ready
        trackPanel.removeAll();
        trackPanel.setLayout(new GridLayout(1, Math.max(1, total), 0, 0));
        for (int i = 0; i < Math.max(1, total); i++) {
            JPanel segment = new JPanel();
            segment.setOpaque(true);
            boolean filled = i < readyCount;
            segment.setBackground(filled ? PRIMARY_BLUE : TRACK_GREY);
            trackPanel.add(segment);
        }
        trackPanel.revalidate();
        trackPanel.repaint();

        if (total < GameServer.MIN_PLAYERS) {
            applyBadge(minPlayersBadge, "Minimum " + GameServer.MIN_PLAYERS + " players needed",
                    WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        } else {
            applyBadge(minPlayersBadge, "Minimum " + GameServer.MIN_PLAYERS + " players reached",
                    SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER);
        }

        boolean allOthersReady = total >= GameServer.MIN_PLAYERS;
        for (String name : playerNames) {
            if (!name.equals(myName) && !Boolean.TRUE.equals(readyStates.get(name))) {
                allOthersReady = false;
            }
        }
        boolean canStart = isHost && allOthersReady;

        startBtn.setEnabled(canStart);
        startBtn.setText(isHost ? "START GAME" : "HOST STARTS");
        startBtn.setBackground(canStart ? Color.WHITE : DISABLED_BG);
        startBtn.setForeground(canStart ? TEXT_DARK : TEXT_DISABLED);
        if (!isHost) {
            startBtn.setToolTipText("Only the host can start the game.");
        } else if (!allOthersReady) {
            startBtn.setToolTipText("Every other player must press READY first.");
        } else {
            startBtn.setToolTipText("All players are ready - start the round.");
        }

        // The ready button keeps its primary blue look; only its label changes with state.
        boolean meReady = Boolean.TRUE.equals(readyStates.get(myName));
        readyBtn.setText(meReady ? "CANCEL READY" : "READY");

        setStatusText(total < GameServer.MIN_PLAYERS
                ? "Waiting for more players to join..."
                : "Get ready while the host prepares the round.");
    }

    // =====================================================================
    //  Chat
    // =====================================================================

    /** Removes all chat lines (used when a fresh room is joined). */
    public void clearChat() {
        chatListPanel.removeAll();
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    /** Adds one chat line. System lines are drawn muted to match the original look. */
    public void addChatMessage(String sender, String text) {
        boolean isUser = sender != null && sender.equals(myName);
        boolean isSystem = "System".equalsIgnoreCase(sender);
        chatListPanel.add(createChatMessage(sender, text, isUser, isSystem));
        chatListPanel.revalidate();
        chatListPanel.repaint();
    }

    // =====================================================================
    //  Rows
    // =====================================================================

    private JPanel createPlayerRow(String initial, Color avatarColor, String name,
                                   String displayName, String status, Color bg, Color fg, Color border) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.setOpaque(false);
        who.add(makeAvatar(initial, avatarColor, 26));
        who.add(makeLabel(displayName, FONT_BODY_BOLD, TEXT_DARK));

        row.add(who, BorderLayout.WEST);
        row.add(makeBadge(status, bg, fg, border), BorderLayout.EAST);
        return row;
    }

    /** Solid-border slot for the next player to join. */
    private JPanel createEmptySlot() {
        JPanel slot = new JPanel(new BorderLayout());
        slot.setOpaque(true);
        slot.setBackground(EMPTY_SLOT_BG);
        slot.setBorder(new CompoundBorder(
            new LineBorder(EMPTY_SLOT_BORDER, 1),
            new EmptyBorder(8, 8, 8, 8)));
        slot.add(makeCenteredLabel("Waiting for player...", FONT_SMALL, TEXT_MUTED), BorderLayout.CENTER);
        return slot;
    }

    private JPanel createInfoRow(String label, String value) {
        return createInfoRow(label, makeLabel(value, FONT_BODY_BOLD, TEXT_DARK));
    }

    private JPanel createInfoRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.add(makeLabel(label, FONT_BODY, TEXT_MUTED), BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel createChatMessage(String sender, String text, boolean isUser, boolean isSystem) {
        Color senderColor;
        if (isUser) {
            senderColor = PRIMARY_BLUE;
        } else if (isSystem) {
            senderColor = WARNING_TEXT;
        } else {
            senderColor = TEXT_MUTED;
        }

        JPanel msg = new JPanel(new GridLayout(2, 1, 0, 0));
        msg.setOpaque(false);
        msg.add(makeLabel(sender, FONT_SMALL_BOLD, senderColor));
        msg.add(makeLabel(text, FONT_SMALL, TEXT_DARK));
        return msg;
    }

    /** One shape picture, or a grey box with the shape name when the file is missing. */
    private JLabel createPreviewPicture(Image image, String name) {
        JLabel picture;
        if (image != null) {
            picture = new JLabel(new ImageIcon(image));
        } else {
            picture = new JLabel(name, JLabel.CENTER);
            picture.setFont(FONT_SMALL_BOLD);
            picture.setForeground(TEXT_MUTED);
            picture.setOpaque(true);
            picture.setBackground(PLACEHOLDER_BG);
            picture.setBorder(new LineBorder(EMPTY_SLOT_BORDER, 1));
            picture.setPreferredSize(new Dimension(PREVIEW_ICON, PREVIEW_ICON));
        }
        return picture;
    }

    // =====================================================================
    //  Picture loading (AWT Toolkit)
    // =====================================================================

    private void loadPreviewImages() {
        for (int i = 0; i < PREVIEW_FILES.length; i++) {
            Image source = loadImage(PREVIEW_FILES[i]);
            previewImages[i] = scaleImage(source, PREVIEW_ICON);
        }
    }

    /** Returns a fully loaded image, or null when the file cannot be found. */
    private Image loadImage(String filename) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        File file = new File(SHAPE_DIR + filename);

        if (file.isFile()) {
            Image candidate = toolkit.createImage(file.getPath());
            if (waitForImage(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private Image scaleImage(Image source, int size) {
        if (source == null) {
            return null;
        }
        Image scaled = source.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        if (waitForImage(scaled)) {
            return scaled;
        } else {
            return null;
        }
    }

    private boolean waitForImage(Image image) {
        MediaTracker tracker = new MediaTracker(this);
        tracker.addImage(image, 0);
        try {
            tracker.waitForID(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !tracker.isErrorID(0);
    }

    // =====================================================================
    //  Small factories
    // =====================================================================

    private static JPanel makeCard() {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
            new LineBorder(CARD_BORDER, 1),
            new EmptyBorder(16, 16, 16, 16)));
        return card;
    }

    private static JLabel makeLabel(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** Same as makeLabel, but the text sits in the middle of the label's width. */
    private static JLabel makeCenteredLabel(String text, Font font, Color color) {
        JLabel label = makeLabel(text, font, color);
        label.setHorizontalAlignment(JLabel.CENTER);
        return label;
    }

    /** Small label with fill + border. */
    private static JLabel makeBadge(String text, Color bg, Color fg, Color border) {
        JLabel badge = makeCenteredLabel(text, FONT_SMALL_BOLD, fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(new CompoundBorder(
            new LineBorder(border, 1),
            new EmptyBorder(4, 10, 4, 10)));
        return badge;
    }

    /** Recolors an existing badge in place (text + fill + border). */
    private static void applyBadge(JLabel badge, String text, Color bg, Color fg, Color border) {
        badge.setText(text);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setBorder(new CompoundBorder(
            new LineBorder(border, 1),
            new EmptyBorder(4, 10, 4, 10)));
        badge.repaint();
    }

    /** Square colored tile with the player's initial, locked to size x size. */
    private static JLabel makeAvatar(String text, Color bg, int size) {
        JLabel avatar = new JLabel(text, JLabel.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(bg);
        avatar.setForeground(Color.WHITE);
        avatar.setFont(new Font("Tahoma", Font.BOLD, size / 2));
        Dimension d = new Dimension(size, size);
        avatar.setPreferredSize(d);
        avatar.setMinimumSize(d);
        avatar.setMaximumSize(d);
        return avatar;
    }

    /**
     * Plain JButton with a flat fill and thin border. The fill changes with
     * mouse enter / exit / press through ButtonHoverListener.
     */
    private static JButton makeButton(String text, boolean primary, int padV, int padH) {
        Color normal;
        Color hover;
        Color pressed;
        Color edge;
        Color textColor;
        if (primary) {
            normal = PRIMARY_BLUE;
            hover = PRIMARY_HOVER;
            pressed = PRIMARY_PRESSED;
            edge = PRIMARY_BLUE;
            textColor = Color.WHITE;
        } else {
            normal = Color.WHITE;
            hover = BG_LIGHT;
            pressed = SECONDARY_PRESSED;
            edge = CARD_BORDER;
            textColor = TEXT_DARK;
        }

        JButton button = new JButton(text);
        button.setFont(FONT_BODY_BOLD);
        button.setForeground(textColor);
        button.setBackground(normal);
        button.setContentAreaFilled(false); // call first: some look and feels reset opaque when it changes
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setBorder(new CompoundBorder(
            new LineBorder(edge, 1),
            new EmptyBorder(padV, padH, padV, padH)));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new ButtonHoverListener(button, normal, hover, pressed));
        return button;
    }

    private static JTextField makeTextField(String text) {
        JTextField field = new JTextField(text);
        field.setBackground(Color.WHITE);
        field.setForeground(TEXT_DARK);
        field.setFont(FONT_BODY);
        field.setBorder(new CompoundBorder(
            new LineBorder(CARD_BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)));
        return field;
    }

    // =====================================================================
    //  Inner classes
    // =====================================================================

    /** Colors a button from mouse events: normal, hover (mouse inside) and pressed. Disabled buttons are left alone. */
    private static class ButtonHoverListener extends MouseAdapter {
        private final JButton button;
        private final Color normal;
        private final Color hover;
        private final Color pressed;
        private boolean inside = false;

        ButtonHoverListener(JButton button, Color normal, Color hover, Color pressed) {
            this.button = button;
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            inside = true;
            if (button.isEnabled()) {
                button.setBackground(hover);
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            inside = false;
            if (button.isEnabled()) {
                button.setBackground(normal);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (button.isEnabled()) {
                button.setBackground(pressed);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!button.isEnabled()) {
                return;
            }
            if (inside) {
                button.setBackground(hover);
            } else {
                button.setBackground(normal);
            }
        }
    }

    // =====================================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Guess What It Looks Like? - Game Lobby");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(1280, 750);
                frame.setLocationRelativeTo(null);
                frame.setContentPane(new LobbyScreen());
                frame.setVisible(true);
            }
        });
    }
}
