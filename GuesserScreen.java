import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.*;

/**
 * GuesserScreen: Screen 4 - Guesser Mode where players try to guess the secret shapes.
 *
 * Remake: built only from standard Swing components (JPanel / JLabel / JButton)
 * and standard borders. Layout is done with BorderLayout, GridLayout and FlowLayout only.
 *
 * Layout:
 *   Left   (WEST)   - Scoreboard card + Scoring guide card, stacked at the top.
 *   Center (CENTER) - "Your guess" card. Inner stage = 3 equal rows:
 *                     correct banner (top-right), guess slots (middle), incorrect banner (bottom-left).
 *   Right  (EAST)   - Objects picker card (2x4 grid + submit button) with the ENTER hint below.
 *
 * Stacking trick used everywhere: a card placed at BorderLayout.NORTH keeps its
 * preferred height, and the leftover space below stays empty.
 */
public class GuesserScreen extends JPanel {
    private static final int MAX_PICKS = 3;

    // ==================== Colors ====================
    private static final Color BG_LIGHT       = new Color(241, 245, 249);
    private static final Color CARD_BG        = Color.WHITE;
    private static final Color CARD_BORDER    = new Color(226, 232, 240);
    private static final Color STAGE_BG       = new Color(247, 250, 255);
    private static final Color STAGE_BORDER   = new Color(226, 232, 240);

    private static final Color PRIMARY_BLUE   = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER  = new Color(29, 78, 216);
    private static final Color BLUE_LIGHT_BG  = new Color(239, 246, 255);
    private static final Color BLUE_BORDER    = new Color(191, 219, 254);

    private static final Color SUCCESS_BG     = new Color(220, 252, 231);
    private static final Color SUCCESS_TEXT   = new Color(22, 101, 52);
    private static final Color SUCCESS_BORDER = new Color(187, 247, 208);

    private static final Color WARNING_BG     = new Color(254, 249, 195);
    private static final Color WARNING_TEXT   = new Color(133, 77, 14);
    private static final Color WARNING_BORDER = new Color(254, 240, 138);

    private static final Color DANGER_BG      = new Color(254, 226, 226);
    private static final Color DANGER_TEXT    = new Color(153, 27, 27);
    private static final Color DANGER_BORDER  = new Color(254, 202, 202);

    private static final Color GOLD_BG        = new Color(254, 243, 199);
    private static final Color GOLD_TEXT      = new Color(180, 83, 9);
    private static final Color GOLD_BORDER    = new Color(253, 230, 138);

    private static final Color TEXT_DARK      = new Color(15, 23, 42);
    private static final Color TEXT_MUTED     = new Color(100, 116, 139);

    private static final Color EMPTY_SLOT_BG     = new Color(248, 250, 252);
    private static final Color EMPTY_SLOT_BORDER = new Color(203, 213, 225);
    private static final Color KEYCAP_TEXT       = new Color(51, 65, 85);

    // ==================== Fonts ====================
    private static final Font FONT_TITLE      = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font FONT_SUBTITLE   = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_BODY       = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BODY_BOLD  = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_SMALL      = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    private static final Font FONT_KEYCAP     = new Font("Segoe UI", Font.BOLD, 9);

    // ==================== State ====================
    private final List<String> guessedShapes = new ArrayList<>(Arrays.asList("Circle", "Triangle", "Diamond"));
    private JPanel guessSlotsPanel;
    private JLabel selectedBadge;
    private JLabel pickCounterLabel;
    private JButton submitBtn;
    private final Map<String, PickerItem> shapeCards = new HashMap<>();
    private final ActionListener onSubmitAction;

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

    public GuesserScreen() {
        this(null);
    }

