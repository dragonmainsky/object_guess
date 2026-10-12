import java.awt.*;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * ScoreboardScreen: Screen 5 - Game Over / Final Results leaderboard and trophy screen.
 */
public class ScoreboardScreen extends JPanel {
    private ActionListener onNewGameAction;
    private ActionListener onQuitAction;

    public ScoreboardScreen() {
        this(null, null);
    }

    public ScoreboardScreen(ActionListener onNewGameAction, ActionListener onQuitAction) {
        this.onNewGameAction = onNewGameAction;
        this.onQuitAction = onQuitAction;

        setLayout(new BorderLayout());
        setBackground(UIStyle.BG_LIGHT);

        JPanel mainContent = new JPanel(new GridBagLayout());
        mainContent.setOpaque(false);
        mainContent.setBorder(new EmptyBorder(20, 40, 20, 40));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 16, 10, 16);

        // Left Column: Trophy, Congratulations, and Final Score Card
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.40;
        gbc.weighty = 1.0;
        mainContent.add(createLeftColumn(), gbc);

        // Right Column: Leaderboard Scoreboard Card
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.60;
        gbc.weighty = 1.0;
        mainContent.add(createRightColumn(), gbc);

        add(mainContent, BorderLayout.CENTER);
    }

    private JPanel createLeftColumn() {
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        // Trophy Circle Illustration
        JPanel trophyCircle = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int d = Math.min(getWidth(), getHeight()) - 10;
                int x = (getWidth() - d) / 2;
                int y = (getHeight() - d) / 2;

                // Outer soft circle
                g2.setColor(new Color(254, 249, 195, 120));
                g2.fillOval(x, y, d, d);

                // Inner circle
                int innerD = d - 24;
                int ix = (getWidth() - innerD) / 2;
                int iy = (getHeight() - innerD) / 2;
                g2.setColor(new Color(254, 243, 199));
                g2.fillOval(ix, iy, innerD, innerD);

                g2.setColor(new Color(253, 230, 138));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawOval(ix, iy, innerD, innerD);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        trophyCircle.setOpaque(false);
        trophyCircle.setPreferredSize(new Dimension(170, 170));
        trophyCircle.setMaximumSize(new Dimension(170, 170));
        trophyCircle.setAlignmentX(Component.CENTER_ALIGNMENT);

        ImageIcon trophyIcon = TextureManager.getIcon("trophy.png", 100, 100);
        trophyCircle.add(new JLabel(trophyIcon));

        // Badge: GAME OVER
        UIStyle.PillBadge gameOverBadge = new UIStyle.PillBadge("GAME OVER", UIStyle.BG_DARK_HEADER, Color.WHITE);
        gameOverBadge.setFont(UIStyle.FONT_SMALL_BOLD);
        gameOverBadge.setBorder(new EmptyBorder(4, 14, 4, 14));
        gameOverBadge.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Titles
        JLabel congrats = new JLabel("Congratulations, Aim!");
        congrats.setFont(new Font("Segoe UI", Font.BOLD, 26));
        congrats.setForeground(UIStyle.TEXT_DARK);
        congrats.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("<html><center>You read the room, matched the shapes,<br>and took first place.</center></html>", SwingConstants.CENTER);
        sub.setFont(UIStyle.FONT_BODY);
        sub.setForeground(UIStyle.TEXT_MUTED);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Blue Final Score Card (centered text, fixed height so nothing is clipped)
        UIStyle.CardPanel scoreCard = new UIStyle.CardPanel(16, UIStyle.PRIMARY_BLUE, null);
        scoreCard.setShowBorder(false);
        scoreCard.setLayout(new GridBagLayout());
        scoreCard.setPreferredSize(new Dimension(220, 140));
        scoreCard.setMaximumSize(new Dimension(220, 140));
        scoreCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel fsLabel = new JLabel("FINAL SCORE", SwingConstants.CENTER);
        fsLabel.setFont(UIStyle.FONT_SMALL_BOLD);
        fsLabel.setForeground(new Color(191, 219, 254));
        fsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel fsValue = new JLabel("8", SwingConstants.CENTER);
        fsValue.setFont(new Font("Segoe UI", Font.BOLD, 36));
        fsValue.setForeground(Color.WHITE);
        fsValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel fsPoints = new JLabel("POINTS", SwingConstants.CENTER);
        fsPoints.setFont(UIStyle.FONT_SMALL_BOLD);
        fsPoints.setForeground(new Color(191, 219, 254));
        fsPoints.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel scoreInner = new JPanel();
        scoreInner.setOpaque(false);
        scoreInner.setLayout(new BoxLayout(scoreInner, BoxLayout.Y_AXIS));
        fsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        fsValue.setAlignmentX(Component.CENTER_ALIGNMENT);
        fsPoints.setAlignmentX(Component.CENTER_ALIGNMENT);
        scoreInner.add(fsLabel);
        scoreInner.add(fsValue);
        scoreInner.add(fsPoints);
        scoreCard.add(scoreInner);

        left.add(Box.createVerticalGlue());
        left.add(trophyCircle);
        left.add(Box.createVerticalStrut(14));
        left.add(gameOverBadge);
        left.add(Box.createVerticalStrut(12));
        left.add(congrats);
        left.add(Box.createVerticalStrut(6));
        left.add(sub);
        left.add(Box.createVerticalStrut(20));
        left.add(scoreCard);
        left.add(Box.createVerticalGlue());

        return left;
    }

    private JPanel createRightColumn() {
        UIStyle.CardPanel card = new UIStyle.CardPanel(20, UIStyle.CARD_BG, UIStyle.CARD_BORDER);
        card.setLayout(new BorderLayout(0, 16));
        card.setBorder(new EmptyBorder(24, 28, 24, 28));

        // Header of card
        JPanel cardHeader = new JPanel(new BorderLayout());
        cardHeader.setOpaque(false);
        cardHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);

        JLabel tag = new JLabel("FINAL RESULTS");
        tag.setFont(UIStyle.FONT_SMALL_BOLD);
        tag.setForeground(UIStyle.PRIMARY_BLUE);

        JLabel title = new JLabel("Scoreboard");
        title.setFont(UIStyle.FONT_TITLE_LARGE);
        title.setForeground(UIStyle.TEXT_DARK);

        titles.add(tag);
        titles.add(title);
        cardHeader.add(titles, BorderLayout.WEST);

        UIStyle.PillBadge completeBadge = new UIStyle.PillBadge("4 ROUNDS COMPLETE", UIStyle.SUCCESS_BG, UIStyle.SUCCESS_TEXT, UIStyle.SUCCESS_BORDER);
        completeBadge.setFont(UIStyle.FONT_SMALL_BOLD);
        completeBadge.setBorder(new EmptyBorder(6, 14, 6, 14));
        cardHeader.add(completeBadge, BorderLayout.EAST);

        card.add(cardHeader, BorderLayout.NORTH);

        // Center Content: Leaderboard List + Stats
        JPanel centerBody = new JPanel();
        centerBody.setOpaque(false);
        centerBody.setLayout(new BoxLayout(centerBody, BoxLayout.Y_AXIS));

        // Table column headers
        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        tableHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel rH = new JLabel("RANK");
        rH.setFont(UIStyle.FONT_SMALL_BOLD);
        rH.setForeground(UIStyle.TEXT_LIGHT);
        rH.setPreferredSize(new Dimension(60, 20));

        JLabel pH = new JLabel("PLAYER");
        pH.setFont(UIStyle.FONT_SMALL_BOLD);
        pH.setForeground(UIStyle.TEXT_LIGHT);

        JLabel sH = new JLabel("SCORE", SwingConstants.RIGHT);
        sH.setFont(UIStyle.FONT_SMALL_BOLD);
        sH.setForeground(UIStyle.TEXT_LIGHT);
        sH.setPreferredSize(new Dimension(60, 20));

        tableHeader.add(rH, BorderLayout.WEST);
        tableHeader.add(pH, BorderLayout.CENTER);
        tableHeader.add(sH, BorderLayout.EAST);

        centerBody.add(tableHeader);
        centerBody.add(Box.createVerticalStrut(8));

        // Rows
        // Rank 1: Winner (Highlighted row with gold background)
        centerBody.add(createLeaderboardRow(1, "A", new Color(234, 179, 8), "Aim", true, 8));
        centerBody.add(Box.createVerticalStrut(6));

        // Rank 2: Player 2
        centerBody.add(createLeaderboardRow(2, "P", new Color(56, 189, 248), "Player 2", false, 6));
        centerBody.add(Box.createVerticalStrut(6));

        // Rank 3: Player 3
        centerBody.add(createLeaderboardRow(3, "P", new Color(139, 92, 246), "Player 3", false, 4));
        centerBody.add(Box.createVerticalStrut(6));

        // Rank 4: Player 4
        centerBody.add(createLeaderboardRow(4, "P", new Color(244, 63, 94), "Player 4", false, 3));
        centerBody.add(Box.createVerticalStrut(18));

        // Divider
        JSeparator sep = new JSeparator();
        sep.setForeground(UIStyle.CARD_BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        centerBody.add(sep);
        centerBody.add(Box.createVerticalStrut(14));

        // Stats Footer Row
        JPanel statsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        statsRow.setOpaque(false);
        statsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        statsRow.add(createStatItem("Best round", "Round 3"));
        statsRow.add(createStatItem("Correct guesses", "8 / 12"));
        statsRow.add(createStatItem("Fastest guess", "06.4s"));

        centerBody.add(statsRow);
        card.add(centerBody, BorderLayout.CENTER);

        // Buttons at Bottom
        JPanel btnRow = new JPanel(new GridLayout(1, 2, 12, 0));
        btnRow.setOpaque(false);

        UIStyle.ModernButton newGameBtn = UIStyle.ModernButton.primary("NEW GAME");
        newGameBtn.setPreferredSize(new Dimension(160, 44));
        if (onNewGameAction != null) {
            newGameBtn.addActionListener(onNewGameAction);
        }

        UIStyle.ModernButton quitBtn = UIStyle.ModernButton.secondary("QUIT");
        quitBtn.setPreferredSize(new Dimension(160, 44));
        if (onQuitAction != null) {
            quitBtn.addActionListener(onQuitAction);
        } else {
            quitBtn.addActionListener(e -> {
                int res = JOptionPane.showConfirmDialog(this, "Exit the game?", "Quit", JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) System.exit(0);
            });
        }

        btnRow.add(newGameBtn);
        btnRow.add(quitBtn);
        card.add(btnRow, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createLeaderboardRow(int rank, String initial, Color avatarColor, String name, boolean isWinner, int score) {
        JPanel row = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (isWinner) {
                    g2.setColor(UIStyle.GOLD_BG);
                    if (UIStyle.ENABLE_ROUNDED_CORNERS) {
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                        g2.setColor(UIStyle.GOLD_BORDER);
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    } else {
                        g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
                        g2.setColor(UIStyle.GOLD_BORDER);
                        g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                    }
                } else {
                    g2.setColor(new Color(248, 250, 252));
                    if (UIStyle.ENABLE_ROUNDED_CORNERS) {
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                    } else {
                        g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
                    }
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        // Rank indicator
        JLabel rankLabel = new JLabel(String.valueOf(rank));
        rankLabel.setFont(UIStyle.FONT_BODY_BOLD);
        rankLabel.setForeground(isWinner ? UIStyle.GOLD_TEXT : UIStyle.TEXT_MUTED);
        rankLabel.setPreferredSize(new Dimension(36, 26));
        row.add(rankLabel, BorderLayout.WEST);

        // Player Avatar + Name + Winner badge
        JPanel playerPart = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        playerPart.setOpaque(false);
        playerPart.add(new UIStyle.AvatarCircle(initial, avatarColor, 28));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(UIStyle.FONT_BODY_BOLD);
        nameLabel.setForeground(UIStyle.TEXT_DARK);
        playerPart.add(nameLabel);

        if (isWinner) {
            UIStyle.PillBadge winBadge = new UIStyle.PillBadge("WINNER", new Color(254, 240, 138), UIStyle.GOLD_TEXT);
            winBadge.setFont(UIStyle.FONT_SMALL_BOLD);
            playerPart.add(winBadge);
        }

        row.add(playerPart, BorderLayout.CENTER);

        // Score
        JLabel scoreLabel = new JLabel(String.valueOf(score), SwingConstants.RIGHT);
        scoreLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        scoreLabel.setForeground(isWinner ? UIStyle.GOLD_TEXT : UIStyle.PRIMARY_BLUE);
        scoreLabel.setPreferredSize(new Dimension(50, 26));
        row.add(scoreLabel, BorderLayout.EAST);

        return row;
    }

    private JPanel createStatItem(String label, String value) {
        JPanel p = new JPanel(new GridLayout(2, 1, 0, 2));
        p.setOpaque(false);

        JLabel l = new JLabel(label);
        l.setFont(UIStyle.FONT_SMALL);
        l.setForeground(UIStyle.TEXT_LIGHT);

        JLabel v = new JLabel(value);
        v.setFont(UIStyle.FONT_BODY_BOLD);
        v.setForeground(UIStyle.TEXT_DARK);

        p.add(l);
        p.add(v);
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Guess What It Looks Like? - Scoreboard");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1280, 750);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new ScoreboardScreen());
            frame.setVisible(true);
        });
    }
}
