import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * GuesserScreen: Screen 4 - Guesser Mode where players try to guess the secret shapes.
 *
 * Layout: left = Scoreboard + Scoring cards, center = "Your guess" card (one slot per shape, 1-5),
 * right = Objects picker (2x4 grid + submit button) with the ENTER hint below.
 *
 * Drag a shape from Objects onto a guess slot (empty slot = add, filled slot = replace or swap).
 * Drag a placed shape back onto the Objects card to remove it.
 */
public class GuesserScreen extends JPanel {
    private static final int GHOST_SIZE = 64;
    private static final int SLOT_ICON_WIDE = 78;    // guess-slot icon when the round needs up to 3 shapes
    private static final int SLOT_ICON_TIGHT = 54;   // smaller icon so 4-5 slots still fit the row
    private static final String SHAPE_DIR = System.getProperty("user.dir") + "/shape/";
    private static final String[][] AVAILABLE_SHAPES = {
        {"Circle", "Circle.png"}, {"Triangle", "Triangle.png"}, {"Rectangle", "Rectangle.png"}, {"Oval", "Oval.png"},
        {"Hexagon", "Hexagon.png"}, {"Diamond", "diamond.png"}, {"Star", "star.png"}, {"Square", "Square.png"}
    };

    // ==================== Colors and fonts ====================
    private static final Color BG_LIGHT = new Color(241, 245, 249), CARD_BG = Color.WHITE, CARD_BORDER = new Color(226, 232, 240);
    private static final Color STAGE_BG = new Color(247, 250, 255), STAGE_BORDER = new Color(226, 232, 240);
    private static final Color PRIMARY_BLUE = new Color(37, 99, 235), PRIMARY_HOVER = new Color(29, 78, 216);
    private static final Color BLUE_LIGHT_BG = new Color(239, 246, 255), BLUE_BORDER = new Color(191, 219, 254);
    private static final Color SUCCESS_BG = new Color(220, 252, 231), SUCCESS_TEXT = new Color(22, 101, 52), SUCCESS_BORDER = new Color(187, 247, 208);
    private static final Color WARNING_BG = new Color(254, 249, 195), WARNING_TEXT = new Color(133, 77, 14), WARNING_BORDER = new Color(254, 240, 138);
    private static final Color DANGER_BG = new Color(254, 226, 226), DANGER_TEXT = new Color(153, 27, 27), DANGER_BORDER = new Color(254, 202, 202);
    private static final Color GOLD_BG = new Color(254, 243, 199), GOLD_TEXT = new Color(180, 83, 9), GOLD_BORDER = new Color(253, 230, 138);
    private static final Color TEXT_DARK = new Color(15, 23, 42), TEXT_MUTED = new Color(100, 116, 139);
    private static final Color EMPTY_SLOT_BG = new Color(248, 250, 252), EMPTY_SLOT_BORDER = new Color(203, 213, 225), KEYCAP_TEXT = new Color(51, 65, 85);

    private static final Font FONT_TITLE = new Font("Tahoma", Font.BOLD, 18), FONT_SUBTITLE = new Font("Tahoma", Font.BOLD, 14);
    private static final Font FONT_BODY = new Font("Tahoma", Font.PLAIN, 13), FONT_BODY_BOLD = new Font("Tahoma", Font.BOLD, 13);
    private static final Font FONT_SMALL = new Font("Tahoma", Font.PLAIN, 11), FONT_SMALL_BOLD = new Font("Tahoma", Font.BOLD, 11);
    private static final Font FONT_KEYCAP = new Font("Tahoma", Font.BOLD, 9);

    // ==================== State ====================
    private final List<String> guessedShapes = new ArrayList<>();   // empty at the start of every round
    private final List<PickerItem> pickers = new ArrayList<>();
    private final Map<String, Image> images = new HashMap<>();   // loaded pictures, key = shape name + size
    private final ActionListener onSubmitAction;
    private JPanel guessSlotsPanel;
    private JLabel selectedBadge, pickCounterLabel;
    private JButton submitBtn;
    private int requiredPicks;       // shapes this round needs; 0 until SECRET_LOCKED says so
    private boolean dragFromBoard;   // the drag in progress started from a placed shape (not from the picker)
    private String dragShape;        // shape being dragged right now (null when no drag is in progress)

