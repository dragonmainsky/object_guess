import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceAdapter;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * ShapeImages: the single place that knows the shape names, loads their pictures from
 * <project>/shape and provides the shapes picker (grid cell + drag and drop) that the
 * questioner and guesser screens share.
 */
public final class ShapeImages {

    private static final String DIR = System.getProperty("user.dir") + "/shape/";
    private static final Color PLACEHOLDER_BG = new Color(220, 230, 242);
    private static final Color PLACEHOLDER_LINE = new Color(70, 90, 120);
    /** Every file of the shape folder, read once; extra pictures are looked up ignoring the case. */
    private static final String[] extraFiles = filesOf();

    /** The eight pictures the players can pick, in picker order (the names GameServer validates). */
    public static final String[] NAMES = GameServer.SHAPE_NAMES;

    private ShapeImages() {
    }

    private static String[] filesOf() {
        var files = new File(DIR).list();
        return (files == null) ? new String[0] : files;
    }

    // =====================================================================
    //  Pictures
    // =====================================================================

    /** Picture file of a shape name; a name without a picture of its own falls back to the first shape. */
    public static String fileNameFor(String name) {
        for (String shape : NAMES) {
            if (shape.equalsIgnoreCase(name)) {
                return fileOf(shape);
            }
        }
        for (String file : extraFiles) {
            if (file.equalsIgnoreCase(name + ".png")) {
                return file;   // extra pictures (trophy, logo) that are not playable shapes
            }
        }
        return fileOf(NAMES[0]);
    }

    /** diamond.png and star.png are the only two files not spelled exactly like their shape name. */
    private static String fileOf(String shape) {
        boolean lowerCase = shape.equalsIgnoreCase("Diamond") || shape.equalsIgnoreCase("Star");
        return (lowerCase ? shape.toLowerCase() : shape) + ".png";
    }

    /** One shape as a ready-to-use panel that paints its picture scaled to size x size. */
    public static JPanel shape(String shapeName, int size) {
        return new Picture(shapeName, size);
    }

    /** Draws one picture scaled to size x size at (x, y); the observer repaints it while it loads. */
    static void drawShape(Graphics g, JPanel observer, String shapeName, int x, int y, int size) {
        String path = DIR + fileNameFor(shapeName);
        if (new File(path).isFile()) {
            var picture = Toolkit.getDefaultToolkit().getImage(path);
            g.drawImage(picture, x, y, size, size, observer);
            return;
        }
        // The file is missing: the same grey box with the shape name stands in for it.
        g.setColor(PLACEHOLDER_BG);
        g.fillRect(x, y, size, size);
        g.setColor(PLACEHOLDER_LINE);
        g.drawRect(x, y, size - 1, size - 1);
        g.setFont(new Font("Tahoma", Font.BOLD, Math.max(10, size / 6)));
        String text = shapeName.length() > 6 ? shapeName.substring(0, 6) : shapeName;
        var metrics = g.getFontMetrics();
        g.drawString(text, x + (size - metrics.stringWidth(text)) / 2,
                y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    /** One picture kept centered in its own box, so a stretched parent cannot distort it. */
    private static class Picture extends JPanel {
        private final String shapeName;
        private final int size;

        Picture(String shapeName, int size) {
            this.shapeName = shapeName;
            this.size = size;
            setOpaque(false);
        }

        /** The picture box plus any border the caller adds around it, so a border never shrinks it. */
        @Override
        public Dimension getPreferredSize() {
            var insets = getInsets();
            return new Dimension(size + insets.left + insets.right, size + insets.top + insets.bottom);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            drawShape(g, this, shapeName, (getWidth() - size) / 2, (getHeight() - size) / 2, size);
        }
    }

    // =====================================================================
    //  Objects picker
    // =====================================================================

    /** One cell of an Objects picker: picture above the shape name, ready to be dragged. */
    public static class Picker extends JPanel {
        public final String shapeName;
        private final JLabel nameLabel;
        private final int padV;
        private final int padH;
        private final boolean hoverable;
        private boolean selected;
        private boolean hovered;

        /** padV and padH are the total border + padding, so the cell never changes size when selected. */
        public Picker(String shapeName, int icon, int padV, int padH, boolean hoverable) {
            super(new BorderLayout());
            this.shapeName = shapeName;
            this.padV = padV;
            this.padH = padH;
            this.hoverable = hoverable;
            nameLabel = AppDialog.label(shapeName, AppDialog.SMALL_BOLD, AppDialog.TEXT_DARK, JLabel.CENTER);
            add(shape(shapeName, icon), BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
            if (hoverable) {
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        hovered = true;
                        refresh();
                    }

                    @Override
                    public void mouseExited(MouseEvent event) {
                        hovered = false;
                        refresh();
                    }
                });
            }
            refresh();
        }

        /** Tells the cell whether its shape is picked right now. */
        public void setSelected(boolean isSelected) {
            selected = isSelected;
            refresh();
        }

        private void refresh() {
            int line = selected ? 2 : 1;
            Color edge = selected ? AppDialog.PRIMARY_BLUE
                    : (hovered ? AppDialog.BLUE_BORDER : AppDialog.CARD_BORDER);
            setBackground(selected ? AppDialog.BLUE_LIGHT_BG : (hovered ? AppDialog.ROW_BG : Color.WHITE));
            setBorder(AppDialog.box(edge, line, padV - line, padH - line));
            nameLabel.setForeground(selected ? AppDialog.PRIMARY_BLUE : AppDialog.TEXT_DARK);
            repaint();
        }
    }

