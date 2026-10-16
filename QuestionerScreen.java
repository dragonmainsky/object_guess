import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

/**
 * QuestionerScreen: screen 3 - the questioner picks the secret shapes. A shape is dragged from the
 * Objects picker onto the canvas, dragged around on the canvas, or dragged back to remove it.
 */
public class QuestionerScreen extends JPanel {

    private static final int SMALL_ICON = 36;    // picture inside a picker card
    private static final int BIG_ICON = 100;     // picture on the canvas, and under the mouse while dragging
    private static final int LEFT_WIDTH = 260;
    private static final int RIGHT_WIDTH = 330;
    private static final Color CANVAS_BG = new Color(250, 252, 255);
    private static final String HINT = "Drag a shape to move it, or back to Objects to remove it";

    // ===== State =====
    private final Set<String> selectedShapes = new LinkedHashSet<>();   // empty at the start of every round
    private final List<ShapeImages.Picker> pickers = new ArrayList<>();
    private final Map<String, double[]> shapeSpots = new HashMap<>();   // shape -> {x, y} as fractions of the canvas
    private final List<StepRow> stepRows = new ArrayList<>();
    private final ShapeImages.DragBoard drag;
    private final ActionListener onConfirmAction;

    private DashedCanvas canvas;
    private JLabel selectedCounterLabel;
    private JLabel readyBadge;
    private JPanel scoreRowsPanel;
    private JLabel roundLabel;
    private JLabel hintLabel;
    private JButton confirmButton;
    private JLabel timeBadge;
    private JLabel targetBadge;
    private int requiredShapes = GameServer.MIN_SHAPES;   // drawn by the server for every round
    private boolean selectionLocked;
    private boolean roundActive;                          // false before the first round of the game
    private String myName = "";

    public QuestionerScreen(ActionListener onConfirmAction) {
        this.onConfirmAction = onConfirmAction;
        drag = new ShapeImages.DragBoard(this, BIG_ICON);
        setLayout(new BorderLayout());
        setBackground(AppDialog.BG_LIGHT);

        var leftColumn = createLeftPanel();
        var rightColumn = createRightPanel();
        leftColumn.setPreferredSize(new Dimension(LEFT_WIDTH, leftColumn.getPreferredSize().height));
        rightColumn.setPreferredSize(new Dimension(RIGHT_WIDTH, rightColumn.getPreferredSize().height));

        var columns = AppDialog.panel();
        columns.setLayout(new BorderLayout(12, 0));
        columns.add(leftColumn, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(rightColumn, BorderLayout.EAST);

        var mainContent = AppDialog.panel();
        mainContent.setLayout(new BorderLayout(12, 12));
        mainContent.setBorder(new EmptyBorder(18, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);
        refreshUI();
    }

    /** The picture that follows the mouse while a shape is dragged is painted over the whole screen. */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        drag.paintGhost(g);
    }

    // =====================================================================
    //  Sections
    // =====================================================================

    private JPanel createSubHeader() {
        var titles = AppDialog.panel();
        titles.setLayout(new GridLayout(2, 1, 0, 2));
        titles.add(AppDialog.label("YOUR TURN TO CHOOSE", AppDialog.SUBTITLE, AppDialog.PRIMARY_BLUE));
        titles.add(AppDialog.label("Select the shapes the others must guess.", AppDialog.TITLE_LARGE, AppDialog.TEXT_DARK));

        targetBadge = AppDialog.topBadge(AppDialog.targetText(requiredShapes), AppDialog.BLUE_LIGHT_BG,
                AppDialog.PRIMARY_BLUE, AppDialog.BLUE_BORDER, false);
        timeBadge = AppDialog.topBadge("Time: --:--", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, true);
        var badges = AppDialog.panel();
        badges.setLayout(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        badges.add(targetBadge);
        badges.add(timeBadge);

        var subHeader = AppDialog.panel();
        subHeader.setLayout(new BorderLayout());
        subHeader.setBorder(new EmptyBorder(4, 0, 6, 0));
        subHeader.add(titles, BorderLayout.WEST);
        // CENTER lets the badge row wrap onto a second line instead of being clipped in a narrow window.
        subHeader.add(badges, BorderLayout.CENTER);
        return subHeader;
    }

    private JPanel createLeftPanel() {
        var playersCard = AppDialog.stack(AppDialog.card());
        playersCard.add(alignLeft(AppDialog.label("Players", AppDialog.SUBTITLE, AppDialog.TEXT_DARK)));
        playersCard.add(alignLeft(AppDialog.label("Live round score", AppDialog.SMALL, AppDialog.TEXT_MUTED)));
        playersCard.add(AppDialog.strut(12));
        scoreRowsPanel = AppDialog.stack(AppDialog.panel());
        playersCard.add(alignLeft(scoreRowsPanel));
        playersCard.add(AppDialog.strut(14));

        // The divider is just the top border of the tip box.
        var tipLines = AppDialog.panel();
        tipLines.setLayout(new GridLayout(2, 1));
        tipLines.add(AppDialog.label("Info: Guessers score +1 per correct shape,", AppDialog.SMALL, AppDialog.TEXT_MUTED));
        tipLines.add(AppDialog.label("and a wrong guess scores nothing for anyone.", AppDialog.SMALL, AppDialog.TEXT_MUTED));
        var tipBox = AppDialog.pin(tipLines, BorderLayout.NORTH);
        tipBox.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppDialog.CARD_BORDER),
                new EmptyBorder(10, 0, 0, 0)));
        playersCard.add(alignLeft(tipBox));

