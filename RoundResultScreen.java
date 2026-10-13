import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * RoundResultScreen: the scoreboard for ONE round, shown between rounds.
 *
 * It is built from the ROUND_RESULT payload only, so every machine shows the same numbers:
 *   - the secret shapes the questioner picked, as large pictures in ONE full-width row on top
 *   - below it, the round table: per player (in names[] order) the shapes they guessed
 *     (green border = in the secret, red = not), the points won this round ("BONUS +2" when the
 *     speed bonus applied) and the running total, inside a scroll pane so no player is cut off
 *
 * Style rules copied from ScoreboardScreen: no lambdas, no ternary expressions and no
 * custom paintComponent - everything is an ordinary JPanel / JLabel with an ImageIcon.
 *
 * There are no buttons. The screen runs a 10-second countdown with a javax.swing.Timer; the
 * server owns the clock too and starts the next round (or the final scoreboard) on its own,
 * so this countdown is only the visible half of that wait.
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
    private static final int SECRET_ICON = 110;
    private static final int SECRET_GAP  = 16;
    private static final int GUESS_ICON  = 42;
    private static final int AVATAR_SIZE = 34;
    private static final int ROW_GAP     = 6;

    /** Card title; the thumbs-down is written as escapes so the source stays plain ASCII. */
    private static final String SECRET_TITLE = "Secret shapes \uD83D\uDC4E";

    /** Avatar colors cycled by row order, matching the lobby palette. */
    private static final Color[] AVATAR_COLORS = {
        new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153),
        new Color(16, 185, 129), new Color(245, 158, 11), new Color(14, 165, 233),
        new Color(244, 63, 94), new Color(99, 102, 241)
    };

    // ===== Countdown =====
    private static final int COUNTDOWN_SECONDS = GameServer.INTERMISSION_SECONDS;

    /** Notified on the EDT when the countdown reaches zero (used for the final switch). */
    private final ActionListener onCountdownFinished;

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
    private JLabel countdownLabel;

    // ===== Countdown state (EDT only) =====
    private javax.swing.Timer countdownTimer;
    private int secondsLeft;

    public RoundResultScreen() {
        this(null);
    }

    public RoundResultScreen(ActionListener onCountdownFinished) {
        this.onCountdownFinished = onCountdownFinished;

        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        JPanel mainContent = new JPanel(new BorderLayout(24, 16));
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(24, 32, 24, 32));
        mainContent.add(buildHeader(), BorderLayout.NORTH);
        mainContent.add(buildBody(), BorderLayout.CENTER);
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
    //  Body: the secret shapes on top, the round table under it
    // =====================================================================

    /** Secret card (top, full width) over the round score card (below, full width). */
    private JPanel buildBody() {
        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(buildSecretCard(), BorderLayout.NORTH);
        body.add(buildResultsCard(), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildSecretCard() {
        JPanel card = buildCard();
        card.setLayout(new BorderLayout(0, 14));

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        secretTitleLabel = new JLabel(SECRET_TITLE);
        secretTitleLabel.setFont(FONT_SUBTITLE);
        secretTitleLabel.setForeground(TEXT_DARK);
        secretSubLabel = new JLabel("Chosen by -");
        secretSubLabel.setFont(FONT_SMALL);
        secretSubLabel.setForeground(TEXT_MUTED);
        titles.add(secretTitleLabel);
        titles.add(secretSubLabel);
        card.add(titles, BorderLayout.NORTH);

        // One centred row with a fixed gap: 5 icons of SECRET_ICON plus 4 gaps fit the narrowest
        // supported window (1100 px), so the row never wraps and never gets clipped.
        secretRow = new JPanel(new FlowLayout(FlowLayout.CENTER, SECRET_GAP, 8));
        secretRow.setOpaque(false);
        card.add(secretRow, BorderLayout.CENTER);

        return card;
    }

    /** One secret shape: big picture with a name in small text underneath. */
    private JPanel buildSecretItem(String shapeName) {
        JPanel item = new JPanel(new BorderLayout(0, 6));
        item.setOpaque(false);
        item.add(shapeIcon(shapeName, SECRET_ICON, CARD_BORDER, 1), BorderLayout.CENTER);

        JLabel nameLabel = new JLabel(shapeName, JLabel.CENTER);
        nameLabel.setFont(FONT_SMALL);
        nameLabel.setForeground(TEXT_MUTED);
        item.add(nameLabel, BorderLayout.SOUTH);
        return item;
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

        // Rows keep their natural height (no stretching) and scroll when there are many players.
        // The column header stays fixed above the scrolling rows, so it is always visible.
        rowsPanel = new ScrollableRowsPanel();
        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setOpaque(false);

        JScrollPane scroll = new JScrollPane(rowsPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setColumnHeaderView(buildTableHeader());
        if (scroll.getColumnHeader() != null) {
            scroll.getColumnHeader().setOpaque(false);
        }
        card.add(scroll, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildTableHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        // 15 px matches a row's border (1) plus padding (14) so headers line up with the columns.
        header.setBorder(new EmptyBorder(0, 15, 0, 15));

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
    //  Footer: the countdown that replaces the old Continue button
    // =====================================================================

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);

        countdownLabel = new JLabel("Next round in " + COUNTDOWN_SECONDS + "s...");
        countdownLabel.setFont(FONT_BODY_BOLD);
        countdownLabel.setForeground(PRIMARY_BLUE);
        footer.add(countdownLabel, BorderLayout.WEST);

        JLabel note = new JLabel("The next screen opens automatically.");
        note.setFont(FONT_SMALL);
        note.setForeground(TEXT_MUTED);
        JPanel noteSlot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        noteSlot.setOpaque(false);
        noteSlot.add(note);
        footer.add(noteSlot, BorderLayout.EAST);

        return footer;
    }

    // =====================================================================
    //  Public API used by MainApp
    // =====================================================================

    /**
     * Draws the whole screen from one ROUND_RESULT. The arrays are parallel and every one of
     * them has one entry per player; guesses[i] is empty when that player did not submit.
     * Players are shown in the same order as names[] (never re-sorted).
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
                secretRow.add(buildSecretItem(shapeName));
            }
            secretTitleLabel.setText(SECRET_TITLE + " (" + secret.length + ")");
        } else {
            JLabel none = new JLabel("No secret shapes were locked in this round.");
            none.setFont(FONT_BODY);
            none.setForeground(TEXT_MUTED);
            secretRow.add(none);
            secretTitleLabel.setText(SECRET_TITLE);
        }
        secretRow.revalidate();
        secretRow.repaint();

        resultsBadge.setText(names.length + (names.length == 1 ? " player" : " players"));

        rowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            int delta = (i < deltas.length) ? deltas[i] : 0;
            int total = (i < totals.length) ? totals[i] : 0;
            String[] guess = (i < guesses.length) ? guesses[i] : new String[0];
            int correct = (i < correctCounts.length) ? correctCounts[i] : 0;
            boolean bonus = (i < bonuses.length) && bonuses[i];
            boolean isQuestioner = name.equals(questionerName);
            JPanel row = buildPlayerRow(i, name, delta, total, guess, correct, bonus, isQuestioner, secret);
            // BoxLayout only stretches up to the max size, so locking the height to the row's own
            // preferred height keeps rows natural instead of filling the whole viewport.
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
            rowsPanel.add(row);
            if (i < names.length - 1) {
                rowsPanel.add(Box.createVerticalStrut(ROW_GAP));
            }
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();

        // No Continue button any more: the server starts the next round on its own clock, and
        // this countdown just shows how long the wait is.
        startCountdown(isFinal);

        revalidate();
        repaint();
    }

    // =====================================================================
    //  Countdown (10 -> 0), driven by a javax.swing.Timer on the EDT
    // =====================================================================

    /** Restarts the 10-second countdown for the round just shown. */
    private void startCountdown(final boolean isFinal) {
        stopCountdown();
        secondsLeft = COUNTDOWN_SECONDS;
        setCountdownText(isFinal);
        countdownTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                secondsLeft--;
                if (secondsLeft <= 0) {
                    secondsLeft = 0;
                    setCountdownText(isFinal);
                    stopCountdown();
                    if (onCountdownFinished != null) {
                        onCountdownFinished.actionPerformed(new ActionEvent(
                            RoundResultScreen.this, ActionEvent.ACTION_PERFORMED, "countdown"));
                    }
                } else {
                    setCountdownText(isFinal);
                }
            }
        });
        countdownTimer.setRepeats(true);
        countdownTimer.start();
    }

    private void setCountdownText(boolean isFinal) {
        if (countdownLabel == null) {
            return;
        }
        if (isFinal) {
            countdownLabel.setText("Final scoreboard in " + secondsLeft + "s...");
        } else {
            countdownLabel.setText("Next round in " + secondsLeft + "s...");
        }
    }

    /** Stops the countdown. Safe to call when none is running. */
    public void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
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
            new EmptyBorder(10, 14, 10, 14)));

        // --- who ---
        JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.setOpaque(false);
        who.add(buildAvatar(initialOf(name), avatarColor(index), AVATAR_SIZE));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(FONT_BODY_BOLD);
        nameLabel.setForeground(TEXT_DARK);
        who.add(nameLabel);

        if (isQuestioner) {
            who.add(buildBadge("Questioner", new Color(226, 232, 240), TEXT_DARK, CARD_BORDER, 2, 8));
        }
        row.add(who, BorderLayout.WEST);

        // --- guessed shapes ---
        JPanel guessed = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
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
    //  Inner classes
    // =====================================================================

    /**
     * The row container for the scroll pane: its width follows the viewport (so rows line up with
     * the fixed column header) while its height stays its own preferred height, which lets the
     * rows keep their natural size and makes the pane scroll instead of squashing them.
     */
    private static class ScrollableRowsPanel extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 80;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