    // =====================================================================
    //  Drag and drop
    // =====================================================================

    /**
     * Drag-and-drop helper for one screen: it remembers the shape being dragged, paints the picture
     * that follows the mouse and turns panels into drop zones. The host screen paints the picture from
     * its own paint(), so no extra window is ever created.
     */
    public static class DragBoard {
        private final JPanel host;
        private final int ghost;
        private String shape;        // shape being dragged right now, null when no drag is running
        private boolean fromBoard;   // the drag started on the board itself, not on the Objects picker
        private int ghostX;
        private int ghostY;
        private DragSourceMotionListener motion;

        public DragBoard(JPanel host, int ghostSize) {
            this.host = host;
            this.ghost = ghostSize;
        }

        public String shape() {
            return shape;
        }

        public boolean fromBoard() {
            return fromBoard;
        }

        /** Lets the user drag this panel; the drag carries the shape name. */
        public void draggable(JPanel source, String shapeName, boolean boardSource, BooleanSupplier locked) {
            source.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    source, DnDConstants.ACTION_MOVE, gesture -> {
                        if (locked.getAsBoolean()) {
                            return;   // the round forbids changes right now
                        }
                        shape = shapeName;
                        fromBoard = boardSource;
                        var origin = gesture.getDragOrigin();
                        var onScreen = source.getLocationOnScreen();
                        moveGhost(origin.x + onScreen.x, origin.y + onScreen.y);
                        motion = event -> moveGhost(event.getLocation().x, event.getLocation().y);
                        DragSource.getDefaultDragSource().addDragSourceMotionListener(motion);
                        try {
                            gesture.startDrag(null, payload(shapeName), new DragSourceAdapter() {
                                @Override
                                public void dragDropEnd(DragSourceDropEvent event) {
                                    endDrag();
                                }
                            });
                        } catch (InvalidDnDOperationException ignored) {
                            endDrag();
                        }
                    });
        }

        /**
         * Turns a panel into a drop zone: accepts = may the drag in progress land here, mark = light it
         * up or off, onDrop = what to do with the dropped shape and the point it was dropped on.
         */
        public void dropZone(JPanel target, BooleanSupplier accepts, Consumer<Boolean> mark,
                             BiConsumer<String, Point> onDrop) {
            new DropTarget(target, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                private boolean allowed() {
                    return shape != null && accepts.getAsBoolean();
                }

                @Override
                public void dragEnter(DropTargetDragEvent event) {
                    dragOver(event);
                    if (allowed()) {
                        mark.accept(true);
                    }
                }

                @Override
                public void dragOver(DropTargetDragEvent event) {
                    if (allowed()) {
                        event.acceptDrag(DnDConstants.ACTION_MOVE);
                    } else {
                        event.rejectDrag();
                    }
                }

                @Override
                public void dragExit(DropTargetEvent event) {
                    mark.accept(false);
                }

                @Override
                public void drop(DropTargetDropEvent event) {
                    mark.accept(false);
                    if (!allowed()) {
                        event.rejectDrop();
                        return;
                    }
                    String name = shape;
                    var where = event.getLocation();
                    event.acceptDrop(DnDConstants.ACTION_MOVE);
                    event.dropComplete(true);
                    onDrop.accept(name, where);
                }
            }, true);
        }

        /** Paints the picture that follows the mouse; the host screen calls this from its own paint(). */
        public void paintGhost(Graphics g) {
            if (shape != null) {
                drawShape(g, host, shape, ghostX, ghostY, ghost);
            }
        }

        private void moveGhost(int screenX, int screenY) {
            if (!host.isShowing()) {
                return;
            }
            var origin = host.getLocationOnScreen();
            host.repaint(ghostX, ghostY, ghost, ghost);   // erase the previous spot
            ghostX = screenX - origin.x - ghost / 2;
            ghostY = screenY - origin.y - ghost / 2;
            host.repaint(ghostX, ghostY, ghost, ghost);   // draw at the new spot
        }

        private void endDrag() {
            if (motion != null) {
                DragSource.getDefaultDragSource().removeDragSourceMotionListener(motion);
                motion = null;
            }
            shape = null;
            host.repaint(ghostX, ghostY, ghost, ghost);
        }

        /** The drag data for one shape: plain text, so an outside drop target also sees the name. */
        private static Transferable payload(final String shapeName) {
            return new Transferable() {
                @Override
                public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] {DataFlavor.stringFlavor};
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
    }
}
