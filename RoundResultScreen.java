import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * RoundResultScreen: the scoreboard for ONE round, shown between rounds.
 *
 * It is built from the ROUND_RESULT payload only, so every machine shows the same numbers:
 *   - the secret shapes the questioner picked, as large pictures
 *   - per player: the shapes they guessed (green border = in the secret, red = not),
 *     the points won this round ("BONUS +2" when the speed bonus applied) and the running total
 *
 * Style rules copied from ScoreboardScreen: no lambdas, no ternary expressions and no
 * custom paintComponent - everything is an ordinary JPanel / JLabel with an ImageIcon.
 *
 * Buttons: between rounds only the host may press CONTINUE (it asks the server for the next
 * round). On the last round the button becomes VIEW FINAL SCOREBOARD, which is a local screen
 * switch and needs no network at all.
 */
public class RoundResultScreen extends JPanel {

    // ===== Colors =====
    private static final Color BG_LIGHT      = new Color(241, 245, 249);
    private static final Color CARD_BG       = Color.WHITE;
    private static final Color CARD_BORDER   = new Color(226, 232, 240);
    private static final Color ROW_BG        = new Color(248, 250, 252);
    private static final Color HEADER_TEXT   = new Color(148, 163, 184);

    private static final Color PRIMARY_BLUE  = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER = new Color(29, 78, 216);
    private static final Color PRIMARY_PRESS = new Color(30, 64, 175);

    private static final Color SUCCESS_BG    = new Color(220, 252, 231);
    private static final Color SUCCESS_TEXT  = new Color(22, 101, 52);
    private static final Color SUCCESS_LINE  = new Color(22, 163, 74);

    private static final Color DANGER_BG     = new Color(254, 226, 226);
    private static final Color DANGER_TEXT   = new Color(153, 27, 27);
    private static final Color DANGER_LINE   = new Color(220, 38, 38);

    private static final Color GOLD_BG       = new Color(254, 243, 199);
    private static final Color GOLD_TEXT     = new Color(180, 83, 9);
    private static final Color GOLD_LINE     = new Color(253, 230, 138);

    private static final Color TEXT_DARK     = new Color(15, 23, 42);
    private static final Color TEXT_MUTED    = new Color(100, 116, 139);
    private static final Color PLACEHOLDER_BG = new Color(220, 230, 242);

    // ===== Fonts =====
    private static final Font FONT_TITLE_LARGE = new Font("Segoe UI", Font.BOLD, 24);
    private static final Font FONT_TITLE       = new Font("Segoe UI", Font.BOLD, 18);
    private static final Font FONT_SUBTITLE    = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_BODY        = new Font("Segoe UI", Font.PLAIN, 13);
    private static final Font FONT_BODY_BOLD   = new Font("Segoe UI", Font.BOLD, 13);
    private static final Font FONT_SMALL       = new Font("Segoe UI", Font.PLAIN, 11);
    private static final Font FONT_SMALL_BOLD  = new Font("Segoe UI", Font.BOLD, 11);

    // ===== Sizes =====
    private static final int SECRET_ICON = 84;
    private static final int GUESS_ICON = 26;
    private static final int SECRET_WIDTH = 360;

