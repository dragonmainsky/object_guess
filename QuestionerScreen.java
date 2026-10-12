import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * QuestionerScreen: Screen 3 - Questioner Mode where the player selects secret shapes.
 *
 * Remake: self-contained plain Swing. Colors, fonts and small component
 * factories live in this class, pictures are loaded with the AWT Toolkit and
 * drawn by a tiny ShapeImage component, and the layout is BorderLayout / BoxLayout.
 * The only custom painting is done with the basic Graphics object.
 */
public class QuestionerScreen extends JPanel {

    // ===== Colors =====
    private static final Color BG_LIGHT        = new Color(241, 245, 249);
    private static final Color CARD_BG         = Color.WHITE;
    private static final Color CARD_BORDER     = new Color(226, 232, 240);
    private static final Color HOVER_BG        = new Color(248, 250, 252);

    private static final Color PRIMARY_BLUE    = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER   = new Color(29, 78, 216);
    private static final Color PRIMARY_PRESSED = new Color(30, 64, 175);
    private static final Color SECONDARY_PRESSED = new Color(226, 232, 240);
    private static final Color BLUE_LIGHT_BG   = new Color(239, 246, 255);
    private static final Color BLUE_BORDER     = new Color(191, 219, 254);

    private static final Color SUCCESS_BG      = new Color(220, 252, 231);
    private static final Color SUCCESS_TEXT    = new Color(22, 101, 52);
    private static final Color SUCCESS_BORDER  = new Color(187, 247, 208);

    private static final Color WARNING_BG      = new Color(254, 249, 195);
    private static final Color WARNING_TEXT    = new Color(133, 77, 14);
    private static final Color WARNING_BORDER  = new Color(254, 240, 138);

    private static final Color TEXT_DARK       = new Color(15, 23, 42);
    private static final Color TEXT_MUTED      = new Color(100, 116, 139);

    private static final Color CANVAS_BG       = new Color(250, 252, 255);
    private static final Color CANVAS_LINE     = new Color(226, 232, 240);
    private static final Color STEP_INACTIVE   = new Color(203, 213, 225);

