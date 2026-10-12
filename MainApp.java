import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * MainApp: Master application with CardLayout allowing easy switching
 * between all 5 GUI screens, plus a top preview bar for direct jumping.
 *
 * Remake: no custom painting and no lambda expressions. Screen changes are
 * handled by the ScreenSwitcher inner class, and the preview bar buttons are
 * plain JButtons whose colors follow their model state (NavButtonStateListener).
 */
public class MainApp extends JFrame {
    public static final String SCREEN_JOIN = "JOIN";
    public static final String SCREEN_LOBBY = "LOBBY";
    public static final String SCREEN_QUESTIONER = "QUESTIONER";
    public static final String SCREEN_GUESSER = "GUESSER";
    public static final String SCREEN_SCOREBOARD = "SCOREBOARD";

    // Preview bar colors
    private static final Color NAV_BAR_BG = new Color(15, 23, 42);
    private static final Color NAV_LABEL_FG = new Color(148, 163, 184);
    private static final Color NAV_BTN_NORMAL = Color.BLACK;
    private static final Color NAV_BTN_HOVER = new Color(30, 41, 59);
    private static final Color NAV_BTN_PRESSED = new Color(51, 65, 85);
    private static final Color NAV_BTN_BORDER = new Color(51, 65, 85);

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cardPanel = new JPanel(cardLayout);

    public MainApp() {
        setTitle("Guess What It Looks Like? - Multiplayer Party Game (GUI)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 800);
        setMinimumSize(new Dimension(1100, 680));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // --- Screen Instances with Flow Interactivity ---
        // Join goes to Lobby
        JoinScreen joinScreen = new JoinScreen(new ScreenSwitcher(SCREEN_LOBBY));

        // Start Game goes to Questioner, Leave Room goes back to Join
        LobbyScreen lobbyScreen = new LobbyScreen(
            new ScreenSwitcher(SCREEN_QUESTIONER),
            new ScreenSwitcher(SCREEN_JOIN)
        );

        // Confirm goes to Guesser
        QuestionerScreen questionerScreen = new QuestionerScreen(
            new ScreenSwitcher(SCREEN_GUESSER)
        );

        // Submit goes to Scoreboard
        GuesserScreen guesserScreen = new GuesserScreen(
            new ScreenSwitcher(SCREEN_SCOREBOARD)
        );

        // New Game goes to Lobby, Quit goes back to Join
        ScoreboardScreen scoreboardScreen = new ScoreboardScreen(
            new ScreenSwitcher(SCREEN_LOBBY),
            new ScreenSwitcher(SCREEN_JOIN)
        );

        // Add to CardPanel
        cardPanel.add(joinScreen, SCREEN_JOIN);
        cardPanel.add(lobbyScreen, SCREEN_LOBBY);
        cardPanel.add(questionerScreen, SCREEN_QUESTIONER);
        cardPanel.add(guesserScreen, SCREEN_GUESSER);
        cardPanel.add(scoreboardScreen, SCREEN_SCOREBOARD);

        // --- Top Quick-Switcher Control Bar ---
        JPanel topSwitcherBar = createQuickSwitcherBar();

        add(topSwitcherBar, BorderLayout.NORTH);
        add(cardPanel, BorderLayout.CENTER);

        // Show initial screen (Join)
        showScreen(SCREEN_JOIN);

        setVisible(true);
    }

    public void showScreen(String name) {
        cardLayout.show(cardPanel, name);
    }

    /**
     * Top demo/navigation bar so anyone testing can easily jump to any screen instantly!
     */
    private JPanel createQuickSwitcherBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(NAV_BAR_BG);
        bar.setBorder(new EmptyBorder(6, 16, 6, 16));

        // Label on left
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        JLabel navLabel = new JLabel("GUI PREVIEW SWITCHER:");
        navLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        navLabel.setForeground(NAV_LABEL_FG);
        left.add(navLabel);
        bar.add(left, BorderLayout.WEST);

        // Switcher Buttons
        JPanel navButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        navButtons.setOpaque(false);

        navButtons.add(createNavButton("1. Join Server", SCREEN_JOIN));
        navButtons.add(createNavButton("2. Game Lobby", SCREEN_LOBBY));
        navButtons.add(createNavButton("3. Questioner Mode", SCREEN_QUESTIONER));
        navButtons.add(createNavButton("4. Guesser Mode", SCREEN_GUESSER));
        navButtons.add(createNavButton("5. Scoreboard / Results", SCREEN_SCOREBOARD));

        bar.add(navButtons, BorderLayout.EAST);
        return bar;
    }

    /**
     * Plain JButton: black fill, thin slate border, white bold text.
     * Fill color changes with hover/press through NavButtonStateListener.
     */
    private JButton createNavButton(String title, String screenName) {
        JButton btn = new JButton(title);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(Color.WHITE);
        btn.setBackground(NAV_BTN_NORMAL);
        btn.setContentAreaFilled(false); // call this first: some look and feels reset opaque when it changes
        btn.setOpaque(true);             // the opaque background is what shows the black fill
        btn.setBorderPainted(true);
        btn.setBorder(new CompoundBorder(
            new LineBorder(NAV_BTN_BORDER, 1),
            new EmptyBorder(4, 11, 4, 11)));
        btn.setFocusPainted(false);
        btn.setFocusable(false); // keep keyboard focus on the visible screen
        btn.setRolloverEnabled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.getModel().addChangeListener(new NavButtonStateListener(btn));
        btn.addActionListener(new ScreenSwitcher(screenName));
        return btn;
    }

    /**
     * Switches the CardLayout to a fixed screen. One small class replaces
     * every inline listener, so each screen gets its own instance with its target name.
     */
    private class ScreenSwitcher implements ActionListener {
        private final String screenName;

        ScreenSwitcher(String screenName) {
            this.screenName = screenName;
        }

        @Override
        public void actionPerformed(ActionEvent event) {
            showScreen(screenName);
        }
    }

    /**
     * Repaints a preview bar button in the right color whenever its model changes:
     * pressed/armed is lightest, rollover is in between, normal is black.
     */
    private static class NavButtonStateListener implements ChangeListener {
        private final JButton button;

        NavButtonStateListener(JButton button) {
            this.button = button;
        }

        @Override
        public void stateChanged(ChangeEvent event) {
            ButtonModel model = button.getModel();
            if (model.isPressed() || model.isArmed()) {
                button.setBackground(NAV_BTN_PRESSED);
            } else if (model.isRollover()) {
                button.setBackground(NAV_BTN_HOVER);
            } else {
                button.setBackground(NAV_BTN_NORMAL);
            }
        }
    }

    public static void main(String[] args) {
        new MainApp();
    }
}