    public GuesserScreen(ActionListener onSubmitAction) {
        this.onSubmitAction = onSubmitAction;
        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        JPanel mainContent = new JPanel(new BorderLayout(14, 14));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(14, 24, 14, 24));

        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(createColumns(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        installEnterShortcut();
    }

    // ==================== Sub header + columns ====================

    private JPanel createSubHeader() {
        JPanel subHeader = new JPanel(new BorderLayout());
        subHeader.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);

        JLabel title = new JLabel("Guess what the Questioner selected!");
        title.setFont(FONT_TITLE);
        title.setForeground(TEXT_DARK);

        JLabel sub = new JLabel("Choose the same three shapes before time runs out.");
        sub.setFont(FONT_BODY);
        sub.setForeground(TEXT_MUTED);

        titles.add(title);
        titles.add(sub);
        subHeader.add(titles, BorderLayout.WEST);

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        badges.setOpaque(false);
        badges.add(createBadge("Target: Guess 3 Shapes", BLUE_LIGHT_BG, PRIMARY_BLUE, BLUE_BORDER));
        badges.add(createBadge("Time: 00:30", WARNING_BG, WARNING_TEXT, WARNING_BORDER));
        badges.add(createBadge("Score: 0", BG_LIGHT, TEXT_DARK, CARD_BORDER));
        badges.add(createBadge("Correct: 0/3", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER));
        subHeader.add(badges, BorderLayout.EAST);

        return subHeader;
    }

