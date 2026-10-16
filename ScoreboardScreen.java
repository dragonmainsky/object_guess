import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * ScoreboardScreen: screen 5 - the standings after the last round. MainApp fills it in through
 * setResults(); the values below are only the preview defaults used when the screen stands alone.
 */
public class ScoreboardScreen extends JPanel {

    private static final Color BG_DARK_HEADER = new Color(24, 34, 53);
    private static final Color GOLD_BADGE_BG = new Color(254, 240, 138);
    private static final Color DANGER_AVATAR = new Color(244, 63, 94);

    private final ActionListener onNewGameAction;
    private final ActionListener onQuitAction;

    private String[] playerNames = {"Aim", "Player 2", "Player 3", "Player 4"};
    private int[] playerScores = {8, 6, 4, 3};
    private int roundsPlayed;
    private int totalRounds;             // players x 2, filled in by setResults()
    private boolean finalRound = true;

    private JLabel congratsLabel;
    private JLabel gameOverBadge;
    private JLabel completeBadge;
    private JLabel finalScoreValue;
    private JPanel rowsPanel;
    private JLabel roundsValue;
    private JLabel playersValue;
    private JLabel targetValue;
    private JButton nextBtn;

    public ScoreboardScreen(ActionListener onNewGameAction, ActionListener onQuitAction) {
        this.onNewGameAction = onNewGameAction;
        this.onQuitAction = onQuitAction;
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        var mainContent = AppDialog.panel();
        mainContent.setLayout(new BorderLayout(32, 0));
        mainContent.setBorder(new EmptyBorder(30, 40, 30, 40));

        var leftColumn = buildLeftColumn();
        leftColumn.setPreferredSize(new Dimension(480, 100));
        mainContent.add(leftColumn, BorderLayout.WEST);
        mainContent.add(buildRightColumn(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
        refresh();
    }

    // =====================================================================
    //  Results coming from the server
    // =====================================================================

    /** Takes the latest scores from the server, sorts them best first and rebuilds the whole screen. */
    public void setResults(String[] names, int[] scores, int rounds, int totalRounds, boolean isFinal) {
        if (names != null && scores != null && names.length > 0) {
            // Insertion sort, so equal scores keep the order the server sent them in.
            String[] sortedNames = new String[names.length];
            int[] sortedScores = new int[names.length];
            for (int i = 0; i < names.length; i++) {
                sortedNames[i] = names[i];
                sortedScores[i] = (i < scores.length) ? scores[i] : 0;
            }
            for (int i = 1; i < sortedNames.length; i++) {
                String name = sortedNames[i];
                int score = sortedScores[i];
                int j = i - 1;
                while (j >= 0 && sortedScores[j] < score) {
                    sortedNames[j + 1] = sortedNames[j];
                    sortedScores[j + 1] = sortedScores[j];
                    j--;
                }
                sortedNames[j + 1] = name;
                sortedScores[j + 1] = score;
            }
            playerNames = sortedNames;
            playerScores = sortedScores;
        }
        roundsPlayed = rounds;
        this.totalRounds = totalRounds;
        finalRound = isFinal;
        refresh();
    }

    /** The continue / new game button only works for the host. */
    public void setNextEnabled(boolean enabled) {
        nextBtn.setEnabled(enabled);
        nextBtn.setToolTipText(enabled
                ? "Continue to the next round, or start a fresh tournament."
                : "Only the host can continue or start a new game.");
    }

    /** Redraws everything that depends on the scores or on the round state. */
    private void refresh() {
        if (playerNames.length > 0) {
            congratsLabel.setText("Congratulations, " + playerNames[0] + "!");
            finalScoreValue.setText(String.valueOf(playerScores[0]));
        } else {
            congratsLabel.setText("No players yet");
            finalScoreValue.setText("0");
        }

        gameOverBadge.setText(finalRound ? "GAME OVER" : "ROUND SUMMARY");
        completeBadge.setText(finalRound ? "TOURNAMENT COMPLETE"
                : "ROUND " + roundsPlayed + " OF " + totalRounds + " COMPLETE");
        nextBtn.setText(finalRound ? "NEW GAME" : "CONTINUE");

        roundsValue.setText(roundsPlayed + " / " + totalRounds);
        playersValue.setText(String.valueOf(playerNames.length));
        targetValue.setText(GameServer.MIN_SHAPES + "-" + GameServer.MAX_SHAPES + " each");

        rowsPanel.removeAll();
        rowsPanel.setLayout(new GridLayout(Math.max(1, playerNames.length), 1, 0, 6));
        for (int i = 0; i < playerNames.length; i++) {
            rowsPanel.add(buildLeaderboardRow(i + 1, playerNames[i], playerScores[i]));
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
        revalidate();
        repaint();
    }

    // =====================================================================
    //  Left column: trophy, GAME OVER badge, the winner and the final score
    // =====================================================================

    private JPanel buildLeftColumn() {
        var left = AppDialog.panel();
        left.setLayout(new BorderLayout());
        // Pushes the content down from the top edge instead of centering it vertically.
        left.setBorder(new EmptyBorder(40, 0, 0, 0));

        gameOverBadge = AppDialog.badge("GAME OVER", BG_DARK_HEADER, Color.WHITE, null, 4, 14);
        congratsLabel = AppDialog.label("Congratulations, " + playerNames[0] + "!", AppDialog.TITLE_LARGE,
                AppDialog.TEXT_DARK, JLabel.CENTER);
        var sub = AppDialog.label("<html><center>You read the room, matched the shapes,<br>and took first place.</center></html>",
                AppDialog.BODY, AppDialog.TEXT_MUTED, JLabel.CENTER);

        var items = new JPanel[] {
            centered(buildTrophyPanel(), 0),
            centered(gameOverBadge, 0),
            centered(congratsLabel),
            centered(sub),
            centered(buildScoreCard(), 8)
        };
        left.add(stackItems(items, 12), BorderLayout.CENTER);
        return left;
    }

    /** Light yellow square with the trophy picture, or with plain text when the file is missing. */
    private JPanel buildTrophyPanel() {
        var trophy = new JPanel(new BorderLayout());
        trophy.setBackground(AppDialog.GOLD_BG);
        trophy.setBorder(new LineBorder(AppDialog.GOLD_BORDER, 2));
        trophy.setPreferredSize(new Dimension(170, 170));
        trophy.add(ShapeImages.shape("Trophy", 100), BorderLayout.CENTER);
        return trophy;
    }

    /** Blue card with the winner's total score. */
    private JPanel buildScoreCard() {
        var card = new JPanel(new GridLayout(3, 1, 0, 0));
        card.setBackground(AppDialog.PRIMARY_BLUE);
        card.setBorder(new EmptyBorder(14, 10, 14, 10));
        card.setPreferredSize(new Dimension(220, 140));

        finalScoreValue = AppDialog.label(String.valueOf(playerScores[0]), AppDialog.f(Font.BOLD, 36),
                Color.WHITE, JLabel.CENTER);
        card.add(AppDialog.label("FINAL SCORE", AppDialog.SMALL_BOLD, AppDialog.BLUE_BORDER, JLabel.CENTER));
        card.add(finalScoreValue);
        card.add(AppDialog.label("POINTS", AppDialog.SMALL_BOLD, AppDialog.BLUE_BORDER, JLabel.CENTER));
        return card;
    }

    // =====================================================================
    //  Right column: the standings table
    // =====================================================================

    private JPanel buildRightColumn() {
        var card = new JPanel(new BorderLayout(0, 16));
        card.setBackground(AppDialog.CARD_BG);
        card.setBorder(new CompoundBorder(new LineBorder(AppDialog.CARD_BORDER, 1), new EmptyBorder(24, 28, 24, 28)));

        var titles = AppDialog.panel();
        titles.setLayout(new GridLayout(2, 1, 0, 2));
        titles.add(AppDialog.label("FINAL RESULTS", AppDialog.SMALL_BOLD, AppDialog.PRIMARY_BLUE));
        titles.add(AppDialog.label("Scoreboard", AppDialog.TITLE_24, AppDialog.TEXT_DARK));

        completeBadge = AppDialog.badge("TOURNAMENT COMPLETE", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                AppDialog.SUCCESS_BORDER, 6, 14);
        var badgeHolder = AppDialog.panel();
        badgeHolder.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 6));
        badgeHolder.add(completeBadge);

        var cardHeader = AppDialog.panel();
        cardHeader.setLayout(new BorderLayout());
        cardHeader.add(titles, BorderLayout.WEST);
        cardHeader.add(badgeHolder, BorderLayout.EAST);
        card.add(cardHeader, BorderLayout.NORTH);

        card.add(stackItems(new JPanel[] {buildTableHeader(), buildRowsPanel(), buildDivider(), buildStatsRow()}, 12),
                BorderLayout.CENTER);

        nextBtn = AppDialog.button("NEW GAME", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER, AppDialog.PRIMARY_PRESS,
                Color.WHITE, AppDialog.PRIMARY_BLUE, 10, 18);
        nextBtn.setPreferredSize(new Dimension(160, 44));
        if (onNewGameAction != null) {
            nextBtn.addActionListener(onNewGameAction);
        }

        var quitBtn = AppDialog.button("QUIT", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 10, 18);
        quitBtn.setPreferredSize(new Dimension(160, 44));
        if (onQuitAction != null) {
            quitBtn.addActionListener(onQuitAction);
        }

        var buttonRow = AppDialog.panel();
        buttonRow.setLayout(new GridLayout(1, 2, 12, 0));
        buttonRow.add(nextBtn);
        buttonRow.add(quitBtn);
        card.add(buttonRow, BorderLayout.SOUTH);
        return card;
    }

    /** RANK / PLAYER / SCORE header, sized to line up with the player rows. */
    private JPanel buildTableHeader() {
        var header = AppDialog.panel();
        header.setLayout(new BorderLayout(10, 0));
        header.setBorder(new EmptyBorder(0, 13, 0, 13));

        header.add(tableTitle("RANK", 36, JLabel.LEFT), BorderLayout.WEST);
        header.add(tableTitle("PLAYER", 0, JLabel.LEADING), BorderLayout.CENTER);
        header.add(tableTitle("SCORE", 50, JLabel.RIGHT), BorderLayout.EAST);
        return header;
    }

    private static JLabel tableTitle(String text, int width, int align) {
        var label = AppDialog.label(text, AppDialog.SMALL_BOLD, AppDialog.TEXT_LIGHT, align);
        if (width > 0) {
            label.setPreferredSize(new Dimension(width, 20));
        }
        return label;
    }

    private JPanel buildRowsPanel() {
        rowsPanel = AppDialog.panel();
        rowsPanel.setLayout(new GridLayout(Math.max(1, playerNames.length), 1, 0, 6));
        for (int i = 0; i < playerNames.length; i++) {
            rowsPanel.add(buildLeaderboardRow(i + 1, playerNames[i], playerScores[i]));
        }
        return rowsPanel;
    }

    /** One row: rank, avatar, name (with a WINNER badge) and the score. */
    private JPanel buildLeaderboardRow(int rank, String name, int score) {
        boolean winner = rank == 1;
        Color rowBg = winner ? AppDialog.GOLD_BG : AppDialog.ROW_BG;
        Color rowBorder = winner ? AppDialog.GOLD_BORDER : AppDialog.ROW_BG;
        Color rankColor = winner ? AppDialog.GOLD_TEXT : AppDialog.TEXT_MUTED;
        Color scoreColor = winner ? AppDialog.GOLD_TEXT : AppDialog.PRIMARY_BLUE;

        var row = new JPanel(new BorderLayout(10, 0));
        row.setBackground(rowBg);
        row.setBorder(new CompoundBorder(new LineBorder(rowBorder, 1), new EmptyBorder(7, 12, 7, 12)));

        var rankLabel = AppDialog.label(String.valueOf(rank), AppDialog.BODY_BOLD, rankColor);
        rankLabel.setPreferredSize(new Dimension(36, 26));
        row.add(rankLabel, BorderLayout.WEST);

        var playerPart = AppDialog.panel();
        playerPart.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        playerPart.add(AppDialog.avatar(name.substring(0, 1), rankColor(rank), 28));
        playerPart.add(AppDialog.label(name, AppDialog.BODY_BOLD, AppDialog.TEXT_DARK));
        if (winner) {
            playerPart.add(AppDialog.badge("WINNER", GOLD_BADGE_BG, AppDialog.GOLD_TEXT, null, 2, 8));
        }
        row.add(playerPart, BorderLayout.CENTER);

        var scoreLabel = AppDialog.label(String.valueOf(score), AppDialog.TITLE, scoreColor, JLabel.RIGHT);
        scoreLabel.setPreferredSize(new Dimension(50, 26));
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    /** Avatar colour picked by rank; the podium places get their own colours. */
    private static Color rankColor(int rank) {
        if (rank == 1) {
            return new Color(234, 179, 8);
        }
        if (rank == 2) {
            return new Color(56, 189, 248);
        }
        return rank == 3 ? new Color(139, 92, 246) : DANGER_AVATAR;
    }

    /** The three statistics under the divider, all of them real game numbers. */
    private JPanel buildStatsRow() {
        roundsValue = AppDialog.label("", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        playersValue = AppDialog.label("", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);
        targetValue = AppDialog.label("", AppDialog.BODY_BOLD, AppDialog.TEXT_DARK);

        var stats = AppDialog.panel();
        stats.setLayout(new GridLayout(1, 3, 10, 0));
        stats.add(statItem("Rounds played", roundsValue));
        stats.add(statItem("Players", playersValue));
        stats.add(statItem("Secret shapes", targetValue));
        return stats;
    }

    private static JPanel statItem(String title, JLabel value) {
        var item = AppDialog.panel();
        item.setLayout(new GridLayout(2, 1, 0, 2));
        item.add(AppDialog.label(title, AppDialog.SMALL, AppDialog.TEXT_LIGHT));
        item.add(value);
        return item;
    }

    /** 1px horizontal divider. */
    private static JPanel buildDivider() {
        var line = new JPanel();
        line.setBackground(AppDialog.CARD_BORDER);
        line.setPreferredSize(new Dimension(1, 1));
        return line;
    }

    /** Stacks blocks top-aligned, each at its own height, with the given gap between them. */
    private static JPanel stackItems(JPanel[] items, int gap) {
        var root = AppDialog.panel();
        root.setLayout(new BorderLayout(0, gap));
        var current = root;
        for (int i = 0; i < items.length; i++) {
            current.add(items[i], BorderLayout.NORTH);
            if (i < items.length - 1) {
                var next = AppDialog.panel();
                next.setLayout(new BorderLayout(0, gap));
                current.add(next, BorderLayout.CENTER);
                current = next;
            }
        }
        return root;
    }

    /** Centers one block horizontally, with an optional vertical padding around it. */
    private static JPanel centered(JPanel inner, int padV) {
        var holder = AppDialog.panel();
        holder.setLayout(new FlowLayout(FlowLayout.CENTER, 0, padV));
        holder.add(inner);
        return holder;
    }

    private static JPanel centered(JLabel inner) {
        return centered(inner, 0);
    }

    private static JPanel centered(JLabel inner, int padV) {
        var holder = AppDialog.panel();
        holder.setLayout(new FlowLayout(FlowLayout.CENTER, 0, padV));
        holder.add(inner);
        return holder;
    }
}