    // ===== Multiplayer state (filled from the server) =====
    private JPanel scoreRowsPanel;   // left scoreboard, rebuilt from server scores
    private JLabel roundLabel;       // "Round 1 of 4" in the scoreboard header
    private JLabel scoreBadge;       // my own total, straight from the server
    private JLabel submittedBadge;   // submitted yes/no for this round
    private JLabel statusLabel;      // hint line under the guess card, doubles as the status line
    private boolean submitted;       // true once this round's guess has been sent
    private boolean secretLocked;    // guesses are only accepted after the questioner locks in
    private JLabel targetBadge;      // "Target: N Shapes", known only after SECRET_LOCKED
    private JLabel timeBadge;        // live mm:ss countdown fed by TICK
    private String myName = "";
    private String questionerName = "";

    public GuesserScreen(ActionListener onSubmitAction) {
        this.onSubmitAction = onSubmitAction;
        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        JPanel mainContent = panel(new BorderLayout(14, 14));
        mainContent.setBorder(new EmptyBorder(14, 24, 14, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(createColumns(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        // ENTER submits the guess while this screen is showing
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "submitGuess");
        getActionMap().put("submitGuess", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitBtn.doClick();
            }
        });
    }

    // ==================== Sub header + columns ====================

    private JPanel createSubHeader() {
        JPanel titles = panel(new GridLayout(2, 1, 0, 2));
        titles.add(label("Guess what the Questioner selected!", FONT_TITLE, TEXT_DARK));
        titles.add(label("Choose the same shapes before time runs out.", FONT_BODY, TEXT_MUTED));

        JPanel badges = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        targetBadge = createBadge("Target: ?", BLUE_LIGHT_BG, PRIMARY_BLUE, BLUE_BORDER);
        badges.add(targetBadge);
        timeBadge = createBadge("Time: --:--", WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        badges.add(timeBadge);
        scoreBadge = createBadge("Score: 0", BG_LIGHT, TEXT_DARK, CARD_BORDER);
        badges.add(scoreBadge);
        submittedBadge = createBadge("Not submitted", WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        badges.add(submittedBadge);

        JPanel subHeader = panel(new BorderLayout());
        subHeader.add(titles, BorderLayout.WEST);
        subHeader.add(badges, BorderLayout.EAST);
        return subHeader;
    }

    private JPanel createColumns() {
        JPanel left = createLeftPanel(), right = createRightPanel();
        left.setPreferredSize(new Dimension(240, 0));
        right.setPreferredSize(new Dimension(300, 0));

        JPanel columns = panel(new BorderLayout(14, 0));
        columns.add(left, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(right, BorderLayout.EAST);
        return columns;
    }

    // ==================== Left column ====================

    private JPanel createLeftPanel() {
        scoreRowsPanel = panel(new GridLayout(0, 1, 0, 8));
        roundLabel = label("Waiting to start", FONT_SMALL, TEXT_MUTED);
        JPanel scoreHeader = panel(new BorderLayout());
        scoreHeader.add(label("Scoreboard", FONT_SUBTITLE, TEXT_DARK), BorderLayout.WEST);
        scoreHeader.add(roundLabel, BorderLayout.EAST);

        JPanel scoreCard = card(new BorderLayout(0, 12));
        scoreCard.add(scoreHeader, BorderLayout.NORTH);
        scoreCard.add(scoreRowsPanel, BorderLayout.CENTER);

        JPanel ruleRows = panel(new GridLayout(0, 1, 0, 8));
        ruleRows.add(createRuleRow("+1", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER, "for each correct shape"));
        ruleRows.add(createRuleRow("+2", GOLD_BG, GOLD_TEXT, GOLD_BORDER, "all correct under 10 sec"));
        ruleRows.add(createRuleRow("0", DANGER_BG, DANGER_TEXT, DANGER_BORDER, "for an incorrect shape"));
        JPanel guideCard = card(new BorderLayout(0, 12));
        guideCard.add(createHeader("Scoring", FONT_SUBTITLE, null, null, null), BorderLayout.NORTH);
        guideCard.add(ruleRows, BorderLayout.CENTER);

        // Both cards keep their natural height; leftover space stays empty at the bottom
        JPanel left = panel(new BorderLayout(0, 12));
        left.add(scoreCard, BorderLayout.NORTH);
        left.add(pin(guideCard, BorderLayout.NORTH), BorderLayout.CENTER);
        return left;
    }

    private JPanel createScoreRow(String initial, Color avatarColor, String name, int score, boolean highlight) {
        JPanel row = panel(new BorderLayout());
        row.setOpaque(highlight);
        row.setBackground(BLUE_LIGHT_BG);
        row.setBorder(highlight ? box(BLUE_BORDER, 1, 4, 8) : new EmptyBorder(5, 9, 5, 9));

        JPanel who = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.add(createAvatar(initial, avatarColor, 24));
        who.add(label(name, FONT_BODY_BOLD, TEXT_DARK));

        row.add(who, BorderLayout.WEST);
        row.add(label(String.valueOf(score), FONT_BODY_BOLD, TEXT_DARK, SwingConstants.RIGHT), BorderLayout.EAST);
        return row;
    }

    private JPanel createRuleRow(String badgeText, Color bg, Color fg, Color border, String ruleText) {
        JLabel badge = createBadge(badgeText, bg, fg, border);
        badge.setBorder(box(border, 1, 2, 6));
        badge.setPreferredSize(new Dimension(34, 22));

        JPanel row = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.add(badge);
        row.add(label(ruleText, FONT_SMALL, TEXT_DARK));
        return row;
    }

    // ==================== Center column ====================

    private JPanel createCenterPanel() {
        selectedBadge = createBadge(picksText(), BLUE_LIGHT_BG, PRIMARY_BLUE, BLUE_BORDER);
        JPanel centerCard = card(new BorderLayout(0, 12));
        centerCard.add(createHeader("Your guess", FONT_TITLE, "Drag shapes here from Objects", FONT_BODY, selectedBadge), BorderLayout.NORTH);
        centerCard.add(createStagePanel(), BorderLayout.CENTER);

        // Helper hint sits outside the card, under its left edge; it doubles as the status line
        JPanel hintRow = panel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        hintRow.add(label("TIP", FONT_SMALL_BOLD, WARNING_TEXT));
        statusLabel = label("Waiting for the questioner to lock in the secret shapes...", FONT_SMALL, TEXT_MUTED);
        hintRow.add(statusLabel);

        JPanel center = panel(new BorderLayout(0, 10));
        center.add(centerCard, BorderLayout.CENTER);
        center.add(hintRow, BorderLayout.SOUTH);
        return center;
    }

    /** Tinted stage in 3 equal rows: correct banner (top-right), guess slots (middle), incorrect banner (bottom-left). */
    private JPanel createStagePanel() {
        guessSlotsPanel = panel(new GridLayout(1, 1, 14, 0));
        updateGuessSlots();
        JPanel middleRow = panel(new BorderLayout());
        middleRow.setBorder(new EmptyBorder(6, 24, 6, 24));
        middleRow.add(guessSlotsPanel, BorderLayout.CENTER);

        JPanel stage = new JPanel(new GridLayout(3, 1));
        stage.setBackground(STAGE_BG);
        stage.setBorder(box(STAGE_BORDER, 1, 14, 14));
        stage.add(pin(createAlertBanner("How to play", "Pick the same shapes the questioner chose.",
            BLUE_LIGHT_BG, BLUE_BORDER, PRIMARY_BLUE, FlowLayout.RIGHT), BorderLayout.NORTH));
        stage.add(middleRow);
        stage.add(pin(createAlertBanner("Scoring", "+1 per correct shape, +2 if every shape is right within 10 seconds.",
            SUCCESS_BG, SUCCESS_BORDER, SUCCESS_TEXT, FlowLayout.LEFT), BorderLayout.SOUTH));
        return stage;
    }

    /** A banner that hugs its content, wrapped in a row that spans the stage width. */
    private static JPanel createAlertBanner(String title, String desc, Color bg, Color border, Color fg, int align) {
        JPanel banner = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        banner.setBackground(bg);
        banner.setBorder(box(border, 1, 6, 8));
        banner.add(label(title, FONT_BODY_BOLD, fg));
        banner.add(label(desc, FONT_SMALL, fg));

        JPanel slot = panel(new FlowLayout(align, 0, 0));
        slot.add(banner);
        return slot;
    }

    /** One slot per shape the round needs. Until the secret is locked there is nothing to size yet. */
    private void updateGuessSlots() {
        guessSlotsPanel.removeAll();
        int slots = Math.max(1, requiredPicks);
        guessSlotsPanel.setLayout(new GridLayout(1, slots, 14, 0));
        if (requiredPicks == 0) {
            guessSlotsPanel.add(label("Waiting for the questioner to lock in the shapes...",
                FONT_BODY, TEXT_MUTED, SwingConstants.CENTER));
        } else {
            for (int i = 0; i < requiredPicks; i++) {
                guessSlotsPanel.add(createSlot(i));
            }
        }
        guessSlotsPanel.revalidate();
        guessSlotsPanel.repaint();
    }

    /** 4-5 slots need smaller pictures, otherwise the row would not fit. */
    private int slotIconSize() {
        if (requiredPicks > 3) {
            return SLOT_ICON_TIGHT;
        }
        return SLOT_ICON_WIDE;
    }

    /** A big draggable shape card when filled, an "Empty" box otherwise. Both accept drops. */
    private JPanel createSlot(int index) {
        boolean filled = index < guessedShapes.size();
        JPanel slot = new JPanel(new BorderLayout(0, filled ? 8 : 0));
        if (filled) {
            String name = guessedShapes.get(index);
            int icon = slotIconSize();
            slot.setBackground(Color.WHITE);
            slot.setBorder(box(PRIMARY_BLUE, 2, requiredPicks > 3 ? 8 : 14, 6));
            slot.add(new Pic(image(name, icon), icon, name), BorderLayout.CENTER);
            slot.add(label(name, requiredPicks > 3 ? FONT_SMALL_BOLD : FONT_BODY_BOLD,
                TEXT_DARK, SwingConstants.CENTER), BorderLayout.SOUTH);
            makeDraggable(slot, true, name); // drag it back to Objects to remove
        } else {
            slot.setBackground(EMPTY_SLOT_BG);
            slot.setBorder(new LineBorder(EMPTY_SLOT_BORDER, 2));
            slot.add(label("Empty", FONT_SMALL, TEXT_MUTED, SwingConstants.CENTER), BorderLayout.CENTER);
        }
        makeDropZone(slot, () -> true, on -> {
            slot.setBackground(on ? BLUE_LIGHT_BG : (filled ? Color.WHITE : EMPTY_SLOT_BG));
            if (!filled) {
                slot.setBorder(new LineBorder(on ? PRIMARY_BLUE : EMPTY_SLOT_BORDER, 2));
            }
            slot.repaint();
        }, shape -> placeShape(shape, index));
        return slot;
    }

    // ==================== Right column ====================

    private JPanel createRightPanel() {
        pickCounterLabel = label(picksCountText(), FONT_BODY_BOLD, PRIMARY_BLUE);
        JPanel objectsCard = card(new BorderLayout(0, 12));
        objectsCard.add(createHeader("Objects", FONT_SUBTITLE, "Drag your shapes into the guess", FONT_BODY, pickCounterLabel), BorderLayout.NORTH);

        // Fixed height so the card keeps its original size
        JPanel grid = panel(new GridLayout(4, 2, 8, 8));
        grid.setPreferredSize(new Dimension(0, 316));
        for (String[] shape : AVAILABLE_SHAPES) {
            PickerItem item = new PickerItem(shape[0]);
            pickers.add(item);
            grid.add(item);
        }
        objectsCard.add(grid, BorderLayout.CENTER);

        submitBtn = createPrimaryButton("SUBMIT GUESS");
        submitBtn.setPreferredSize(new Dimension(0, 44));
        submitBtn.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                submitGuess();
            }
        });
        objectsCard.add(submitBtn, BorderLayout.SOUTH);

        // The whole card is a "drop here to remove" zone for shapes dragged off the slots
        makeDropZone(objectsCard, () -> dragFromBoard, on -> {
            objectsCard.setBackground(on ? DANGER_BG : CARD_BG);
            objectsCard.setBorder(box(on ? DANGER_BORDER : CARD_BORDER, 1, 16, 16));
        }, this::removeShape);

        JPanel hintRow = panel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        hintRow.add(createKeyCap("ENTER"));
        hintRow.add(label("to submit your guess", FONT_SMALL, TEXT_MUTED));

        JPanel right = panel(new BorderLayout(0, 12));
        right.add(objectsCard, BorderLayout.NORTH);
        right.add(pin(hintRow, BorderLayout.NORTH), BorderLayout.CENTER);
        return right;
    }

    /** One cell of the Objects picker: picture + label, restyled on selection. */
    private class PickerItem extends JPanel {
        private final String shapeName;
        private final JLabel nameLabel;

        PickerItem(String shapeName) {
            super(new BorderLayout());
            this.shapeName = shapeName;
            nameLabel = label(shapeName, FONT_SMALL_BOLD, TEXT_DARK, SwingConstants.CENTER);
            add(new Pic(image(shapeName, 34), 34, shapeName), BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
            makeDraggable(this, false, shapeName);
            applySelectionStyle();
        }

        void applySelectionStyle() {
            boolean selected = guessedShapes.contains(shapeName);
            setBackground(selected ? BLUE_LIGHT_BG : Color.WHITE);
            // Border thickness + padding always add up to 8px, so the tile never jumps in size
            setBorder(selected ? box(PRIMARY_BLUE, 2, 6, 4) : box(CARD_BORDER, 1, 7, 5));
            nameLabel.setForeground(selected ? PRIMARY_BLUE : TEXT_DARK);
            repaint();
        }
    }

    /** Puts a shape into the slot at slotIndex. The guess list has no gaps, so an empty slot appends to the end. */
    private void placeShape(String shapeName, int slotIndex) {
        if (submitted) {
            return; // the guess is already locked in for this round
        }
        int from = guessedShapes.indexOf(shapeName);
        if (from >= 0) {
            if (slotIndex >= guessedShapes.size()) {
                guessedShapes.remove(from);
                guessedShapes.add(shapeName);
            } else {
                Collections.swap(guessedShapes, from, slotIndex);
            }
        } else if (slotIndex < guessedShapes.size()) {
            guessedShapes.set(slotIndex, shapeName);
        } else if (guessedShapes.size() < requiredPicks) {
            guessedShapes.add(shapeName);
        }
        refreshSelection();
    }

    private void removeShape(String shapeName) {
        if (submitted) {
            return;
        }
        if (guessedShapes.remove(shapeName)) {
            refreshSelection();
        }
    }

    // =====================================================================
    //  Multiplayer hooks (called by MainApp from the network listener)
    // =====================================================================

    public void setMyName(String name) {
        this.myName = (name == null) ? "" : name;
    }

    /**
     * Starts a round. The server already announced how many shapes it drew, so the guess slots
     * are sized right away; guessing and the shared clock stay locked until the questioner
     * locks in the secret (see onSecretLocked).
     */
    public void startRound(int roundNumber, int totalRounds, String questioner, int requiredShapes) {
        submitted = false;
        secretLocked = false;
        if (requiredShapes >= GameServer.MIN_SHAPES && requiredShapes <= GameServer.MAX_SHAPES) {
            requiredPicks = requiredShapes;
        } else {
            requiredPicks = GameServer.MIN_SHAPES;
        }
        questionerName = (questioner == null) ? "" : questioner;
        guessedShapes.clear();
        if (roundLabel != null) {
            roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
        }
        setTargetBadge(requiredPicks);
        showWaitingTime();
        setStatus("Waiting for questioner (" + questionerName + ") to lock in the shapes...");
        updateSubmittedBadge();
        refreshSelection();
        setSubmitEnabled(false);
    }

    /**
     * Called when the server confirms the questioner locked in the secret. The count comes from the
     * server, so the number of slots follows whatever the questioner actually picked (1-5).
     */
    public void onSecretLocked(String questioner, int count) {
        secretLocked = true;
        questionerName = (questioner == null) ? "" : questioner;
        requiredPicks = Math.max(1, count);
        guessedShapes.clear();
        setTargetBadge(requiredPicks);
        setStatus("The questioner locked in " + requiredPicks + " shape"
                + (requiredPicks == 1 ? "" : "s") + ". Pick yours, then press SUBMIT GUESS.");
        setSubmitEnabled(!submitted);
        if (!submitted) {
            setSubmittedBadge("Not submitted", WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }
        refreshSelection();
    }

    /** How many shapes this round needs (0 while the questioner has not locked in yet). */
    public int getRequiredPicks() {
        return requiredPicks;
    }

    /** Countdown fed by the server's TICK; the last ten seconds turn red. */
    public void setTimeLeft(int seconds) {
        if (timeBadge == null) {
            return;
        }
        timeBadge.setText(secondsText(seconds));
        if (seconds <= GameServer.BONUS_WINDOW_SECONDS) {
            setBadgeColors(timeBadge, DANGER_BG, DANGER_TEXT, DANGER_BORDER);
        } else {
            setBadgeColors(timeBadge, WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }
    }

    /** The guessers' clock has not started: it only runs after the questioner submits. */
    private void showWaitingTime() {
        if (timeBadge == null) {
            return;
        }
        timeBadge.setText("Time: --:--");
        setBadgeColors(timeBadge, WARNING_BG, WARNING_TEXT, WARNING_BORDER);
    }

    private void setTargetBadge(int count) {
        if (targetBadge == null) {
            return;
        }
        if (count <= 0) {
            targetBadge.setText("Target: ?");
        } else if (count == 1) {
            targetBadge.setText("Target: 1 Shape");
        } else {
            targetBadge.setText("Target: " + count + " Shapes");
        }
    }

    /** "Time: mm:ss", never below zero. */
    private static String secondsText(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("Time: %02d:%02d", safe / 60, safe % 60);
    }

    private String picksText() {
        return picksCountText() + " selected";
    }

    private String picksCountText() {
        if (requiredPicks == 0) {
            return guessedShapes.size() + " / ?";
        }
        return guessedShapes.size() + " / " + requiredPicks;
    }

    private void setBadgeColors(JLabel badge, Color bg, Color fg, Color border) {
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setBorder(box(border, 1, 4, 10));
        badge.repaint();
    }

    public boolean isSubmitted() {
        return submitted;
    }

    public java.util.List<String> getGuessedShapes() {
        return new ArrayList<>(guessedShapes);
    }

    /** Locks the guess after the server accepted it, so it cannot be sent twice. */
    public void markSubmitted() {
        submitted = true;
        setSubmitEnabled(false);
        setStatus("Guess submitted. Waiting for the other players...");
        setSubmittedBadge("Submitted", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER);
    }

    /** Updates the hint line under the guess card. */
    public void setStatus(String text) {
        if (statusLabel != null) {
            statusLabel.setText(text);
        }
    }

    /** Rebuilds the left scoreboard straight from the server's numbers. */
    public void setScoreboard(String[] names, int[] scores) {
        if (scoreRowsPanel == null) {
            return;
        }
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            int score = (i < scores.length) ? scores[i] : 0;
            boolean me = names[i].equals(myName);
            if (me && scoreBadge != null) {
                scoreBadge.setText("Score: " + score);
            }
            scoreRowsPanel.add(createScoreRow(initialOf(names[i]), avatarColor(i), names[i], score, me));
        }
        scoreRowsPanel.revalidate();
        scoreRowsPanel.repaint();
    }

    private void submitGuess() {
        if (submitted) {
            JOptionPane.showMessageDialog(this, "You already submitted a guess this round.",
                "Already submitted", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (!secretLocked) {
            JOptionPane.showMessageDialog(this,
                "Wait for the questioner to lock in the secret shapes first.",
                "Not yet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (requiredPicks == 0) {
            JOptionPane.showMessageDialog(this,
                "Wait for the questioner to lock in the shapes first.",
                "Not yet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (guessedShapes.size() != requiredPicks) {
            JOptionPane.showMessageDialog(this,
                "Pick exactly " + requiredPicks + " shape" + (requiredPicks == 1 ? "" : "s")
                    + " before submitting.",
                "Not ready yet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (onSubmitAction == null) {
            markSubmitted(); // preview mode with no network attached
            return;
        }
        onSubmitAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "submit"));
    }

    private void setSubmitEnabled(boolean enabled) {
        if (submitBtn != null) {
            submitBtn.setEnabled(enabled);
            submitBtn.setBackground(enabled ? PRIMARY_BLUE : new Color(203, 213, 225));
        }
    }

    private void updateSubmittedBadge() {
        if (submitted) {
            setSubmittedBadge("Submitted", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER);
        } else {
            setSubmittedBadge("Not submitted", WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }
    }

    private void setSubmittedBadge(String text, Color bg, Color fg, Color border) {
        if (submittedBadge == null) {
            return;
        }
        submittedBadge.setText(text);
        submittedBadge.setBackground(bg);
        submittedBadge.setForeground(fg);
        submittedBadge.setBorder(box(border, 1, 4, 10));
        submittedBadge.repaint();
    }

    private static String initialOf(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        return name.substring(0, 1).toUpperCase();
    }

    private static Color avatarColor(int index) {
        Color[] palette = {
            new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153),
            new Color(16, 185, 129), new Color(245, 158, 11), new Color(14, 165, 233),
            new Color(244, 63, 94), new Color(99, 102, 241)
        };
        return palette[Math.abs(index) % palette.length];
    }

    /** Re-syncs every widget that depends on how many shapes are currently picked. */
    private void refreshSelection() {
        updateGuessSlots();
        if (selectedBadge != null) {
            selectedBadge.setText(picksText());
        }
        if (pickCounterLabel != null) {
            pickCounterLabel.setText(picksCountText());
        }
        pickers.forEach(PickerItem::applySelectionStyle);
    }

    // =====================================================================
    //  Drag and drop (java.awt.dnd)
    // =====================================================================

    /** Lets the user drag this component; the drag carries shapeName. */
    private void makeDraggable(JComponent source, boolean fromBoard, String shapeName) {
        source.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(source, DnDConstants.ACTION_MOVE, e -> {
            if (submitted) {
                return; // the guess is locked in for this round
            }
            dragFromBoard = fromBoard;
            dragShape = shapeName;
            DragGhost ghost = new DragGhost(e.getComponent(), shapeName);
            try {
                e.startDrag(null, new StringSelection(shapeName), ghost);
            } catch (InvalidDnDOperationException ex) {
                ghost.finish();
            }
        });
    }

    /**
     * The picture that follows the mouse while dragging: an ordinary component on the window's drag layer,
     * kept centered on the pointer, so it looks the same on every platform. It never takes mouse or drop events.
     */
    private class DragGhost extends DragSourceAdapter {
        private final JLayeredPane layer;
        private final JComponent picture;

        DragGhost(Component source, String shapeName) {
            JRootPane root = SwingUtilities.getRootPane(source);
            layer = (root == null) ? null : root.getLayeredPane();
            picture = new Pic(image(shapeName, GHOST_SIZE), GHOST_SIZE, shapeName) {
                @Override
                protected void paintComponent(Graphics g) {
                    ((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
                    super.paintComponent(g);
                }

                @Override
                public boolean contains(int x, int y) {
                    return false; // invisible to mouse and drop hit-testing
                }
            };
            if (layer != null) {
                picture.setSize(GHOST_SIZE, GHOST_SIZE);
                layer.add(picture, JLayeredPane.DRAG_LAYER);
                PointerInfo pointer = MouseInfo.getPointerInfo();
                if (pointer != null) {
                    moveTo(pointer.getLocation());
                }
                DragSource.getDefaultDragSource().addDragSourceMotionListener(this);
            }
        }

        /** Centers the picture on a point given in screen coordinates. */
        private void moveTo(Point screen) {
            Point p = new Point(screen);
            SwingUtilities.convertPointFromScreen(p, layer);
            picture.setLocation(p.x - GHOST_SIZE / 2, p.y - GHOST_SIZE / 2);
        }

        @Override
        public void dragMouseMoved(DragSourceDragEvent e) {
            moveTo(e.getLocation());
        }

        @Override
        public void dragDropEnd(DragSourceDropEvent e) {
            finish();
        }

        /** Removes the picture and forgets the drag. Safe to call more than once. */
        void finish() {
            DragSource.getDefaultDragSource().removeDragSourceMotionListener(this);
            if (layer != null) {
                Rectangle area = picture.getBounds();
                layer.remove(picture);
                layer.repaint(area);
            }
            dragShape = null;
        }
    }

    /**
     * Turns a component into a drop zone. accepts = may the drag in progress land here, highlight = light it up or off,
     * onDrop = what to do with the dropped shape (runs after the drop event, because the UI gets rebuilt).
     * Children without their own drop target hand the drop to their parent, so this covers everything inside.
     */
    private void makeDropZone(JComponent target, BooleanSupplier accepts, Consumer<Boolean> highlight, Consumer<String> onDrop) {
        new DropTarget(target, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
            private boolean canDrop() {
                return dragShape != null && accepts.getAsBoolean();
            }

            @Override
            public void dragEnter(DropTargetDragEvent e) {
                dragOver(e);
                if (canDrop()) {
                    highlight.accept(true);
                }
            }

            @Override
            public void dragOver(DropTargetDragEvent e) {
                if (canDrop()) {
                    e.acceptDrag(DnDConstants.ACTION_MOVE);
                } else {
                    e.rejectDrag();
                }
            }

            @Override
            public void dragExit(DropTargetEvent e) {
                highlight.accept(false);
            }

            @Override
            public void drop(DropTargetDropEvent e) {
                highlight.accept(false);
                if (!canDrop()) {
                    e.rejectDrop();
                    return;
                }
                String shapeName = dragShape;
                e.acceptDrop(DnDConstants.ACTION_MOVE);
                e.dropComplete(true);
                SwingUtilities.invokeLater(() -> onDrop.accept(shapeName));
            }
        }, true);
    }

    // =====================================================================
    //  Pictures (AWT Toolkit, folder: <project folder>/shape)
    // =====================================================================

    /** Picture of a shape scaled to size x size (cached). Null when the file is missing. */
    private Image image(String shapeName, int size) {
        String key = shapeName + size;
        if (!images.containsKey(key)) {
            String file = "Circle.png";
            for (String[] s : AVAILABLE_SHAPES) {
                if (s[0].equalsIgnoreCase(shapeName)) {
                    file = s[1];
                }
            }
            Image scaled = Toolkit.getDefaultToolkit().createImage(SHAPE_DIR + file).getScaledInstance(size, size, Image.SCALE_SMOOTH);
            MediaTracker tracker = new MediaTracker(this);
            tracker.addImage(scaled, 0);
            try {
                tracker.waitForID(0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            images.put(key, tracker.isErrorID(0) ? null : scaled);
        }
        return images.get(key);
    }

    /** Draws a picture centered in its area (a gray box with the name when the file is missing). */
    private static class Pic extends JComponent {
        private final Image img;
        private final int size;
        private final String name;

        Pic(Image img, int size, String name) {
            this.img = img;
            this.size = size;
            this.name = name;
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
            if (img != null) {
                g.drawImage(img, x, y, size, size, this);
                return;
            }
            g.setColor(new Color(220, 230, 242));
            g.fillRect(x, y, size, size);
            g.setColor(new Color(70, 90, 120));
            g.drawRect(x, y, size - 1, size - 1);
            g.setFont(new Font("Tahoma", Font.BOLD, Math.max(10, size / 6)));
            FontMetrics fm = g.getFontMetrics();
            String text = name.length() > 6 ? name.substring(0, 6) : name;
            g.drawString(text, x + (size - fm.stringWidth(text)) / 2, y + (size - fm.getHeight()) / 2 + fm.getAscent());
        }
    }

    // ==================== Shared helpers ====================

    private static CompoundBorder box(Color line, int thickness, int vPad, int hPad) {
        return new CompoundBorder(new LineBorder(line, thickness), new EmptyBorder(vPad, hPad, vPad, hPad));
    }

    /** Transparent panel with the given layout. */
    private static JPanel panel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setOpaque(false);
        return p;
    }

    /** Transparent panel that keeps c at its natural size at the given edge (NORTH or SOUTH). */
    private static JPanel pin(JComponent c, String edge) {
        JPanel p = panel(new BorderLayout());
        p.add(c, edge);
        return p;
    }

    /** White card with a thin border and padding. */
    private static JPanel card(LayoutManager layout) {
        JPanel card = new JPanel(layout);
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, 16, 16));
        return card;
    }

    private static JLabel label(String text, Font font, Color color, int align) {
        JLabel label = new JLabel(text, align);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JLabel label(String text, Font font, Color color) {
        return label(text, font, color, SwingConstants.LEADING);
    }

    /** Small label with fill + border. Use setText() to update it later. */
    private static JLabel createBadge(String text, Color bg, Color fg, Color border) {
        JLabel badge = label(text, FONT_SMALL_BOLD, fg, SwingConstants.CENTER);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(box(border, 1, 4, 10));
        return badge;
    }

    /** Square avatar tile with the player's initial. */
    private static JLabel createAvatar(String letter, Color bg, int size) {
        JLabel avatar = label(letter, new Font("Tahoma", Font.BOLD, size / 2), Color.WHITE, SwingConstants.CENTER);
        avatar.setBackground(bg);
        avatar.setOpaque(true);
        avatar.setPreferredSize(new Dimension(size, size));
        return avatar;
    }

    private static JLabel createKeyCap(String text) {
        JLabel key = label(text, FONT_KEYCAP, KEYCAP_TEXT, SwingConstants.CENTER);
        key.setBackground(Color.WHITE);
        key.setOpaque(true);
        key.setBorder(box(EMPTY_SLOT_BORDER, 1, 3, 6));
        return key;
    }

    private static JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
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

    /** Two-line card header: bold title, optional muted sub line under it, optional widget pinned right of the title. */
    private static JPanel createHeader(String title, Font titleFont, String sub, Font subFont, JComponent right) {
        JPanel titleRow = panel(new BorderLayout());
        titleRow.add(label(title, titleFont, TEXT_DARK), BorderLayout.WEST);
        if (right != null) {
            JPanel rightWrap = panel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            rightWrap.add(right);
            titleRow.add(rightWrap, BorderLayout.EAST);
        }
        JPanel header = panel(new BorderLayout(0, 2));
        header.add(titleRow, BorderLayout.NORTH);
        if (sub != null) {
            header.add(label(sub, subFont, TEXT_MUTED), BorderLayout.CENTER);
        }
        return header;
    }
}