import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * QuestionerScreen: Screen 3 - Questioner Mode where the player selects secret shapes.
 *
 * Drag a shape from the Objects picker and drop it on the secret canvas; it lands exactly where the mouse
 * is released. Drag a shape that is already on the canvas to move it, or back onto the Objects card to remove it.
 */
public class QuestionerScreen extends JPanel {

    // ===== Colors and fonts =====
    private static final Color BG_LIGHT = new Color(241, 245, 249), CARD_BG = Color.WHITE, CARD_BORDER = new Color(226, 232, 240);
    private static final Color HOVER_BG = new Color(248, 250, 252);
    private static final Color PRIMARY_BLUE = new Color(37, 99, 235), PRIMARY_HOVER = new Color(29, 78, 216), PRIMARY_PRESSED = new Color(30, 64, 175);
    private static final Color SECONDARY_PRESSED = new Color(226, 232, 240);
    private static final Color BLUE_LIGHT_BG = new Color(239, 246, 255), BLUE_BORDER = new Color(191, 219, 254);
    private static final Color SUCCESS_BG = new Color(220, 252, 231), SUCCESS_TEXT = new Color(22, 101, 52), SUCCESS_BORDER = new Color(187, 247, 208);
    private static final Color WARNING_BG = new Color(254, 249, 195), WARNING_TEXT = new Color(133, 77, 14), WARNING_BORDER = new Color(254, 240, 138);
    private static final Color DANGER_BG = new Color(254, 226, 226), DANGER_BORDER = new Color(254, 202, 202);
    private static final Color DANGER_TEXT = new Color(153, 27, 27);
    private static final Color TEXT_DARK = new Color(15, 23, 42), TEXT_MUTED = new Color(100, 116, 139);
    private static final Color CANVAS_BG = new Color(250, 252, 255), CANVAS_LINE = new Color(226, 232, 240), STEP_INACTIVE = new Color(203, 213, 225);

    private static final Font FONT_TITLE = new Font("Tahoma", Font.BOLD, 18), FONT_SUBTITLE = new Font("Tahoma", Font.BOLD, 14);
    private static final Font FONT_BODY = new Font("Tahoma", Font.PLAIN, 13), FONT_BODY_BOLD = new Font("Tahoma", Font.BOLD, 13);
    private static final Font FONT_SMALL = new Font("Tahoma", Font.PLAIN, 11), FONT_SMALL_BOLD = new Font("Tahoma", Font.BOLD, 11);

    // ===== Sizes =====
    private static final int SMALL_ICON = 36;   // picture inside a picker card
    private static final int BIG_ICON = 100;    // picture on the secret canvas (and under the mouse while dragging)
    private static final int LEFT_WIDTH = 260;
    private static final int RIGHT_WIDTH = 330;

    // Folder that holds every PNG picture: <project folder>/shape
    private static final String SHAPE_DIR = System.getProperty("user.dir") + "/shape/";

    // Available shapes: {display name, picture file}
    private static final String[][] AVAILABLE_SHAPES = {
        {"Circle", "Circle.png"}, {"Triangle", "Triangle.png"}, {"Rectangle", "Rectangle.png"}, {"Oval", "Oval.png"},
        {"Hexagon", "Hexagon.png"}, {"Diamond", "diamond.png"}, {"Star", "star.png"}, {"Square", "Square.png"}
    };

    // ===== State =====
    private final Set<String> selectedShapes = new LinkedHashSet<>();   // empty at the start of every round
    private final List<ShapeCard> shapeCards = new ArrayList<>();
    private final Map<String, Image> images = new HashMap<>();   // loaded pictures, key = shape name + size
    // Where each selected shape sits on the canvas: {x, y} as fractions (0..1) of the canvas size, at the shape's center
    private final Map<String, double[]> shapeSpots = new HashMap<>();
    private final ActionListener onConfirmAction;
    private DashedCanvas secretSetCanvas;
    private JLabel selectedCounterLabel, readyBadge;
    private boolean dragFromBoard;   // the drag in progress started from a shape on the canvas (not from the picker)
    private String dragShape;        // shape being dragged right now (null when no drag is in progress)