        var roundCard = AppDialog.stack(AppDialog.card());
        roundLabel = AppDialog.label("Waiting to start", AppDialog.SUBTITLE, AppDialog.TEXT_DARK);
        roundCard.add(alignLeft(roundLabel));
        roundCard.add(AppDialog.strut(12));
        roundCard.add(createStepRow(1, "Choose your shapes").row);
        roundCard.add(AppDialog.strut(10));
        roundCard.add(createStepRow(2, "Confirm selection").row);
        roundCard.add(AppDialog.strut(10));
        roundCard.add(createStepRow(3, "Watch guesses").row);

        var column = AppDialog.stack(AppDialog.panel());
        column.add(alignLeft(playersCard, 290));
        column.add(AppDialog.strut(12));
        column.add(alignLeft(roundCard, 200));
        column.add(AppDialog.glue());
        return column;
    }

    private JPanel createCenterPanel() {
        var titles = AppDialog.panel();
        titles.setLayout(new GridLayout(2, 1, 0, 2));
        titles.add(AppDialog.label("Your secret set", AppDialog.TITLE, AppDialog.TEXT_DARK));
        titles.add(AppDialog.label("Drag your shapes onto the canvas", AppDialog.BODY, AppDialog.TEXT_MUTED));

        readyBadge = AppDialog.badge("READY TO CONFIRM", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                AppDialog.SUCCESS_BORDER, 6, 14);
        var cardHeader = AppDialog.panel();
        cardHeader.setLayout(new BorderLayout());
        cardHeader.add(titles, BorderLayout.WEST);
        cardHeader.add(centered(readyBadge), BorderLayout.EAST);

        // The canvas takes shapes from the picker and shapes being moved around on it.
        canvas = new DashedCanvas();
        drag.dropZone(canvas,
                () -> drag.fromBoard() || selectedShapes.contains(drag.shape()) || selectedShapes.size() < requiredShapes,
                canvas::setDropHighlight,
                (shapeName, where) -> {
                    if (selectionLocked) {
                        return;
                    }
                    if (selectedShapes.size() >= requiredShapes && !selectedShapes.contains(shapeName)) {
                        return;
                    }
                    selectedShapes.add(shapeName);
                    // The shape's center goes exactly where the mouse was released.
                    double fx = where.x / (double) Math.max(1, canvas.getWidth());
                    double fy = where.y / (double) Math.max(1, canvas.getHeight());
                    shapeSpots.put(shapeName, new double[] {clamp(fx), clamp(fy)});
                    refreshUI();
                });

        hintLabel = AppDialog.label(HINT, AppDialog.SMALL, AppDialog.TEXT_MUTED, JLabel.CENTER);
        var centerCard = AppDialog.card();
        centerCard.setLayout(new BorderLayout(0, 12));
        centerCard.add(cardHeader, BorderLayout.NORTH);
        centerCard.add(canvas, BorderLayout.CENTER);
        centerCard.add(hintLabel, BorderLayout.SOUTH);
        return centerCard;
    }

    private JPanel createRightPanel() {
        selectedCounterLabel = AppDialog.label("Selected: 0 / " + requiredShapes, AppDialog.SMALL_BOLD,
                AppDialog.PRIMARY_BLUE);
        var objectsHeader = AppDialog.panel();
        objectsHeader.setLayout(new BorderLayout());
        objectsHeader.add(AppDialog.label("Objects", AppDialog.SUBTITLE, AppDialog.TEXT_DARK), BorderLayout.WEST);
        objectsHeader.add(selectedCounterLabel, BorderLayout.EAST);

        var grid = AppDialog.panel();
        grid.setLayout(new GridLayout(4, 2, 8, 8));
        for (String name : ShapeImages.NAMES) {
            var picker = new ShapeImages.Picker(name, SMALL_ICON, 7, 7, true);
            pickers.add(picker);
            drag.draggable(picker, name, false, () -> selectionLocked);
            grid.add(picker);
        }

        var clearBtn = AppDialog.button("CLEAR", Color.WHITE, AppDialog.BG_LIGHT, AppDialog.CARD_BORDER,
                AppDialog.TEXT_DARK, AppDialog.CARD_BORDER, 10, 18);
        clearBtn.addActionListener(event -> {
            if (selectionLocked) {
                return;
            }
            selectedShapes.clear();
            shapeSpots.clear();
            refreshUI();
        });
        confirmButton = AppDialog.button("CONFIRM", AppDialog.PRIMARY_BLUE, AppDialog.PRIMARY_HOVER,
                AppDialog.PRIMARY_PRESS, Color.WHITE, AppDialog.PRIMARY_BLUE, 10, 18);
        confirmButton.addActionListener(event -> confirmSelection());

        var buttonRow = AppDialog.panel();
        buttonRow.setLayout(new GridLayout(1, 2, 8, 0));
        buttonRow.add(clearBtn);
        buttonRow.add(confirmButton);

        var objectsCard = AppDialog.stack(AppDialog.card());
        objectsCard.add(alignLeft(objectsHeader, 24));
        objectsCard.add(alignLeft(AppDialog.label("Drag your shapes to the canvas", AppDialog.SMALL, AppDialog.TEXT_MUTED)));
        objectsCard.add(AppDialog.strut(10));
        objectsCard.add(alignLeft(grid));
        objectsCard.add(AppDialog.strut(14));
        objectsCard.add(alignLeft(buttonRow, 46));

        // The whole card is a "drop here to remove" zone for shapes dragged off the canvas.
        drag.dropZone(objectsCard, () -> drag.fromBoard(), on -> {
            objectsCard.setBackground(on ? AppDialog.DANGER_BG : AppDialog.CARD_BG);
            objectsCard.setBorder(AppDialog.box(on ? AppDialog.DANGER_BORDER : AppDialog.CARD_BORDER, 1, 16, 16));
        }, (shapeName, where) -> {
            selectedShapes.remove(shapeName);
            shapeSpots.remove(shapeName);
            refreshUI();
        });

        // The glue soaks up the extra height, so the card keeps its natural size.
        var column = AppDialog.stack(AppDialog.panel());
        column.add(alignLeft(objectsCard, objectsCard.getPreferredSize().height));
        column.add(AppDialog.glue());
        return column;
    }

    // =====================================================================
    //  Rows and steps
    // =====================================================================

    /** One "Choose / Confirm / Watch" step row; its circle and text are remembered for setStep(). */
    private StepRow createStepRow(int number, String text) {
        var row = AppDialog.panel();
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        alignLeft(row, 28);
        var circle = AppDialog.avatar(String.valueOf(number), AppDialog.SLOT_BORDER, 20);
        var textLabel = AppDialog.label(text, AppDialog.BODY, AppDialog.TEXT_MUTED);
        row.add(circle);
        row.add(textLabel);
        var step = new StepRow(row, circle, textLabel);
        stepRows.add(step);
        return step;
    }

    /** Highlights one of the three steps: done ones green, the active one blue and bold, later ones grey. */
    private void setStep(int activeStep) {
        for (int i = 0; i < stepRows.size(); i++) {
            var step = stepRows.get(i);
            int number = i + 1;
            boolean done = number < activeStep;
            boolean current = number == activeStep;
            step.circle.setBackground(done ? AppDialog.SUCCESS_TEXT
                    : (current ? AppDialog.PRIMARY_BLUE : AppDialog.SLOT_BORDER));
            step.text.setFont(current ? AppDialog.BODY_BOLD : AppDialog.BODY);
            step.text.setForeground(current ? AppDialog.TEXT_DARK : AppDialog.TEXT_MUTED);
        }
        revalidate();
        repaint();
    }

    private JPanel createScoreRow(String initial, Color avatarColor, String name, int score, boolean highlight) {
        var who = AppDialog.panel();
        who.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.add(AppDialog.avatar(initial, avatarColor, 24));
        who.add(AppDialog.label(name, AppDialog.BODY_BOLD, AppDialog.TEXT_DARK));

        var scoreLabel = AppDialog.label(String.valueOf(score), AppDialog.BODY_BOLD, AppDialog.TEXT_DARK, JLabel.RIGHT);
        scoreLabel.setPreferredSize(new Dimension(24, 24));

        var row = AppDialog.panel();
        row.setLayout(new BorderLayout());
        alignLeft(row, 36);
        row.setOpaque(highlight);
        row.setBackground(AppDialog.BLUE_LIGHT_BG);
        row.setBorder(highlight ? AppDialog.box(AppDialog.BLUE_BORDER, 1, 4, 8) : new EmptyBorder(4, 8, 4, 8));
        row.add(who, BorderLayout.WEST);
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    // =====================================================================
    //  State changes
    // =====================================================================

    /** Rebuilds the canvas and re-syncs every widget that depends on the selection. */
    private void refreshUI() {
        canvas.removeAll();
        for (String shapeName : selectedShapes) {
            var picture = ShapeImages.shape(shapeName, BIG_ICON);
            picture.setName(shapeName);   // the canvas finds the shape's spot through this name
            picture.setToolTipText("Drag to move " + shapeName + ", or back to Objects to remove it");
            drag.draggable(picture, shapeName, true, () -> selectionLocked);
            canvas.add(picture);
        }
        canvas.revalidate();
        canvas.repaint();

        int count = selectedShapes.size();
        selectedCounterLabel.setText("Selected: " + count + " / " + requiredShapes);
        if (count == requiredShapes) {
            AppDialog.paintBadge(readyBadge, "READY TO CONFIRM", AppDialog.SUCCESS_BG, AppDialog.SUCCESS_TEXT,
                    AppDialog.SUCCESS_BORDER, 6, 14);
        } else {
            AppDialog.paintBadge(readyBadge, "PICK " + requiredShapes + (requiredShapes == 1 ? " SHAPE" : " SHAPES"),
                    AppDialog.WARNING_BG, AppDialog.WARNING_TEXT, AppDialog.WARNING_BORDER, 6, 14);
        }
        targetBadge.setText(AppDialog.targetText(requiredShapes));

        // The card look lives in the background and the border, so every cell is refreshed, not just repainted.
        for (var picker : pickers) {
            picker.setSelected(selectedShapes.contains(picker.shapeName));
        }

        // Before the first round no step is active; a locked selection stays on step 3.
        if (!roundActive) {
            setStep(0);
        } else if (selectionLocked) {
            setStep(3);
        } else if (count == requiredShapes) {
            setStep(2);
        } else {
            setStep(1);
        }
    }

    // =====================================================================
    //  Multiplayer hooks (called by MainApp from the network listener)
    // =====================================================================

    public void setMyName(String name) {
        myName = (name == null) ? "" : name;
    }

    /** Clears the canvas for a new round with the server's random shape count. */
    public void startRound(int roundNumber, int totalRounds, int requiredShapes) {
        if (requiredShapes >= GameServer.MIN_SHAPES && requiredShapes <= GameServer.MAX_SHAPES) {
            this.requiredShapes = requiredShapes;
        }
        roundActive = true;
        selectionLocked = false;
        selectedShapes.clear();
        shapeSpots.clear();
        roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
        confirmButton.setEnabled(true);
        setStatus("Pick exactly " + this.requiredShapes + " shape" + (this.requiredShapes == 1 ? "" : "s")
                + " for the others to guess, then press CONFIRM.");
        showWaitingTime();
        refreshUI();
        setStep(1);
    }

    /** How many shapes this round needs; the server draws it and every player must match it. */
    public int getRequiredPicks() {
        return requiredShapes;
    }

    /** The shapes currently on the canvas, in pick order. */
    public List<String> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    /** Locks the choice after the server accepted it; the round continues while the others guess. */
    public void lockSelection() {
        selectionLocked = true;
        confirmButton.setEnabled(false);
        setStatus("Secret locked. Waiting for the other players to submit guesses...");
        setStep(3);
    }

    /** Updates the hint line under the canvas, which doubles as the round status line. */
    public void setStatus(String text) {
        hintLabel.setText(text);
    }

    /** Countdown fed by the server's TICK; the last ten seconds turn red. */
    public void setTimeLeft(int seconds) {
        boolean hurry = seconds <= GameServer.BONUS_WINDOW_SECONDS;
        AppDialog.paintTopBadge(timeBadge, AppDialog.timeText(seconds),
                hurry ? AppDialog.DANGER_BG : AppDialog.WARNING_BG,
                hurry ? AppDialog.DANGER_TEXT : AppDialog.WARNING_TEXT,
                hurry ? AppDialog.DANGER_BORDER : AppDialog.WARNING_BORDER, true);
    }

    /** Before the secret is locked the shared clock has not started yet. */
    private void showWaitingTime() {
        AppDialog.paintTopBadge(timeBadge, "Time: --:--", AppDialog.WARNING_BG, AppDialog.WARNING_TEXT,
                AppDialog.WARNING_BORDER, true);
    }

    /** Rebuilds the live round score list straight from the server's numbers. */
    public void setScoreboard(String[] names, int[] scores) {
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                scoreRowsPanel.add(AppDialog.strut(8));
            }
            int score = (i < scores.length) ? scores[i] : 0;
            scoreRowsPanel.add(createScoreRow(AppDialog.initialOf(names[i]), AppDialog.avatarColor(i), names[i],
                    score, names[i].equals(myName)));
        }
        scoreRowsPanel.revalidate();
        scoreRowsPanel.repaint();
    }

    private void confirmSelection() {
        if (selectionLocked) {
            return;
        }
        int count = selectedShapes.size();
        if (count != requiredShapes) {
            AppDialog.showWarning(this, "Not ready yet", "Please select exactly " + requiredShapes + " shape"
                    + (requiredShapes == 1 ? "" : "s") + " before confirming.");
            return;
        }
        if (onConfirmAction != null) {
            onConfirmAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "confirm"));
        } else {
            lockSelection();   // preview mode with no network attached
        }
    }

    // =====================================================================
    //  Small helpers and inner classes
    // =====================================================================

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    /** Left aligned inside a BoxLayout column, and never taller than maxHeight. */
    private static JPanel alignLeft(JPanel c, int maxHeight) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        return alignLeft(c);
    }

    private static JPanel alignLeft(JPanel c) {
        c.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel alignLeft(JLabel c) {
        c.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return c;
    }

    /** Wraps a label so it keeps its own height and sits in the vertical middle of its column. */
    private static JPanel centered(JLabel c) {
        var wrapper = AppDialog.stack(AppDialog.panel());
        wrapper.add(AppDialog.glue());
        wrapper.add(c);
        wrapper.add(AppDialog.glue());
        return wrapper;
    }

    /** The mutable parts of one step row, so setStep() can restyle the circle and the text. */
    private static class StepRow {
        final JPanel row;
        final JLabel circle;
        final JLabel text;

        StepRow(JPanel row, JLabel circle, JLabel text) {
            this.row = row;
            this.circle = circle;
            this.text = text;
        }
    }

    /** Light canvas with a dashed cross; every shape sits on its own spot, looked up by its name. */
    private class DashedCanvas extends JPanel {
        private boolean dropHighlight;

        DashedCanvas() {
            setOpaque(false);
            setLayout(null);   // the shapes are placed by doLayout() below
        }

        /** Puts each shape's center on its spot, nudged inward so it never leaves the canvas. */
        @Override
        public void doLayout() {
            for (int i = 0; i < getComponentCount(); i++) {
                var shape = getComponent(i);
                double[] spot = shapeSpots.get(shape.getName());
                if (spot == null) {
                    continue;
                }
                Dimension size = shape.getPreferredSize();
                int x = (int) Math.round(spot[0] * getWidth() - size.width / 2.0);
                int y = (int) Math.round(spot[1] * getHeight() - size.height / 2.0);
                shape.setBounds(Math.max(0, Math.min(x, Math.max(0, getWidth() - size.width))),
                        Math.max(0, Math.min(y, Math.max(0, getHeight() - size.height))),
                        size.width, size.height);
            }
        }

        /** Tints the canvas while a shape is being dragged over it. */
        void setDropHighlight(boolean on) {
            dropHighlight = on;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            g.setColor(dropHighlight ? AppDialog.BLUE_LIGHT_BG : CANVAS_BG);
            g.fillRect(0, 0, w - 1, h - 1);
            g.setColor(dropHighlight ? AppDialog.PRIMARY_BLUE : AppDialog.CARD_BORDER);
            g.drawRect(0, 0, w - 1, h - 1);

            // Dashed cross: 6px dashes with a 4px gap.
            g.setColor(AppDialog.CARD_BORDER);
            int midY = h / 2 + 30;
            int midX = w / 2;
            for (int x = 20; x < w - 20; x += 10) {
                g.drawLine(x, midY, Math.min(x + 6, w - 20), midY);
            }
            for (int y = 20; y < h - 20; y += 10) {
                g.drawLine(midX, y, midX, Math.min(y + 6, h - 20));
            }
        }
    }
}