    private JPanel createColumns() {
        JPanel columns = new JPanel(new BorderLayout(14, 0));
        columns.setOpaque(false);

        JPanel left = createLeftPanel();
        left.setPreferredSize(new Dimension(240, 0));

        JPanel right = createRightPanel();
        right.setPreferredSize(new Dimension(300, 0));

        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);
        return columns;
    }

    // ==================== Left column ====================

    private JPanel createLeftPanel() {
        JPanel left = new JPanel(new BorderLayout(0, 12));
        left.setOpaque(false);

        // --- Card 1: Scoreboard ---
        JPanel scoreCard = createCard();
        scoreCard.setLayout(new BorderLayout(0, 12));
        scoreCard.add(createHeader("Scoreboard", FONT_SUBTITLE, "Round 1 of 4", FONT_SMALL, null),
            BorderLayout.NORTH);

        JPanel scoreRows = new JPanel(new GridLayout(0, 1, 0, 8));
        scoreRows.setOpaque(false);
        scoreRows.add(createScoreRow("A", new Color(37, 99, 235), "Aim", 0, true));
        scoreRows.add(createScoreRow("P", new Color(139, 92, 246), "Player 2", 2, false));
        scoreRows.add(createScoreRow("P", new Color(236, 72, 153), "Player 3", 1, false));
        scoreCard.add(scoreRows, BorderLayout.CENTER);

        // --- Card 2: Scoring guide ---
        JPanel guideCard = createCard();
        guideCard.setLayout(new BorderLayout(0, 12));
        guideCard.add(createHeader("Scoring", FONT_SUBTITLE, null, null, null), BorderLayout.NORTH);

        JPanel ruleRows = new JPanel(new GridLayout(0, 1, 0, 8));
        ruleRows.setOpaque(false);
        ruleRows.add(createRuleRow("+1", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER, "for each correct shape"));
        ruleRows.add(createRuleRow("+2", GOLD_BG, GOLD_TEXT, GOLD_BORDER, "all correct under 10 sec"));
        ruleRows.add(createRuleRow("0", DANGER_BG, DANGER_TEXT, DANGER_BORDER, "for an incorrect shape"));
        guideCard.add(ruleRows, BorderLayout.CENTER);

        // Both cards keep their natural height; leftover space stays empty at the bottom
        JPanel leftRest = new JPanel(new BorderLayout());
        leftRest.setOpaque(false);
        leftRest.add(guideCard, BorderLayout.NORTH);

        left.add(scoreCard, BorderLayout.NORTH);
        left.add(leftRest, BorderLayout.CENTER);
        return left;
    }

    private JPanel createScoreRow(String initial, Color avatarColor, String name, int score, boolean highlight) {
        JPanel row = new JPanel(new BorderLayout());
        if (highlight) {
            row.setOpaque(true);
            row.setBackground(BLUE_LIGHT_BG);
            row.setBorder(createBoxBorder(BLUE_BORDER, 1, 4, 8));
        } else {
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(5, 9, 5, 9)); // same total insets as the highlighted row
        }

        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.setOpaque(false);
        who.add(createAvatar(initial, avatarColor, 24));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FONT_BODY_BOLD);
        nameLabel.setForeground(TEXT_DARK);
        who.add(nameLabel);

        JLabel scoreLabel = new JLabel(String.valueOf(score), SwingConstants.RIGHT);
        scoreLabel.setFont(FONT_BODY_BOLD);
        scoreLabel.setForeground(TEXT_DARK);

        row.add(who, BorderLayout.WEST);
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    private JPanel createRuleRow(String badgeText, Color bg, Color fg, Color border, String ruleText) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);

        JLabel badge = createBadge(badgeText, bg, fg, border);
        badge.setBorder(createBoxBorder(border, 1, 2, 6));
        badge.setPreferredSize(new Dimension(34, 22));

        JLabel label = new JLabel(ruleText);
        label.setFont(FONT_SMALL);
        label.setForeground(TEXT_DARK);

        row.add(badge);
        row.add(label);
        return row;
    }

    // ==================== Center column ====================

    private JPanel createCenterPanel() {
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);

        JPanel centerCard = createCard();
        centerCard.setLayout(new BorderLayout(0, 12));

        selectedBadge = createBadge(guessedShapes.size() + " / " + MAX_PICKS + " selected",
            BLUE_LIGHT_BG, PRIMARY_BLUE, BLUE_BORDER);

        centerCard.add(createHeader("Your guess", FONT_TITLE,
            "Your selected shapes appear here", FONT_BODY, selectedBadge), BorderLayout.NORTH);
        centerCard.add(createStagePanel(), BorderLayout.CENTER);

        // Helper hint sits outside the card, under its left edge
        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hintRow.setOpaque(false);

        JLabel tip = new JLabel("TIP");
        tip.setFont(FONT_SMALL_BOLD);
        tip.setForeground(WARNING_TEXT);

        JLabel hintLabel = new JLabel("You can change your choices until you submit.");
        hintLabel.setFont(FONT_SMALL);
        hintLabel.setForeground(TEXT_MUTED);

        hintRow.add(tip);
        hintRow.add(hintLabel);

        center.add(centerCard, BorderLayout.CENTER);
        center.add(hintRow, BorderLayout.SOUTH);
        return center;
    }

    /**
     * Tinted inner stage split into three equal rows, so the guess slots
     * always sit in the vertical middle without any glue/filler components.
     */
    private JPanel createStagePanel() {
        JPanel stage = new JPanel(new GridLayout(3, 1, 0, 0));
        stage.setOpaque(true);
        stage.setBackground(STAGE_BG);
        stage.setBorder(createBoxBorder(STAGE_BORDER, 1, 14, 14));

        // Row 1: correct banner pinned top-right
        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(createBannerSlot(
            createAlertBanner("Correct! +1 Point", "Circle matches the secret set.",
                SUCCESS_BG, SUCCESS_BORDER, SUCCESS_TEXT),
            FlowLayout.RIGHT), BorderLayout.NORTH);

        // Row 2: three guess slots
        guessSlotsPanel = new JPanel(new GridLayout(1, MAX_PICKS, 14, 0));
        guessSlotsPanel.setOpaque(false);
        updateGuessSlots();

        JPanel middleRow = new JPanel(new BorderLayout());
        middleRow.setOpaque(false);
        middleRow.setBorder(new EmptyBorder(6, 24, 6, 24));
        middleRow.add(guessSlotsPanel, BorderLayout.CENTER);

        // Row 3: incorrect banner pinned bottom-left
        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.add(createBannerSlot(
            createAlertBanner("Incorrect!", "Diamond is not in the secret set.",
                DANGER_BG, DANGER_BORDER, DANGER_TEXT),
            FlowLayout.LEFT), BorderLayout.SOUTH);

        stage.add(topRow);
        stage.add(middleRow);
        stage.add(bottomRow);
        return stage;
    }

    /** Wraps a banner so it hugs its content while still spanning the stage width. */
    private JPanel createBannerSlot(JPanel banner, int alignment) {
        JPanel slot = new JPanel(new FlowLayout(alignment, 0, 0));
        slot.setOpaque(false);
        slot.add(banner);
        return slot;
    }

    private JPanel createAlertBanner(String title, String desc, Color bg, Color border, Color fg) {
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        banner.setOpaque(true);
        banner.setBackground(bg);
        banner.setBorder(createBoxBorder(border, 1, 6, 8));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(FONT_BODY_BOLD);
        titleLabel.setForeground(fg);

        JLabel descLabel = new JLabel(desc);
        descLabel.setFont(FONT_SMALL);
        descLabel.setForeground(fg);

        banner.add(titleLabel);
        banner.add(descLabel);
        return banner;
    }

    private void updateGuessSlots() {
        guessSlotsPanel.removeAll();
        for (int i = 0; i < MAX_PICKS; i++) {
            if (i < guessedShapes.size()) {
                String shapeName = guessedShapes.get(i);
                guessSlotsPanel.add(createBigShapeCard(shapeName, getFileNameForShape(shapeName)));
            } else {
                guessSlotsPanel.add(createEmptySlot());
            }
        }
        guessSlotsPanel.revalidate();
        guessSlotsPanel.repaint();
    }

    private JPanel createBigShapeCard(String name, String filename) {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setOpaque(true);
        card.setBackground(Color.WHITE);
        card.setBorder(createBoxBorder(PRIMARY_BLUE, 2, 14, 10));

        JLabel iconLabel = new JLabel(TextureManager.getIcon(filename, 78, 78), SwingConstants.CENTER);
        JLabel textLabel = new JLabel(name, SwingConstants.CENTER);
        textLabel.setFont(FONT_BODY_BOLD);
        textLabel.setForeground(TEXT_DARK);

        card.add(iconLabel, BorderLayout.CENTER);
        card.add(textLabel, BorderLayout.SOUTH);
        return card;
    }

    private JPanel createEmptySlot() {
        JPanel slot = new JPanel(new BorderLayout());
        slot.setOpaque(true);
        slot.setBackground(EMPTY_SLOT_BG);
        slot.setBorder(new LineBorder(EMPTY_SLOT_BORDER, 2));

        JLabel label = new JLabel("Empty", SwingConstants.CENTER);
        label.setFont(FONT_SMALL);
        label.setForeground(TEXT_MUTED);
        slot.add(label, BorderLayout.CENTER);
        return slot;
    }

    // ==================== Right column ====================

    private JPanel createRightPanel() {
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);

        JPanel objectsCard = createCard();
        objectsCard.setLayout(new BorderLayout(0, 12));

        pickCounterLabel = new JLabel(guessedShapes.size() + " / " + MAX_PICKS);
        pickCounterLabel.setFont(FONT_BODY_BOLD);
        pickCounterLabel.setForeground(PRIMARY_BLUE);

        objectsCard.add(createHeader("Objects", FONT_SUBTITLE,
            "Pick " + MAX_PICKS + " shapes", FONT_BODY, pickCounterLabel), BorderLayout.NORTH);

        // Shapes grid with a fixed height so the card keeps its original size
        JPanel grid = new JPanel(new GridLayout(4, 2, 8, 8));
        grid.setOpaque(false);
        grid.setPreferredSize(new Dimension(0, 316));
        for (String[] shape : AVAILABLE_SHAPES) {
            PickerItem item = new PickerItem(shape[0], shape[1]);
            shapeCards.put(shape[0], item);
            grid.add(item);
        }
        objectsCard.add(grid, BorderLayout.CENTER);

        submitBtn = createPrimaryButton("SUBMIT GUESS");
        submitBtn.setPreferredSize(new Dimension(0, 44));
        if (onSubmitAction != null) {
            submitBtn.addActionListener(onSubmitAction);
        }
        objectsCard.add(submitBtn, BorderLayout.SOUTH);

        // Keyboard hint right below the card
        JPanel hintRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        hintRow.setOpaque(false);
        hintRow.add(createKeyCap("ENTER"));

        JLabel hintLabel = new JLabel("to submit your guess");
        hintLabel.setFont(FONT_SMALL);
        hintLabel.setForeground(TEXT_MUTED);
        hintRow.add(hintLabel);

        JPanel rightRest = new JPanel(new BorderLayout());
        rightRest.setOpaque(false);
        rightRest.add(hintRow, BorderLayout.NORTH);

        right.add(objectsCard, BorderLayout.NORTH);
        right.add(rightRest, BorderLayout.CENTER);
        return right;
    }

    /** One cell of the Objects picker: icon + label, restyled on selection. */
    private class PickerItem extends JPanel {
        private final String shapeName;
        private final JLabel nameLabel;

        PickerItem(String shapeName, String filename) {
            this.shapeName = shapeName;
            setLayout(new BorderLayout());
            setOpaque(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            JLabel iconLabel = new JLabel(TextureManager.getIcon(filename, 34, 34), SwingConstants.CENTER);
            nameLabel = new JLabel(shapeName, SwingConstants.CENTER);
            nameLabel.setFont(FONT_SMALL_BOLD);

            add(iconLabel, BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleShape(PickerItem.this.shapeName);
                }
            });

            applySelectionStyle();
        }

        void applySelectionStyle() {
            boolean selected = guessedShapes.contains(shapeName);
            setBackground(selected ? BLUE_LIGHT_BG : Color.WHITE);
            // Border thickness + padding always add up to 8px, so the tile never jumps in size
            setBorder(selected
                ? createBoxBorder(PRIMARY_BLUE, 2, 6, 4)
                : createBoxBorder(CARD_BORDER, 1, 7, 5));
            nameLabel.setForeground(selected ? PRIMARY_BLUE : TEXT_DARK);
            repaint();
        }
    }

    private void toggleShape(String shapeName) {
        if (guessedShapes.contains(shapeName)) {
            guessedShapes.remove(shapeName);
        } else if (guessedShapes.size() < MAX_PICKS) {
            guessedShapes.add(shapeName);
        }
        refreshSelection();
    }

    /** Re-syncs every widget that depends on how many shapes are currently picked. */
    private void refreshSelection() {
        updateGuessSlots();
        if (selectedBadge != null) {
            selectedBadge.setText(guessedShapes.size() + " / " + MAX_PICKS + " selected");
        }
        if (pickCounterLabel != null) {
            pickCounterLabel.setText(guessedShapes.size() + " / " + MAX_PICKS);
        }
        for (PickerItem item : shapeCards.values()) {
            item.applySelectionStyle();
        }
    }

    /** ENTER submits the guess while this screen is showing. */
    private void installEnterShortcut() {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submitGuess");
        getActionMap().put("submitGuess", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (submitBtn != null) {
                    submitBtn.doClick();
                }
            }
        });
    }

    // ==================== Shared helpers ====================

    private static CompoundBorder createBoxBorder(Color line, int thickness, int vPad, int hPad) {
        return new CompoundBorder(new LineBorder(line, thickness), new EmptyBorder(vPad, hPad, vPad, hPad));
    }

    /** White card with a thin border and padding. Caller picks the layout. */
    private static JPanel createCard() {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(createBoxBorder(CARD_BORDER, 1, 16, 16));
        return card;
    }

    /** Small label with fill + border. Use setText() to update it later. */
    private static JLabel createBadge(String text, Color bg, Color fg, Color border) {
        JLabel badge = new JLabel(text, SwingConstants.CENTER);
        badge.setFont(FONT_SMALL_BOLD);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(createBoxBorder(border, 1, 4, 10));
        return badge;
    }

    /** Square avatar tile with the player's initial. */
    private static JLabel createAvatar(String letter, Color bg, int size) {
        JLabel avatar = new JLabel(letter, SwingConstants.CENTER);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(bg);
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(size, size));
        return avatar;
    }

    private static JLabel createKeyCap(String text) {
        JLabel key = new JLabel(text, SwingConstants.CENTER);
        key.setFont(FONT_KEYCAP);
        key.setForeground(KEYCAP_TEXT);
        key.setBackground(Color.WHITE);
        key.setOpaque(true);
        key.setBorder(createBoxBorder(EMPTY_SLOT_BORDER, 1, 3, 6));
        return key;
    }

    private static JButton createPrimaryButton(String text) {
        final JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(Color.WHITE);
        btn.setBackground(PRIMARY_BLUE);
        btn.setOpaque(true);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 18, 10, 18));
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(PRIMARY_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(PRIMARY_BLUE);
            }
        });
        return btn;
    }

    /**
     * Two-line card header: bold title with an optional muted sub line under it
     * and an optional widget pinned to the right of the title line.
     */
    private static JPanel createHeader(String title, Font titleFont, String sub, Font subFont, JComponent right) {
        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleFont);
        titleLabel.setForeground(TEXT_DARK);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);

        if (right != null) {
            JPanel rightWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            rightWrap.setOpaque(false);
            rightWrap.add(right);
            titleRow.add(rightWrap, BorderLayout.EAST);
        }
        header.add(titleRow, BorderLayout.NORTH);

        if (sub != null) {
            JLabel subLabel = new JLabel(sub);
            subLabel.setFont(subFont);
            subLabel.setForeground(TEXT_MUTED);
            header.add(subLabel, BorderLayout.CENTER);
        }
        return header;
    }

    private String getFileNameForShape(String name) {
        for (String[] s : AVAILABLE_SHAPES) {
            if (s[0].equalsIgnoreCase(name)) return s[1];
        }
        return "Circle.png";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Guess What It Looks Like? - Guesser Mode");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1280, 750);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(new GuesserScreen());
        frame.setVisible(true);
    }
}