    // ===== Fonts =====
    private static final Font FONT_TITLE      = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font FONT_SUBTITLE   = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_BODY       = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BODY_BOLD  = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_SMALL      = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);

    // ===== Sizes =====
    private static final int MAX_SHAPES = 3;
    private static final int SMALL_ICON = 36;   // icon inside a picker card
    private static final int BIG_ICON = 100;    // icon on the secret canvas
    private static final int LEFT_WIDTH = 260;
    private static final int RIGHT_WIDTH = 330;
    private static final int BADGE_PAD_V = 6;
    private static final int BADGE_PAD_H = 14;

    // Folders searched for the shape pictures (same list the other screens use)
    private static final String[] SEARCH_DIRS = {
        ".",
        "UI_ONLY",
        "object_texture/UI_ONLY",
        "object_texture",
        "../UI_ONLY",
        "../object_texture/UI_ONLY",
        "../../object_texture/UI_ONLY"
    };

    // Available shapes: {display name, picture file}
    private static final String[][] AVAILABLE_SHAPES = {
        {"Circle", "Circle.png"},
        {"Triangle", "Triangle.png"},
        {"Rectangle", "Rectangle.png"},
        {"Oval", "Oval.png"},
        {"Hexagon", "Hexagon.png"},
        {"Diamond", "diamond.png"},
        {"Star", "star.png"},
        {"Square", "Square.png"}
    };

    // ===== State =====
    private final Set<String> selectedShapes =
        new LinkedHashSet<String>(Arrays.asList("Triangle", "Circle", "Rectangle"));
    private final List<ShapeCard> shapeCards = new ArrayList<ShapeCard>();
    private final Image[] smallImages = new Image[AVAILABLE_SHAPES.length];
    private final Image[] bigImages = new Image[AVAILABLE_SHAPES.length];
    private final ActionListener onConfirmAction;

    private JPanel secretSetCanvas;
    private JLabel selectedCounterLabel;
    private JLabel readyBadge;

    public QuestionerScreen() {
        this(null);
    }

    public QuestionerScreen(ActionListener onConfirmAction) {
        this.onConfirmAction = onConfirmAction;
        loadShapeImages();

        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        JPanel mainContent = new JPanel(new BorderLayout(16, 16));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(14, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);

        // 3 columns: fixed-width left, flexible center, fixed-width right
        JPanel leftColumn = createLeftPanel();
        leftColumn.setPreferredSize(new Dimension(LEFT_WIDTH, leftColumn.getPreferredSize().height));
        JPanel rightColumn = createRightPanel();
        rightColumn.setPreferredSize(new Dimension(RIGHT_WIDTH, rightColumn.getPreferredSize().height));

        JPanel columns = new JPanel(new BorderLayout(12, 0));
        columns.setOpaque(false);
        columns.add(leftColumn, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(rightColumn, BorderLayout.EAST);

        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        refreshUI();
    }

    // =====================================================================
    //  Sections
    // =====================================================================

    private JPanel createSubHeader() {
        JPanel subHeader = new JPanel(new BorderLayout());
        subHeader.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        titles.add(makeLabel("YOUR TURN TO CHOOSE", FONT_SMALL_BOLD, PRIMARY_BLUE));
        titles.add(makeLabel("Select the shapes the others must guess.", FONT_SUBTITLE, TEXT_DARK));
        subHeader.add(titles, BorderLayout.WEST);

        // Badges on right: Target & Time
        JPanel badgeGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        badgeGroup.setOpaque(false);
        badgeGroup.add(makeBadge("Target: 3 Shapes", BLUE_LIGHT_BG, PRIMARY_BLUE, BLUE_BORDER, FONT_BODY_BOLD));
        badgeGroup.add(makeBadge("Time: 00:30", WARNING_BG, WARNING_TEXT, WARNING_BORDER, FONT_BODY_BOLD));
        subHeader.add(badgeGroup, BorderLayout.EAST);

        return subHeader;
    }

    private JPanel createLeftPanel() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        // Card 1: Players live round score
        JPanel playersCard = makeCard();
        playersCard.setLayout(new BoxLayout(playersCard, BoxLayout.Y_AXIS));

        playersCard.add(alignLeft(makeLabel("Players", FONT_SUBTITLE, TEXT_DARK)));
        playersCard.add(alignLeft(makeLabel("Live round score", FONT_SMALL, TEXT_MUTED)));
        playersCard.add(Box.createVerticalStrut(12));

        JPanel aimRow = createScoreRow("A", new Color(37, 99, 235), "Aim", 0);
        aimRow.setOpaque(true);
        aimRow.setBackground(BLUE_LIGHT_BG);
        aimRow.setBorder(new CompoundBorder(
            new LineBorder(BLUE_BORDER, 1),
            new EmptyBorder(4, 8, 4, 8)));
        playersCard.add(aimRow);
        playersCard.add(Box.createVerticalStrut(8));
        playersCard.add(createScoreRow("P", new Color(139, 92, 246), "Player 2", 0));
        playersCard.add(Box.createVerticalStrut(8));
        playersCard.add(createScoreRow("P", new Color(236, 72, 153), "Player 3", 0));
        playersCard.add(Box.createVerticalStrut(14));

        // The divider is just the top border of the tip box
        // Plain labels, one per line (no wrapping markup)
        JPanel tipLines = new JPanel(new GridLayout(2, 1, 0, 0));
        tipLines.setOpaque(false);
        tipLines.add(makeLabel("Info: Questioner scores when", FONT_SMALL, TEXT_MUTED));
        tipLines.add(makeLabel("nobody finds all 3.", FONT_SMALL, TEXT_MUTED));
        JPanel tipBox = new JPanel(new BorderLayout());
        tipBox.setOpaque(false);
        tipBox.setBorder(new CompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER),
            new EmptyBorder(10, 0, 0, 0)));
        tipBox.add(tipLines, BorderLayout.NORTH);
        playersCard.add(alignLeft(tipBox));

        alignLeft(playersCard);
        playersCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 290));
        column.add(playersCard);
        column.add(Box.createVerticalStrut(12));

        // Card 2: Round 1 of 4 steps
        JPanel roundCard = makeCard();
        roundCard.setLayout(new BoxLayout(roundCard, BoxLayout.Y_AXIS));
        roundCard.add(alignLeft(makeLabel("Round 1 of 4", FONT_SUBTITLE, TEXT_DARK)));
        roundCard.add(Box.createVerticalStrut(12));
        roundCard.add(createStepRow(1, "Choose 3 shapes", true));
        roundCard.add(Box.createVerticalStrut(10));
        roundCard.add(createStepRow(2, "Confirm selection", false));
        roundCard.add(Box.createVerticalStrut(10));
        roundCard.add(createStepRow(3, "Watch guesses", false));

        alignLeft(roundCard);
        roundCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        column.add(roundCard);
        column.add(Box.createVerticalGlue());
        return column;
    }

    private JPanel createCenterPanel() {
        JPanel centerCard = makeCard();
        centerCard.setLayout(new BorderLayout(0, 12));

        // Header of center card
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setOpaque(false);

        JPanel titleGroup = new JPanel(new GridLayout(2, 1, 0, 2));
        titleGroup.setOpaque(false);
        titleGroup.add(makeLabel("Your secret set", FONT_TITLE, TEXT_DARK));
        titleGroup.add(makeLabel("Arrange your 3 selected shapes on the canvas", FONT_BODY, TEXT_MUTED));
        cardHeader.add(titleGroup, BorderLayout.WEST);

        readyBadge = makeBadge("READY TO CONFIRM", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER, FONT_SMALL_BOLD);
        cardHeader.add(centerVertically(readyBadge), BorderLayout.EAST);
        centerCard.add(cardHeader, BorderLayout.NORTH);

        // Secret set canvas (its content is filled in by refreshUI)
        secretSetCanvas = new DashedCanvas();
        secretSetCanvas.setLayout(new FlowLayout(FlowLayout.CENTER, 30, 60));
        centerCard.add(secretSetCanvas, BorderLayout.CENTER);

        // Helper text at bottom
        JLabel removeHint = makeLabel("Click a shape to remove it from the canvas", FONT_SMALL, TEXT_MUTED);
        removeHint.setHorizontalAlignment(SwingConstants.CENTER);
        centerCard.add(removeHint, BorderLayout.SOUTH);

        return centerCard;
    }

    private JPanel createRightPanel() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        // ----- Objects picker card -----
        JPanel objectsCard = makeCard();
        objectsCard.setLayout(new BoxLayout(objectsCard, BoxLayout.Y_AXIS));

        JPanel objectsHeader = new JPanel(new BorderLayout());
        objectsHeader.setOpaque(false);
        objectsHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        selectedCounterLabel = makeLabel("Selected: " + selectedShapes.size() + " / " + MAX_SHAPES,
            FONT_SMALL_BOLD, PRIMARY_BLUE);
        objectsHeader.add(makeLabel("Objects", FONT_SUBTITLE, TEXT_DARK), BorderLayout.WEST);
        objectsHeader.add(selectedCounterLabel, BorderLayout.EAST);
        objectsCard.add(alignLeft(objectsHeader));

        objectsCard.add(alignLeft(makeLabel("Select exactly 3 shapes", FONT_SMALL, TEXT_MUTED)));
        objectsCard.add(Box.createVerticalStrut(10));

        // Grid of shapes
        JPanel grid = new JPanel(new GridLayout(4, 2, 8, 8));
        grid.setOpaque(false);
        for (int i = 0; i < AVAILABLE_SHAPES.length; i++) {
            ShapeCard card = new ShapeCard(AVAILABLE_SHAPES[i][0], smallImages[i]);
            shapeCards.add(card);
            grid.add(card);
        }
        objectsCard.add(alignLeft(grid));
        objectsCard.add(Box.createVerticalStrut(14));

        // Action buttons: Clear & Confirm
        JPanel buttonRow = new JPanel(new GridLayout(1, 2, 8, 0));
        buttonRow.setOpaque(false);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JButton clearBtn = makeButton("CLEAR", false, 10, 18);
        clearBtn.addActionListener(new ClearListener());

        JButton confirmBtn = makeButton("CONFIRM", true, 10, 18);
        if (onConfirmAction != null) {
            confirmBtn.addActionListener(onConfirmAction);
        }

        buttonRow.add(clearBtn);
        buttonRow.add(confirmBtn);
        objectsCard.add(alignLeft(buttonRow));

        alignLeft(objectsCard);
        column.add(objectsCard);
        column.add(Box.createVerticalStrut(10));

        // ----- Chat card -----
        JPanel miniChat = makeCard();
        miniChat.setLayout(new BorderLayout(0, 6));

        miniChat.add(makeLabel("Chat", FONT_SMALL_BOLD, TEXT_DARK), BorderLayout.NORTH);

        // Name in bold blue + message in normal text, as two plain labels side by side
        JPanel chatSample = new JPanel();
        chatSample.setOpaque(false);
        chatSample.setLayout(new BoxLayout(chatSample, BoxLayout.X_AXIS));
        chatSample.add(makeLabel("Aim:", FONT_SMALL_BOLD, PRIMARY_BLUE));
        chatSample.add(Box.createHorizontalStrut(4));
        chatSample.add(makeLabel("Ready for challenge?", FONT_SMALL, TEXT_DARK));
        JPanel chatBody = new JPanel(new BorderLayout());
        chatBody.setOpaque(false);
        chatBody.add(chatSample, BorderLayout.NORTH); // keeps the message at the top
        miniChat.add(chatBody, BorderLayout.CENTER);

        JPanel inputRow = new JPanel(new BorderLayout(4, 0));
        inputRow.setOpaque(false);
        inputRow.add(makeTextField("Type a message..."), BorderLayout.CENTER);
        inputRow.add(makeButton("SEND", true, 4, 8), BorderLayout.EAST);
        miniChat.add(inputRow, BorderLayout.SOUTH);

        alignLeft(miniChat);
        column.add(miniChat);
        return column;
    }

    // =====================================================================
    //  Rows
    // =====================================================================

    private JPanel createScoreRow(String initial, Color avatarColor, String name, int score) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        // Lock height so BoxLayout can't stretch name and score apart
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(4, 8, 4, 8));

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.setOpaque(false);
        who.add(makeAvatar(initial, avatarColor, 24));
        who.add(makeLabel(name, FONT_BODY_BOLD, TEXT_DARK));

        // Labels are centered vertically by default, so the score lines up on its own
        JLabel scoreLabel = makeLabel(String.valueOf(score), FONT_BODY_BOLD, TEXT_DARK);
        scoreLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        scoreLabel.setPreferredSize(new Dimension(24, 24));

        row.add(who, BorderLayout.WEST);
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel createStepRow(int number, String text, boolean active) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        // Lock height so the steps card can't pull the rows apart
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(makeAvatar(String.valueOf(number), active ? PRIMARY_BLUE : STEP_INACTIVE, 20));
        row.add(makeLabel(text,
            active ? FONT_BODY_BOLD : FONT_BODY,
            active ? TEXT_DARK : TEXT_MUTED));
        return row;
    }

    // =====================================================================
    //  State changes
    // =====================================================================

    private void toggleShape(String name) {
        if (selectedShapes.contains(name)) {
            selectedShapes.remove(name);
        } else if (selectedShapes.size() < MAX_SHAPES) {
            selectedShapes.add(name);
        }
        refreshUI();
    }

    private void refreshUI() {
        updateSecretCanvas();

        int count = selectedShapes.size();
        selectedCounterLabel.setText("Selected: " + count + " / " + MAX_SHAPES);

        if (count == MAX_SHAPES) {
            readyBadge.setText("READY TO CONFIRM");
            applyBadgeColors(readyBadge, SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER);
        } else {
            readyBadge.setText("PICK " + (MAX_SHAPES - count) + " MORE");
            applyBadgeColors(readyBadge, WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }

        // Card look lives in background/border, so each card must be refreshed (not just repainted)
        for (ShapeCard card : shapeCards) {
            card.refresh();
        }
    }

    private void updateSecretCanvas() {
        secretSetCanvas.removeAll();
        for (String shapeName : selectedShapes) {
            ShapeImage picture = new ShapeImage(bigImages[shapeIndex(shapeName)], BIG_ICON, shapeName);
            picture.setToolTipText("Click to remove " + shapeName);
            picture.addMouseListener(new RemoveShapeListener(shapeName));
            secretSetCanvas.add(picture);
        }
        secretSetCanvas.revalidate();
        secretSetCanvas.repaint();
    }

    private int shapeIndex(String name) {
        for (int i = 0; i < AVAILABLE_SHAPES.length; i++) {
            if (AVAILABLE_SHAPES[i][0].equalsIgnoreCase(name)) {
                return i;
            }
        }
        return 0;
    }

    // =====================================================================
    //  Picture loading (AWT Toolkit)
    // =====================================================================

    private void loadShapeImages() {
        for (int i = 0; i < AVAILABLE_SHAPES.length; i++) {
            Image source = loadImage(AVAILABLE_SHAPES[i][1]);
            smallImages[i] = scaleImage(source, SMALL_ICON);
            bigImages[i] = scaleImage(source, BIG_ICON);
        }
    }

    /** Returns a fully loaded image, or null when the file cannot be found. */
    private Image loadImage(String filename) {
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        for (String dir : SEARCH_DIRS) {
            File file = new File(dir, filename);
            if (file.isFile()) {
                Image candidate = toolkit.getImage(file.getPath());
                if (waitForImage(candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private Image scaleImage(Image source, int size) {
        if (source == null) {
            return null;
        }
        Image scaled = source.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return waitForImage(scaled) ? scaled : null;
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
    //  Small component factories
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

    private static JLabel makeBadge(String text, Color bg, Color fg, Color border, Font font) {
        JLabel badge = new JLabel(text, SwingConstants.CENTER);
        badge.setFont(font);
        badge.setOpaque(true);
        applyBadgeColors(badge, bg, fg, border);
        return badge;
    }

    private static void applyBadgeColors(JLabel badge, Color bg, Color fg, Color border) {
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setBorder(new CompoundBorder(
            new LineBorder(border, 1),
            new EmptyBorder(BADGE_PAD_V, BADGE_PAD_H, BADGE_PAD_V, BADGE_PAD_H)));
    }

    /** Square colored label with a letter or number, locked to size x size. */
    private static JLabel makeAvatar(String text, Color bg, int size) {
        JLabel avatar = new JLabel(text, SwingConstants.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(bg);
        avatar.setForeground(Color.WHITE);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
        Dimension d = new Dimension(size, size);
        avatar.setPreferredSize(d);
        avatar.setMinimumSize(d);
        avatar.setMaximumSize(d);
        return avatar;
    }

    /**
     * Plain JButton with a flat fill and thin border. The fill changes with
     * hover / press through ButtonStateListener.
     */
    private static JButton makeButton(String text, boolean primary, int padV, int padH) {
        Color normal = primary ? PRIMARY_BLUE : Color.WHITE;
        Color hover = primary ? PRIMARY_HOVER : BG_LIGHT;
        Color pressed = primary ? PRIMARY_PRESSED : SECONDARY_PRESSED;
        Color edge = primary ? PRIMARY_BLUE : CARD_BORDER;

        JButton button = new JButton(text);
        button.setFont(FONT_BODY_BOLD);
        button.setForeground(primary ? Color.WHITE : TEXT_DARK);
        button.setBackground(normal);
        button.setContentAreaFilled(false); // call first: some look and feels reset opaque when it changes
        button.setOpaque(true);
        button.setBorderPainted(true);
        button.setBorder(new CompoundBorder(
            new LineBorder(edge, 1),
            new EmptyBorder(padV, padH, padV, padH)));
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.getModel().addChangeListener(new ButtonStateListener(button, normal, hover, pressed));
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

    private static JComponent alignLeft(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    /** Wraps a component so it stays at its own height and sits in the vertical middle. */
    private static JPanel centerVertically(JComponent component) {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.add(Box.createVerticalGlue());
        wrapper.add(component);
        wrapper.add(Box.createVerticalGlue());
        return wrapper;
    }

    // =====================================================================
    //  Inner classes
    // =====================================================================

    /** Draws one prebuilt picture centered in its area (or a placeholder box when missing). */
    private static class ShapeImage extends JComponent {
        private final Image image;
        private final int size;
        private final String label;

        ShapeImage(Image image, int size, String label) {
            this.image = image;
            this.size = size;
            this.label = label;
            Dimension d = new Dimension(size, size);
            setPreferredSize(d);
            setMinimumSize(d);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            if (image != null) {
                g.drawImage(image, x, y, size, size, this);
                return;
            }

            // Placeholder when the texture file is missing
            g.setColor(new Color(220, 230, 242));
            g.fillRect(x, y, size, size);
            g.setColor(new Color(70, 90, 120));
            g.drawRect(x, y, size - 1, size - 1);
            g.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, size / 6)));
            FontMetrics fm = g.getFontMetrics();
            String text = label.length() > 6 ? label.substring(0, 6) : label;
            int tx = x + (size - fm.stringWidth(text)) / 2;
            int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, tx, ty);
        }
    }

    /** Light canvas with a dashed cross, drawn with short line segments. */
    private static class DashedCanvas extends JPanel {
        DashedCanvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();

            g.setColor(CANVAS_BG);
            g.fillRect(0, 0, w - 1, h - 1);
            g.setColor(CANVAS_LINE);
            g.drawRect(0, 0, w - 1, h - 1);

            int midY = h / 2 + 30;
            int midX = w / 2;
            // dash 6px, gap 4px
            for (int x = 20; x < w - 20; x += 10) {
                g.drawLine(x, midY, Math.min(x + 6, w - 20), midY);
            }
            for (int y = 20; y < h - 20; y += 10) {
                g.drawLine(midX, y, midX, Math.min(y + 6, h - 20));
            }
        }
    }

    /** One clickable card in the Objects grid. Its look follows the selection through refresh(). */
    private class ShapeCard extends JPanel {
        private final String name;
        private final JLabel nameLabel;
        private boolean hovered = false;

        ShapeCard(String name, Image icon) {
            super(new BorderLayout());
            this.name = name;
            setOpaque(true);

            nameLabel = new JLabel(name, SwingConstants.CENTER);
            nameLabel.setFont(FONT_SMALL_BOLD);

            add(new ShapeImage(icon, SMALL_ICON, name), BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleShape(ShapeCard.this.name);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    refresh();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    refresh();
                }
            });

            refresh();
        }

        void refresh() {
            boolean selected = selectedShapes.contains(name);
            // Total inset stays 7px so the card never shifts when the border gets thicker
            int line = selected ? 2 : 1;
            int pad = 7 - line;

            Color bg = selected ? BLUE_LIGHT_BG : (hovered ? HOVER_BG : Color.WHITE);
            Color edge = selected ? PRIMARY_BLUE : (hovered ? BLUE_BORDER : CARD_BORDER);

            setBackground(bg);
            setBorder(new CompoundBorder(
                new LineBorder(edge, line),
                new EmptyBorder(pad, pad, pad, pad)));
            nameLabel.setForeground(selected ? PRIMARY_BLUE : TEXT_DARK);
            repaint();
        }
    }

    /** Click on a shape of the secret canvas removes it. */
    private class RemoveShapeListener extends MouseAdapter {
        private final String shapeName;

        RemoveShapeListener(String shapeName) {
            this.shapeName = shapeName;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            selectedShapes.remove(shapeName);
            refreshUI();
        }
    }

    private class ClearListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent event) {
            selectedShapes.clear();
            refreshUI();
        }
    }

    /** Repaints a button in the right color whenever its model changes. */
    private static class ButtonStateListener implements ChangeListener {
        private final JButton button;
        private final Color normal;
        private final Color hover;
        private final Color pressed;

        ButtonStateListener(JButton button, Color normal, Color hover, Color pressed) {
            this.button = button;
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }

        @Override
        public void stateChanged(ChangeEvent event) {
            ButtonModel model = button.getModel();
            if (model.isPressed() || model.isArmed()) {
                button.setBackground(pressed);
            } else if (model.isRollover()) {
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
                JFrame frame = new JFrame("Guess What It Looks Like? - Questioner Mode");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(1280, 750);
                frame.setLocationRelativeTo(null);
                frame.setContentPane(new QuestionerScreen());
                frame.setVisible(true);
            }
        });
    }
}