    // ===== Multiplayer state (filled from the server) =====
    private static final String DEFAULT_HINT = "Drag a shape to move it, or back to Objects to remove it";
    private JPanel scoreRowsPanel;      // "Players / live round score" rows, rebuilt from server scores
    private JLabel roundLabel;          // "Round 1 of 4"
    private JLabel hintLabel;           // the hint line under the canvas, doubles as the status line
    private JButton confirmButton;
    private JLabel timeBadge;           // live mm:ss countdown fed by TICK
    private JLabel targetBadge;         // "Target: N Shapes" for the round's random count
    private int requiredShapes = GameServer.MIN_SHAPES;   // drawn by the server each round (1-5)
    private boolean selectionLocked;
    private boolean roundActive;   // true from startRound() until the next round; false before the first round
    private final List<StepRow> stepRows = new ArrayList<>();   // the 3 "Choose / Confirm / Watch" rows
    private String myName = "";

    public QuestionerScreen(ActionListener onConfirmAction) {
        this.onConfirmAction = onConfirmAction;

        setLayout(new BorderLayout());
        setBackground(BG_LIGHT);

        // 3 columns: fixed-width left, flexible center, fixed-width right
        JPanel leftColumn = createLeftPanel(), rightColumn = createRightPanel();
        leftColumn.setPreferredSize(new Dimension(LEFT_WIDTH, leftColumn.getPreferredSize().height));
        rightColumn.setPreferredSize(new Dimension(RIGHT_WIDTH, rightColumn.getPreferredSize().height));

        JPanel columns = panel();
        columns.setLayout(new BorderLayout(12, 0));
        columns.add(leftColumn, BorderLayout.WEST);
        columns.add(createCenterPanel(), BorderLayout.CENTER);
        columns.add(rightColumn, BorderLayout.EAST);

        JPanel mainContent = panel();
        mainContent.setLayout(new BorderLayout(16, 16));
        mainContent.setBorder(new EmptyBorder(14, 24, 16, 24));
        mainContent.add(createSubHeader(), BorderLayout.NORTH);
        mainContent.add(columns, BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        refreshUI();
    }

    // =====================================================================
    //  Sections
    // =====================================================================

    private JPanel createSubHeader() {
        JPanel titles = panel();
        titles.setLayout(new GridLayout(2, 1, 0, 2));
        titles.add(label("YOUR TURN TO CHOOSE", FONT_BODY_BOLD, PRIMARY_BLUE));
        titles.add(label("Select the shapes the others must guess.", FONT_TITLE, TEXT_DARK));

        // Badges on right: Target & Time, sized exactly like the GuesserScreen sub header
        JPanel badgeGroup = panel();
        badgeGroup.setLayout(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        targetBadge = makeTopBadge(targetText(), BLUE_LIGHT_BG, PRIMARY_BLUE, BLUE_BORDER);
        badgeGroup.add(targetBadge);
        timeBadge = makeTopBadge(waitingTimeText(), WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        badgeGroup.add(timeBadge);

        JPanel subHeader = panel();
        subHeader.setLayout(new BorderLayout());
        subHeader.add(titles, BorderLayout.WEST);
        subHeader.add(badgeGroup, BorderLayout.EAST);
        return subHeader;
    }

    private JPanel createLeftPanel() {
        // Card 1: Players live round score
        JPanel playersCard = stack(card());
        playersCard.add(alignLeft(label("Players", FONT_SUBTITLE, TEXT_DARK)));
        playersCard.add(alignLeft(label("Live round score", FONT_SMALL, TEXT_MUTED)));
        playersCard.add(strut(12));
        scoreRowsPanel = stack(panel());
        playersCard.add(alignLeft(scoreRowsPanel));
        playersCard.add(strut(14));

        // The divider is just the top border of the tip box
        JPanel tipLines = panel();
        tipLines.setLayout(new GridLayout(2, 1));
        tipLines.add(label("Info: Guessers score +1 per correct shape,", FONT_SMALL, TEXT_MUTED));
        tipLines.add(label("and a wrong guess scores nothing for anyone.", FONT_SMALL, TEXT_MUTED));
        JPanel tipBox = pin(tipLines, BorderLayout.NORTH);
        tipBox.setBorder(new CompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, CARD_BORDER), new EmptyBorder(10, 0, 0, 0)));
        playersCard.add(alignLeft(tipBox));

        // Card 2: Round 1 of 4 steps
        JPanel roundCard = stack(card());
        roundLabel = label("Waiting to start", FONT_SUBTITLE, TEXT_DARK);
        roundCard.add(alignLeft(roundLabel));
        roundCard.add(strut(12));
        roundCard.add(createStepRow(1, "Choose your shapes").row);
        roundCard.add(strut(10));
        roundCard.add(createStepRow(2, "Confirm selection").row);
        roundCard.add(strut(10));
        roundCard.add(createStepRow(3, "Watch guesses").row);

        JPanel column = stack(panel());
        column.add(alignLeft(playersCard, 290));
        column.add(strut(12));
        column.add(alignLeft(roundCard, 200));
        column.add(glue());
        return column;
    }

    private JPanel createCenterPanel() {
        JPanel titleGroup = panel();
        titleGroup.setLayout(new GridLayout(2, 1, 0, 2));
        titleGroup.add(label("Your secret set", FONT_TITLE, TEXT_DARK));
        titleGroup.add(label("Drag your shapes onto the canvas", FONT_BODY, TEXT_MUTED));
        readyBadge = makeBadge("READY TO CONFIRM", SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER, FONT_SMALL_BOLD);
        JPanel cardHeader = panel();
        cardHeader.setLayout(new BorderLayout());
        cardHeader.add(titleGroup, BorderLayout.WEST);
        cardHeader.add(centerVertically(readyBadge), BorderLayout.EAST);

        // Secret set canvas (its content is filled in by refreshUI). It accepts shapes from the
        // Objects picker and shapes being moved around on it.
        secretSetCanvas = new DashedCanvas();
        makeDropZone(secretSetCanvas,
            () -> dragFromBoard || selectedShapes.contains(dragShape) || selectedShapes.size() < requiredShapes,
            secretSetCanvas::setDropHighlight,
            (shapeName, where) -> {
                if (selectionLocked) {
                    return;
                }
                if (selectedShapes.size() >= requiredShapes && !selectedShapes.contains(shapeName)) {
                    return;
                }
                selectedShapes.add(shapeName);
                // The shape's center goes exactly where the mouse was released
                double fx = where.x / (double) Math.max(1, secretSetCanvas.getWidth());
                double fy = where.y / (double) Math.max(1, secretSetCanvas.getHeight());
                shapeSpots.put(shapeName, new double[] {Math.max(0, Math.min(1, fx)), Math.max(0, Math.min(1, fy))});
                refreshUI();
            });

        hintLabel = label(DEFAULT_HINT, FONT_SMALL, TEXT_MUTED, JLabel.CENTER);

        JPanel centerCard = card();
        centerCard.setLayout(new BorderLayout(0, 12));
        centerCard.add(cardHeader, BorderLayout.NORTH);
        centerCard.add(secretSetCanvas, BorderLayout.CENTER);
        centerCard.add(hintLabel, BorderLayout.SOUTH);
        return centerCard;
    }

    private JPanel createRightPanel() {
        // ----- Objects picker card -----
        selectedCounterLabel = label("Selected: " + selectedShapes.size() + " / " + requiredShapes, FONT_SMALL_BOLD, PRIMARY_BLUE);
        JPanel objectsHeader = panel();
        objectsHeader.setLayout(new BorderLayout());
        objectsHeader.add(label("Objects", FONT_SUBTITLE, TEXT_DARK), BorderLayout.WEST);
        objectsHeader.add(selectedCounterLabel, BorderLayout.EAST);

        JPanel grid = panel();
        grid.setLayout(new GridLayout(4, 2, 8, 8));
        for (String[] shape : AVAILABLE_SHAPES) {
            ShapeCard tile = new ShapeCard(shape[0]);
            shapeCards.add(tile);
            grid.add(tile);
        }

        // Action buttons: Clear & Confirm
        JButton clearBtn = makeButton("CLEAR", false, 10, 18);
        clearBtn.addActionListener(e -> {
            if (selectionLocked) {
                return;
            }
            selectedShapes.clear();
            shapeSpots.clear();
            refreshUI();
        });
        confirmButton = makeButton("CONFIRM", true, 10, 18);
        confirmButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                confirmSelection();
            }
        });
        JPanel buttonRow = panel();
        buttonRow.setLayout(new GridLayout(1, 2, 8, 0));
        buttonRow.add(clearBtn);
        buttonRow.add(confirmButton);

        JPanel objectsCard = stack(card());
        objectsCard.add(alignLeft(objectsHeader, 24));
        objectsCard.add(alignLeft(label("Drag your shapes to the canvas", FONT_SMALL, TEXT_MUTED)));
        objectsCard.add(strut(10));
        objectsCard.add(alignLeft(grid));
        objectsCard.add(strut(14));
        objectsCard.add(alignLeft(buttonRow, 46));

        // The whole card is a "drop here to remove" zone for shapes dragged off the canvas
        makeDropZone(objectsCard, () -> dragFromBoard, on -> {
            objectsCard.setBackground(on ? DANGER_BG : CARD_BG);
            objectsCard.setBorder(box(on ? DANGER_BORDER : CARD_BORDER, 1, 16, 16));
        }, (shapeName, where) -> {
            selectedShapes.remove(shapeName);
            shapeSpots.remove(shapeName);
            refreshUI();
        });

        // The right column holds the Objects card only. The glue at the end soaks up the extra
        // height BorderLayout gives this column, so the card keeps its natural size (no stretching).
        JPanel column = stack(panel());
        column.add(alignLeft(objectsCard, objectsCard.getPreferredSize().height));
        column.add(glue());
        return column;
    }

    // =====================================================================
    //  Rows
    // =====================================================================

    private static JPanel createScoreRow(String initial, Color avatarColor, String name, int score, boolean highlight) {
        JPanel who = panel();
        who.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        who.add(makeAvatar(initial, avatarColor, 24));
        who.add(label(name, FONT_BODY_BOLD, TEXT_DARK));

        JLabel scoreLabel = label(String.valueOf(score), FONT_BODY_BOLD, TEXT_DARK, JLabel.RIGHT);
        scoreLabel.setPreferredSize(new Dimension(24, 24));

        // Max height is locked so BoxLayout can't stretch name and score apart
        JPanel row = panel();
        row.setLayout(new BorderLayout());
        row = alignLeft(row, 36);
        row.setOpaque(highlight);
        row.setBackground(BLUE_LIGHT_BG);
        row.setBorder(highlight ? box(BLUE_BORDER, 1, 4, 8) : new EmptyBorder(4, 8, 4, 8));
        row.add(who, BorderLayout.WEST);
        row.add(scoreLabel, BorderLayout.EAST);
        return row;
    }

    /** Builds one "Choose / Confirm / Watch" step row and remembers its circle and text. */
    private StepRow createStepRow(int number, String text) {
        JPanel row = panel();
        row.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row = alignLeft(row, 28); // max height locked so the card can't pull rows apart
        JLabel circle = makeAvatar(String.valueOf(number), STEP_INACTIVE, 20);
        JLabel textLabel = label(text, FONT_BODY, TEXT_MUTED);
        row.add(circle);
        row.add(textLabel);
        StepRow step = new StepRow(row, circle, textLabel);
        stepRows.add(step);
        return step;
    }

    /**
     * Highlights one of the three round steps (1-3). Steps before it turn green with a check mark,
     * the active step is blue and bold, and steps after it stay grey. Pass 0 for "no round yet",
     * which leaves every step grey and inactive.
     */
    private void setStep(int activeStep) {
        for (int i = 0; i < stepRows.size(); i++) {
            StepRow step = stepRows.get(i);
            int number = i + 1;
            if (number < activeStep) {
                // done: a green circle with a check mark instead of the number
                step.circle.setText("\u2713");
                step.circle.setBackground(SUCCESS_TEXT);
                step.text.setFont(FONT_BODY);
                step.text.setForeground(TEXT_MUTED);
            } else if (number == activeStep) {
                // current: highlighted circle and bold text
                step.circle.setText(String.valueOf(number));
                step.circle.setBackground(PRIMARY_BLUE);
                step.text.setFont(FONT_BODY_BOLD);
                step.text.setForeground(TEXT_DARK);
            } else {
                // not reached yet: grey circle with the number
                step.circle.setText(String.valueOf(number));
                step.circle.setBackground(STEP_INACTIVE);
                step.text.setFont(FONT_BODY);
                step.text.setForeground(TEXT_MUTED);
            }
        }
        revalidate();
        repaint();
    }

    // =====================================================================
    //  State changes
    // =====================================================================

    /** Rebuilds the canvas and re-syncs every widget that depends on the selection. */
    private void refreshUI() {
        secretSetCanvas.removeAll();
        for (String shapeName : selectedShapes) {
            ShapeImage picture = new ShapeImage(image(shapeName, BIG_ICON), BIG_ICON, shapeName);
            picture.setName(shapeName); // the canvas finds the shape's spot through its name
            picture.setToolTipText("Drag to move " + shapeName + ", or back to Objects to remove it");
            makeDraggable(picture, true, shapeName);
            secretSetCanvas.add(picture);
        }
        secretSetCanvas.revalidate();
        secretSetCanvas.repaint();

        int count = selectedShapes.size();
        selectedCounterLabel.setText("Selected: " + count + " / " + requiredShapes);
        if (count == requiredShapes) {
            readyBadge.setText("READY TO CONFIRM");
            applyBadgeColors(readyBadge, SUCCESS_BG, SUCCESS_TEXT, SUCCESS_BORDER);
        } else {
            readyBadge.setText("PICK " + requiredShapes + (requiredShapes == 1 ? " SHAPE" : " SHAPES"));
            applyBadgeColors(readyBadge, WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }
        if (targetBadge != null) {
            targetBadge.setText(targetText());
        }

        // Card look lives in background/border, so each card must be refreshed (not just repainted)
        shapeCards.forEach(ShapeCard::refresh);

        // Keep the step card in sync with the selection. Before the first round no step is active;
        // while a round runs, filling every slot moves to step 2. A locked selection stays on
        // step 3 and is never pushed back.
        if (!roundActive) {
            setStep(0);
        } else if (selectionLocked) {
            setStep(3);
        } else if (selectedShapes.size() == requiredShapes) {
            setStep(2);
        } else {
            setStep(1);
        }
    }

    // =====================================================================
    //  Multiplayer hooks (called by MainApp from the network listener)
    // =====================================================================

    public void setMyName(String name) {
        this.myName = (name == null) ? "" : name;
    }

    /** Clears the canvas for a new round with the server's random shape count (1-5). */
    public void startRound(int roundNumber, int totalRounds, int requiredShapes) {
        if (requiredShapes >= GameServer.MIN_SHAPES && requiredShapes <= GameServer.MAX_SHAPES) {
            this.requiredShapes = requiredShapes;
        }
        roundActive = true;
        selectionLocked = false;
        selectedShapes.clear();
        shapeSpots.clear();
        if (roundLabel != null) {
            roundLabel.setText("Round " + roundNumber + " of " + totalRounds);
        }
        if (confirmButton != null) {
            confirmButton.setEnabled(true);
        }
        setStatus("Pick exactly " + this.requiredShapes + " shape"
                + (this.requiredShapes == 1 ? "" : "s") + " for the others to guess, then press CONFIRM.");
        showWaitingTime();
        refreshUI();
        setStep(1);   // every round starts on step 1
    }

    /** How many shapes this round needs; the server draws it and every player must match it. */
    public int getRequiredPicks() {
        return requiredShapes;
    }

    /** The shapes the questioner currently has on the canvas, in pick order. */
    public List<String> getSelectedShapes() {
        return new ArrayList<>(selectedShapes);
    }

    public boolean isSelectionLocked() {
        return selectionLocked;
    }

    /** Locks the choice after the server accepted it; the round continues while the others guess. */
    public void lockSelection() {
        selectionLocked = true;
        if (confirmButton != null) {
            confirmButton.setEnabled(false);
        }
        setStatus("Secret locked. Waiting for the other players to submit guesses...");
        setStep(3);
    }

    /** Updates the hint line under the canvas (also used as the round status line). */
    public void setStatus(String text) {
        if (hintLabel != null) {
            hintLabel.setText(text);
        }
    }

    /** Countdown fed by the server's TICK; the last ten seconds turn red. */
    public void setTimeLeft(int seconds) {
        if (timeBadge == null) {
            return;
        }
        timeBadge.setText(secondsText(seconds));
        if (seconds <= GameServer.BONUS_WINDOW_SECONDS) {
            applyTopBadgeColors(timeBadge, DANGER_BG, DANGER_TEXT, DANGER_BORDER);
        } else {
            applyTopBadgeColors(timeBadge, WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }
    }

    /** "Time: mm:ss", never below zero. */
    private static String secondsText(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("Time: %02d:%02d", safe / 60, safe % 60);
    }

    /** "Target: N Shapes" for the badge, using the count the server drew this round. */
    private String targetText() {
        return "Target: " + requiredShapes + (requiredShapes == 1 ? " Shape" : " Shapes");
    }

    /** Before the secret is locked the shared clock has not started yet. */
    private void showWaitingTime() {
        if (timeBadge != null) {
            timeBadge.setText(waitingTimeText());
            applyTopBadgeColors(timeBadge, WARNING_BG, WARNING_TEXT, WARNING_BORDER);
        }
    }

    private static String waitingTimeText() {
        return "Time: --:--";
    }

    /** Rebuilds the live round score list straight from the server's numbers. */
    public void setScoreboard(String[] names, int[] scores) {
        if (scoreRowsPanel == null) {
            return;
        }
        scoreRowsPanel.removeAll();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                scoreRowsPanel.add(strut(8));
            }
            int score = (i < scores.length) ? scores[i] : 0;
            boolean me = names[i].equals(myName);
            scoreRowsPanel.add(createScoreRow(initialOf(names[i]), avatarColor(i), names[i], score, me));
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
            JOptionPane.showMessageDialog(this,
                "Please select exactly " + requiredShapes + " shape"
                    + (requiredShapes == 1 ? "" : "s") + " before confirming.",
                "Not ready yet", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (onConfirmAction != null) {
            onConfirmAction.actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "confirm"));
        } else {
            lockSelection(); // preview mode with no network attached
        }
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

    // =====================================================================
    //  Drag and drop (java.awt.dnd)
    // =====================================================================

    /** Lets the user drag this panel; the drag carries shapeName. */
    private void makeDraggable(JPanel source, boolean fromBoard, String shapeName) {
        source.setCursor(new Cursor(Cursor.MOVE_CURSOR));
        DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(source, DnDConstants.ACTION_MOVE, e -> {
            if (selectionLocked) {
                return; // the secret is locked in; the canvas no longer accepts changes
            }
            dragFromBoard = fromBoard;
            dragShape = shapeName;
            DragGhost ghost = new DragGhost(e.getComponent(), shapeName);
            try {
                e.startDrag(null, shapePayload(shapeName), ghost);
            } catch (InvalidDnDOperationException ex) {
                ghost.finish();
            }
        });
    }

    /**
     * The drag data for one shape: plain text, with the same flavour and the same text the old
     * text-selection payload carried. Nothing on the drop side reads it (the drop uses dragShape),
     * but keeping it complete means an outside drop target still sees the shape name.
     */
    private static Transferable shapePayload(final String shapeName) {
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] { DataFlavor.stringFlavor };
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.stringFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!DataFlavor.stringFlavor.equals(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return shapeName;
            }
        };
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
            picture = new ShapeImage(image(shapeName, BIG_ICON), BIG_ICON, shapeName) {
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
                picture.setSize(BIG_ICON, BIG_ICON);
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
            picture.setLocation(p.x - BIG_ICON / 2, p.y - BIG_ICON / 2);
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
     * onDrop = what to do with the dropped shape and the drop point in the component's own coordinates (runs after
     * the drop event, because the canvas gets rebuilt). Children without their own drop target hand the drop to
     * their parent, so this covers everything inside.
     */
    private void makeDropZone(JPanel target, BooleanSupplier accepts, Consumer<Boolean> highlight, BiConsumer<String, Point> onDrop) {
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
                Point where = new Point(e.getLocation());
                e.acceptDrop(DnDConstants.ACTION_MOVE);
                e.dropComplete(true);
                SwingUtilities.invokeLater(() -> onDrop.accept(shapeName, where));
            }
        }, true);
    }

    // =====================================================================
    //  Pictures (AWT Toolkit)
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
            // Building the icon waits for the picture to finish loading; status 8 means it completed,
            // while a missing or broken file reports 4 (errored) or 2 (aborted) and is cached as null.
            ImageIcon loaded = new ImageIcon(Toolkit.getDefaultToolkit().createImage(SHAPE_DIR + file));
            Image scaled = null;
            if (loaded.getImageLoadStatus() == 8) {
                scaled = new ImageIcon(loaded.getImage().getScaledInstance(size, size, Image.SCALE_SMOOTH)).getImage();
            }
            images.put(key, scaled);
        }
        return images.get(key);
    }

    // =====================================================================
    //  Small component factories
    // =====================================================================

    private static CompoundBorder box(Color line, int thickness, int vPad, int hPad) {
        return new CompoundBorder(new LineBorder(line, thickness), new EmptyBorder(vPad, hPad, vPad, hPad));
    }

    /** Transparent panel; the caller sets the layout it needs straight afterwards. */
    private static JPanel panel() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    /** White card with a thin border and padding; the caller sets the layout straight afterwards. */
    private static JPanel card() {
        JPanel card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, 16, 16));
        return card;
    }

    /** Fixed-height transparent filler for BoxLayout, with the same sizes the old vertical strut had. */
    private static JPanel strut(int height) {
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        Dimension fixed = new Dimension(0, height);
        filler.setMinimumSize(fixed);
        filler.setPreferredSize(fixed);
        filler.setMaximumSize(new Dimension(Short.MAX_VALUE, height));
        return filler;
    }

    /** Transparent filler that soaks up the leftover BoxLayout space, like the old vertical glue. */
    private static JPanel glue() {
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        Dimension none = new Dimension(0, 0);
        filler.setMinimumSize(none);
        filler.setPreferredSize(none);
        filler.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        return filler;
    }

    /** Gives a panel a BoxLayout that stacks its children top to bottom (or left to right for X_AXIS). */
    private static JPanel stack(JPanel p, int axis) {
        p.setLayout(new BoxLayout(p, axis));
        return p;
    }

    private static JPanel stack(JPanel p) {
        return stack(p, BoxLayout.Y_AXIS);
    }

    /** Transparent panel that keeps c at its natural size at the given edge (NORTH or SOUTH). */
    private static JPanel pin(JPanel c, String edge) {
        JPanel p = panel();
        p.setLayout(new BorderLayout());
        p.add(c, edge);
        return p;
    }

    /** Wraps a label so it stays at its own height and sits in the vertical middle. */
    private static JPanel centerVertically(JLabel c) {
        JPanel wrapper = stack(panel());
        wrapper.add(glue());
        wrapper.add(c);
        wrapper.add(glue());
        return wrapper;
    }

    private static JPanel alignLeft(JPanel c) {
        c.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel alignLeft(JLabel c) {
        c.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return c;
    }

    /** Left aligned, and never taller than maxHeight inside a BoxLayout. */
    private static JPanel alignLeft(JPanel c, int maxHeight) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        return alignLeft(c);
    }

    private static JLabel alignLeft(JLabel c, int maxHeight) {
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        return alignLeft(c);
    }

    private static JLabel label(String text, Font font, Color color, int align) {
        JLabel label = new JLabel(text, align);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static JLabel label(String text, Font font, Color color) {
        return label(text, font, color, JLabel.LEADING);
    }

    private static JLabel makeBadge(String text, Color bg, Color fg, Color border, Font font) {
        JLabel badge = label(text, font, fg, JLabel.CENTER);
        badge.setOpaque(true);
        applyBadgeColors(badge, bg, fg, border);
        return badge;
    }

    /** Sub header badge (Target / Time): the small font and the tight padding GuesserScreen uses up top. */
    private static JLabel makeTopBadge(String text, Color bg, Color fg, Color border) {
        JLabel badge = label(text, FONT_SMALL_BOLD, fg, JLabel.CENTER);
        badge.setOpaque(true);
        applyTopBadgeColors(badge, bg, fg, border);
        return badge;
    }

    private static void applyBadgeColors(JLabel badge, Color bg, Color fg, Color border) {
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setBorder(box(border, 1, 6, 14));
    }

    /** Re-colors a sub header badge; padding stays at 4/10 so the badge never changes size. */
    private static void applyTopBadgeColors(JLabel badge, Color bg, Color fg, Color border) {
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setBorder(box(border, 1, 4, 10));
    }

    /** Square colored label with a letter or number, locked to size x size. */
    private static JLabel makeAvatar(String text, Color bg, int size) {
        JLabel avatar = label(text, new Font("Tahoma", Font.BOLD, size / 2), Color.WHITE, JLabel.CENTER);
        avatar.setOpaque(true);
        avatar.setBackground(bg);
        Dimension d = new Dimension(size, size);
        avatar.setPreferredSize(d);
        avatar.setMinimumSize(d);
        avatar.setMaximumSize(d);
        return avatar;
    }

    /** Plain JButton with a flat fill and thin border. The fill follows hover / press through mouse events. */
    private static JButton makeButton(String text, boolean primary, int padV, int padH) {
        Color normal = primary ? PRIMARY_BLUE : Color.WHITE;
        Color hover = primary ? PRIMARY_HOVER : BG_LIGHT;
        Color pressed = primary ? PRIMARY_PRESSED : SECONDARY_PRESSED;

        JButton button = new JButton(text);
        button.setFont(FONT_BODY_BOLD);
        button.setForeground(primary ? Color.WHITE : TEXT_DARK);
        button.setBackground(normal);
        button.setContentAreaFilled(false); // call first: some look and feels reset opaque when it changes
        button.setOpaque(true);
        button.setBorder(box(primary ? PRIMARY_BLUE : CARD_BORDER, 1, padV, padH));
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.addMouseListener(new ButtonHoverListener(button, normal, hover, pressed));
        return button;
    }

    // =====================================================================
    //  Inner classes
    // =====================================================================

    /**
     * Colors a button from mouse events: normal, hover (mouse inside) and pressed. A disabled button
     * is left alone, so one that is disabled while the pointer is still over it keeps the colour it
     * had (the old button-model listener repainted it normal). A press made from the keyboard is not
     * highlighted either, because it raises no mouse event at all.
     */
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

    /** Draws one picture centered in its area (or a placeholder box when missing). */
    private static class ShapeImage extends JPanel {
        private final Image image;
        private final int size;
        private final String label;

        ShapeImage(Image image, int size, String label) {
            this.image = image;
            this.size = size;
            this.label = label;
            setOpaque(false); // the panel paints nothing of its own, only the picture below
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
            // Placeholder when the picture file is missing
            g.setColor(new Color(220, 230, 242));
            g.fillRect(x, y, size, size);
            g.setColor(new Color(70, 90, 120));
            g.drawRect(x, y, size - 1, size - 1);
            g.setFont(new Font("Tahoma", Font.BOLD, Math.max(10, size / 6)));
            String text = label.length() > 6 ? label.substring(0, 6) : label;
            int textWidth = g.getFontMetrics().stringWidth(text);
            int textHeight = g.getFontMetrics().getHeight();
            int ascent = g.getFontMetrics().getAscent();
            g.drawString(text, x + (size - textWidth) / 2, y + (size - textHeight) / 2 + ascent);
        }
    }

    /** Light canvas with a dashed cross. Each shape is laid out on its own spot (looked up by the shape's name). */
    private class DashedCanvas extends JPanel {
        private boolean dropHighlight = false;

        DashedCanvas() {
            setOpaque(false);
            setLayout(null); // shapes are placed by doLayout() below
        }

        /** Puts each shape's center on its spot, nudged inward so it never leaves the canvas. */
        @Override
        public void doLayout() {
            for (int i = 0; i < getComponentCount(); i++) {
                Container shape = (Container) getComponent(i);
                double[] spot = shapeSpots.get(shape.getName());
                if (spot == null) {
                    continue;
                }
                Dimension d = shape.getPreferredSize();
                int x = (int) Math.round(spot[0] * getWidth() - d.width / 2.0);
                int y = (int) Math.round(spot[1] * getHeight() - d.height / 2.0);
                x = Math.max(0, Math.min(x, Math.max(0, getWidth() - d.width)));
                y = Math.max(0, Math.min(y, Math.max(0, getHeight() - d.height)));
                shape.setBounds(x, y, d.width, d.height);
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

            g.setColor(dropHighlight ? BLUE_LIGHT_BG : CANVAS_BG);
            g.fillRect(0, 0, w - 1, h - 1);
            g.setColor(dropHighlight ? PRIMARY_BLUE : CANVAS_LINE);
            g.drawRect(0, 0, w - 1, h - 1);
            g.setColor(CANVAS_LINE);

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

    /** One draggable card in the Objects grid. Its look follows the selection through refresh(). */
    private class ShapeCard extends JPanel {
        private final String name;
        private final JLabel nameLabel;
        private boolean hovered = false;

        ShapeCard(String name) {
            super(new BorderLayout());
            this.name = name;
            nameLabel = label(name, FONT_SMALL_BOLD, TEXT_DARK, JLabel.CENTER);
            add(new ShapeImage(image(name, SMALL_ICON), SMALL_ICON, name), BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
            makeDraggable(this, false, name);
            addMouseListener(new MouseAdapter() {
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
            setBackground(selected ? BLUE_LIGHT_BG : (hovered ? HOVER_BG : Color.WHITE));
            setBorder(box(selected ? PRIMARY_BLUE : (hovered ? BLUE_BORDER : CARD_BORDER), line, 7 - line, 7 - line));
            nameLabel.setForeground(selected ? PRIMARY_BLUE : TEXT_DARK);
            repaint();
        }
    }
}