    /** Avatar colors cycled by row order, matching the lobby palette. */
    private static final Color[] AVATAR_COLORS = {
        new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153),
        new Color(16, 185, 129), new Color(245, 158, 11), new Color(14, 165, 233),
        new Color(244, 63, 94), new Color(99, 102, 241)
    };

    private final ActionListener onContinueAction;
    private final ActionListener onViewFinalAction;

    private boolean hostMode;

    // ===== Widgets rebuilt by showResult() =====
    private JLabel tagLabel;
    private JLabel titleLabel;
    private JLabel questionerLabel;
    private JLabel reasonBadge;
    private JLabel secretTitleLabel;
    private JLabel secretSubLabel;
    private JPanel secretRow;
    private JLabel resultsTitleLabel;
    private JLabel resultsBadge;
    private JPanel rowsPanel;
    private JButton continueBtn;
    private JButton viewFinalBtn;
    private JLabel hintLabel;

    public RoundResultScreen() {
        this(null, null);
    }

    public RoundResultScreen(ActionListener onContinueAction, ActionListener onViewFinalAction) {
        this.onContinueAction = onContinueAction;
        this.onViewFinalAction = onViewFinalAction;

        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        JPanel mainContent = new JPanel(new BorderLayout(24, 16));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(24, 32, 24, 32));
        mainContent.add(buildHeader(), BorderLayout.NORTH);
        mainContent.add(buildColumns(), BorderLayout.CENTER);
        mainContent.add(buildFooter(), BorderLayout.SOUTH);
        add(mainContent, BorderLayout.CENTER);
    }

    // =====================================================================
    //  Header
    // =====================================================================

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titles = new JPanel(new GridLayout(3, 1, 0, 2));
        titles.setOpaque(false);

        tagLabel = new JLabel("ROUND SUMMARY");
        tagLabel.setFont(FONT_SMALL_BOLD);
        tagLabel.setForeground(PRIMARY_BLUE);

        titleLabel = new JLabel("Round 1");
        titleLabel.setFont(FONT_TITLE_LARGE);
        titleLabel.setForeground(TEXT_DARK);

        questionerLabel = new JLabel("Questioner: -");
        questionerLabel.setFont(FONT_BODY);
        questionerLabel.setForeground(TEXT_MUTED);

        titles.add(tagLabel);
        titles.add(titleLabel);
        titles.add(questionerLabel);
        header.add(titles, BorderLayout.WEST);

        reasonBadge = buildBadge("Round complete", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_LINE, 6, 14);
        JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        badgeHolder.setOpaque(false);
        badgeHolder.add(reasonBadge);
        header.add(badgeHolder, BorderLayout.EAST);

        return header;
    }

    // =====================================================================
    //  Columns: secret shapes (left) and the round table (right)
    // =====================================================================

    private JPanel buildColumns() {
        JPanel columns = new JPanel(new BorderLayout(20, 0));
        columns.setOpaque(false);

        JPanel secretCard = buildSecretCard();
        secretCard.setPreferredSize(new Dimension(SECRET_WIDTH, 100));
        columns.add(secretCard, BorderLayout.WEST);
        columns.add(buildResultsCard(), BorderLayout.CENTER);
        return columns;
    }

    private JPanel buildSecretCard() {
        JPanel card = buildCard();
        card.setLayout(new BorderLayout(0, 14));

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        secretTitleLabel = new JLabel("Secret shapes");
        secretTitleLabel.setFont(FONT_SUBTITLE);
        secretTitleLabel.setForeground(TEXT_DARK);
        secretSubLabel = new JLabel("Chosen by -");
        secretSubLabel.setFont(FONT_SMALL);
        secretSubLabel.setForeground(TEXT_MUTED);
        titles.add(secretTitleLabel);
        titles.add(secretSubLabel);
        card.add(titles, BorderLayout.NORTH);

        secretRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        secretRow.setOpaque(false);
        JPanel secretSlot = new JPanel(new BorderLayout());
        secretSlot.setOpaque(false);
        secretSlot.add(secretRow, BorderLayout.NORTH);
        card.add(secretSlot, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildResultsCard() {
        JPanel card = buildCard();
        card.setLayout(new BorderLayout(0, 14));

        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        resultsTitleLabel = new JLabel("Round score");
        resultsTitleLabel.setFont(FONT_TITLE);
        resultsTitleLabel.setForeground(TEXT_DARK);
        titles.add(resultsTitleLabel, BorderLayout.WEST);
        resultsBadge = buildBadge("-", BG_LIGHT, TEXT_DARK, CARD_BORDER, 4, 10);
        JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeHolder.setOpaque(false);
        badgeHolder.add(resultsBadge);
        titles.add(badgeHolder, BorderLayout.EAST);
        card.add(titles, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(buildTableHeader(), BorderLayout.NORTH);

        rowsPanel = new JPanel(new GridLayout(0, 1, 0, 6));
        rowsPanel.setOpaque(false);
        body.add(rowsPanel, BorderLayout.CENTER);
        card.add(body, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildTableHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 13, 0, 13));

        JLabel playerHeader = new JLabel("PLAYER");
        playerHeader.setFont(FONT_SMALL_BOLD);
        playerHeader.setForeground(HEADER_TEXT);

        JLabel guessHeader = new JLabel("GUESSED");
        guessHeader.setFont(FONT_SMALL_BOLD);
        guessHeader.setForeground(HEADER_TEXT);

        JLabel roundHeader = new JLabel("ROUND");
        roundHeader.setFont(FONT_SMALL_BOLD);
        roundHeader.setForeground(HEADER_TEXT);
        roundHeader.setPreferredSize(new Dimension(86, 18));
        roundHeader.setHorizontalAlignment(JLabel.RIGHT);

        JLabel totalHeader = new JLabel("TOTAL");
        totalHeader.setFont(FONT_SMALL_BOLD);
        totalHeader.setForeground(HEADER_TEXT);
        totalHeader.setPreferredSize(new Dimension(50, 18));
        totalHeader.setHorizontalAlignment(JLabel.RIGHT);

        header.add(playerHeader, BorderLayout.WEST);
        header.add(guessHeader, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(roundHeader);
        right.add(totalHeader);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    // =====================================================================
    //  Footer buttons
    // =====================================================================

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);

        hintLabel = new JLabel("Waiting for the host...");
        hintLabel.setFont(FONT_SMALL);
        hintLabel.setForeground(TEXT_MUTED);
        footer.add(hintLabel, BorderLayout.WEST);

        continueBtn = buildButton("CONTINUE", PRIMARY_BLUE, PRIMARY_HOVER, PRIMARY_PRESS, Color.WHITE);
        continueBtn.setPreferredSize(new Dimension(190, 44));
        continueBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (onContinueAction != null) {
                    onContinueAction.actionPerformed(event);
                }
            }
        });

        viewFinalBtn = buildButton("VIEW FINAL SCOREBOARD", PRIMARY_BLUE, PRIMARY_HOVER, PRIMARY_PRESS, Color.WHITE);
        viewFinalBtn.setPreferredSize(new Dimension(230, 44));
        viewFinalBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if (onViewFinalAction != null) {
                    onViewFinalAction.actionPerformed(event);
                }
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        buttons.add(continueBtn);
        buttons.add(viewFinalBtn);
        footer.add(buttons, BorderLayout.EAST);

        return footer;
    }

    // =====================================================================
    //  Public API used by MainApp
    // =====================================================================

    /** Only the host may press CONTINUE between rounds; the last round ignores this. */
    public void setHostMode(boolean host) {
        hostMode = host;
    }

    /**
     * Draws the whole screen from one ROUND_RESULT. The arrays are parallel and every one of
     * them has one entry per player; guesses[i] is empty when that player did not submit.
     */
    public void showResult(int round, int totalRounds, boolean isFinal, String[] secret, String reason,
                           String questioner, String[] names, int[] deltas, int[] totals,
                           String[][] guesses, int[] correctCounts, boolean[] bonuses) {
        String questionerName = (questioner == null) ? "" : questioner;
        titleLabel.setText("Round " + round + " of " + totalRounds);
        questionerLabel.setText("Questioner: " + (questionerName.isEmpty() ? "-" : questionerName));
        tagLabel.setText(isFinal ? "FINAL ROUND SUMMARY" : "ROUND SUMMARY");

        if ("time".equals(reason)) {
            setBadge(reasonBadge, "Time is up", DANGER_BG, DANGER_TEXT, DANGER_LINE);
        } else if ("all".equals(reason)) {
            setBadge(reasonBadge, "All guesses in", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_LINE);
        } else {
            setBadge(reasonBadge, "Round complete", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_LINE);
        }

        secretSubLabel.setText("Chosen by " + (questionerName.isEmpty() ? "-" : questionerName));
        secretRow.removeAll();
        if (secret != null && secret.length > 0) {
            for (String shapeName : secret) {
                secretRow.add(shapeIcon(shapeName, SECRET_ICON, CARD_BORDER, 1));
            }
            secretTitleLabel.setText("Secret shapes (" + secret.length + ")");
        } else {
            JLabel none = new JLabel("No secret shapes were locked in this round.");
            none.setFont(FONT_BODY);
            none.setForeground(TEXT_MUTED);
            secretRow.add(none);
            secretTitleLabel.setText("Secret shapes");
        }
        secretRow.revalidate();
        secretRow.repaint();

        resultsBadge.setText(names.length + (names.length == 1 ? " player" : " players"));

        rowsPanel.removeAll();
        rowsPanel.setLayout(new GridLayout(Math.max(1, names.length), 1, 0, 6));
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            int delta = (i < deltas.length) ? deltas[i] : 0;
            int total = (i < totals.length) ? totals[i] : 0;
            String[] guess = (i < guesses.length) ? guesses[i] : new String[0];
            int correct = (i < correctCounts.length) ? correctCounts[i] : 0;
            boolean bonus = (i < bonuses.length) && bonuses[i];
            boolean isQuestioner = name.equals(questionerName);
            rowsPanel.add(buildPlayerRow(i, name, delta, total, guess, correct, bonus, isQuestioner, secret));
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();

        if (isFinal) {
            continueBtn.setVisible(false);
            viewFinalBtn.setVisible(true);
            hintLabel.setText("The tournament is over - open the final standings.");
        } else {
            viewFinalBtn.setVisible(false);
            continueBtn.setVisible(true);
            if (hostMode) {
                continueBtn.setEnabled(true);
                hintLabel.setText("Press CONTINUE for the lobby, then start the next round.");
            } else {
                continueBtn.setEnabled(false);
                hintLabel.setText("Waiting for the host to continue...");
            }
        }

        revalidate();
        repaint();
    }

    // =====================================================================
    //  Rows
    // =====================================================================

    private JPanel buildPlayerRow(int index, String name, int delta, int total, String[] guess,
                                  int correct, boolean bonus, boolean isQuestioner, String[] secret) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(true);
        row.setBackground(ROW_BG);
        row.setBorder(new CompoundBorder(
            new LineBorder(ROW_BG, 1),
            new EmptyBorder(6, 12, 6, 12)));

        // --- who ---
        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.setOpaque(false);
        who.add(buildAvatar(initialOf(name), avatarColor(index), 26));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FONT_BODY_BOLD);
        nameLabel.setForeground(TEXT_DARK);
        who.add(nameLabel);

        if (isQuestioner) {
            who.add(buildBadge("Questioner", new Color(226, 232, 240), TEXT_DARK, CARD_BORDER, 2, 8));
        }
        row.add(who, BorderLayout.WEST);

        // --- guessed shapes ---
        JPanel guessed = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        guessed.setOpaque(false);
        if (isQuestioner) {
            guessed.add(mutedLabel("picked the secret"));
        } else if (guess.length == 0) {
            guessed.add(mutedLabel("did not guess"));
        } else {
            for (String shapeName : guess) {
                boolean hit = containsShape(secret, shapeName);
                if (hit) {
                    guessed.add(shapeIcon(shapeName, GUESS_ICON, SUCCESS_LINE, 2));
                } else {
                    guessed.add(shapeIcon(shapeName, GUESS_ICON, DANGER_LINE, 2));
                }
            }
            guessed.add(mutedLabel(correct + " / " + guess.length + " correct"));
        }
        row.add(guessed, BorderLayout.CENTER);

        // --- points this round + running total ---
        JPanel score = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        score.setOpaque(false);
        if (bonus) {
            score.add(buildBadge("BONUS +2", GOLD_BG, GOLD_TEXT, GOLD_LINE, 3, 8));
        }
        score.add(buildDeltaBadge(delta));

        JLabel totalLabel = new JLabel(String.valueOf(total));
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        totalLabel.setPreferredSize(new Dimension(46, 24));
        totalLabel.setHorizontalAlignment(JLabel.RIGHT);
        if (total > 0) {
            totalLabel.setForeground(PRIMARY_BLUE);
        } else {
            totalLabel.setForeground(TEXT_MUTED);
        }
        score.add(totalLabel);
        row.add(score, BorderLayout.EAST);

        return row;
    }

    private JLabel buildDeltaBadge(int delta) {
        String text = "+" + delta;
        if (delta > 0) {
            return buildBadge(text, SUCCESS_BG, SUCCESS_TEXT, SUCCESS_LINE, 3, 10);
        }
        return buildBadge("+0", BG_LIGHT, TEXT_MUTED, CARD_BORDER, 3, 10);
    }

    private static boolean containsShape(String[] shapes, String wanted) {
        if (shapes == null || wanted == null) {
            return false;
        }
        for (String shape : shapes) {
            if (wanted.equalsIgnoreCase(shape)) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================
    //  Small factories
    // =====================================================================

    private static JPanel buildCard() {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(CARD_BG);
        card.setBorder(new CompoundBorder(
            new LineBorder(CARD_BORDER, 1),
            new EmptyBorder(18, 20, 18, 20)));
        return card;
    }

    private static JLabel buildBadge(String text, Color bg, Color fg, Color border, int padV, int padH) {
        JLabel badge = new JLabel(text, JLabel.CENTER);
        badge.setFont(FONT_SMALL_BOLD);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(new CompoundBorder(
            new LineBorder(border, 1),
            new EmptyBorder(padV, padH, padV, padH)));
        return badge;
    }

    private static void setBadge(JLabel badge, String text, Color bg, Color fg, Color border) {
        badge.setText(text);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(new CompoundBorder(
            new LineBorder(border, 1),
            new EmptyBorder(6, 14, 6, 14)));
        badge.repaint();
    }

    private static JLabel buildAvatar(String initial, Color color, int size) {
        JLabel avatar = new JLabel(initial, JLabel.CENTER);
        avatar.setFont(new Font("Segoe UI", Font.BOLD, size / 2));
        avatar.setForeground(Color.WHITE);
        avatar.setBackground(color);
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(size, size));
        return avatar;
    }

    private static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_SMALL);
        label.setForeground(TEXT_MUTED);
        return label;
    }

    /** One shape picture with a coloured border; a grey named box when the PNG is missing. */
    private static JLabel shapeIcon(String shapeName, int size, Color borderColor, int borderWidth) {
        ImageIcon icon = ShapeImages.icon(shapeName, size);
        JLabel label;
        if (icon != null) {
            label = new JLabel(icon);
        } else {
            label = new JLabel(shapeName, JLabel.CENTER);
            label.setFont(FONT_SMALL_BOLD);
            label.setForeground(TEXT_MUTED);
            label.setOpaque(true);
            label.setBackground(PLACEHOLDER_BG);
            label.setPreferredSize(new Dimension(size, size));
        }
        label.setToolTipText(shapeName);
        if (borderColor != null) {
            label.setBorder(new LineBorder(borderColor, borderWidth));
        }
        return label;
    }

    private static JButton buildButton(String text, Color normal, Color hover, Color pressed, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(fg);
        btn.setBackground(normal);
        btn.setContentAreaFilled(false); // call before setOpaque: some look and feels reset opaque
        btn.setOpaque(true);
        btn.setBorderPainted(true);
        btn.setBorder(new CompoundBorder(
            new LineBorder(normal, 1),
            new EmptyBorder(10, 18, 10, 18)));
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.setRolloverEnabled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.getModel().addChangeListener(new ButtonStateListener(btn, normal, hover, pressed));
        return btn;
    }

    private static String initialOf(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase();
    }

    private static Color avatarColor(int index) {
        return AVATAR_COLORS[Math.abs(index) % AVATAR_COLORS.length];
    }

    // =====================================================================

    /** Repaints a button in the right colour for pressed / hover / normal. */
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
}
