import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
/** คลาสจัดการรูปทรงและระบบลากวาง */
public final class ShapeImages {

    // โฟลเดอร์ที่เก็บไฟล์รูปภาพรูปทรงเรขาคณิต
    private static final String DIR = System.getProperty("user.dir") + "/shape/";
    // สีพื้นหลังของกล่องสำรอง
    private static final Color PLACEHOLDER_BG = new Color(220, 230, 242);
    // สีกรอบสำหรับกล่องสำรองเมื่อหาไฟล์รูปภาพไม่เจอ
    private static final Color PLACEHOLDER_LINE = new Color(70, 90, 120);
    // รายชื่อไฟล์ในโฟลเดอร์ shape
    private static final String[] extraFiles = filesOf();

    // ชื่อรูปทรงทั้ง 8 แบบ
    public static final String[] NAMES = GameServer.SHAPE_NAMES;

    // คอนสตรักเตอร์แบบไพรเวต
    private ShapeImages() {
    }

    // รายชื่อไฟล์ทั้งหมดในโฟลเดอร์ shape
    private static String[] filesOf() {
        String[] files = new File(DIR).list();
        if (files == null) {
            return new String[0];
        }
        return files;
    }

    // =====================================================================
    //  การจัดการและแสดงผลรูปภาพ (Pictures)
    // =====================================================================

    /** ไฟล์รูปภาพของรูปทรง (fallback = รูปแรก) */
    private static String fileNameFor(String name) {
        // วนลูปตรวจสอบกับชื่อรูปทรงหลักของเกม
        for (String shape : NAMES) {
            if (shape.equalsIgnoreCase(name)) {
                return fileOf(shape);
            }
        }
        // ไฟล์อื่นในโฟลเดอร์
        for (String file : extraFiles) {
            if (file.equalsIgnoreCase(name + ".png")) {
                return file;   // รูปภาพพิเศษ (เช่น ถ้วยรางวัล, โลโก้) ที่ไม่ใช่รูปทรงที่ใช้เล่น
            }
        }
        return fileOf(NAMES[0]);
    }

    /** ชื่อไฟล์ .png ของรูปทรง */
    private static String fileOf(String shape) {
        boolean lowerCase = shape.equalsIgnoreCase("Diamond") || shape.equalsIgnoreCase("Star");
        String baseName;
        if (lowerCase) {
            baseName = shape.toLowerCase();
        } else {
            baseName = shape;
        }
        return baseName + ".png";
    }

    /** สร้าง JPanel แสดงรูปทรงตามขนาด */
    public static JPanel shape(String shapeName, int size) {
        return new Picture(shapeName, size);
    }

    /** วาดรูปทรงหรือกล่องสำรอง */
    static void drawShape(Graphics g, JPanel observer, String shapeName, int x, int y, int size) {
        String path = DIR + fileNameFor(shapeName);
        if (new File(path).isFile()) {
            // วาดรูปภาพ
            Image picture = Toolkit.getDefaultToolkit().getImage(path);
            g.drawImage(picture, x, y, size, size, observer);
            return;
        }
        // กล่องสำรองเมื่อไม่พบไฟล์
        g.setColor(PLACEHOLDER_BG);
        g.fillRect(x, y, size, size);
        g.setColor(PLACEHOLDER_LINE);
        g.drawRect(x, y, size - 1, size - 1);
        g.setFont(new Font("Tahoma", Font.BOLD, Math.max(10, size / 6)));
        String text;
        if (shapeName.length() > 6) {
            text = shapeName.substring(0, 6);
        } else {
            text = shapeName;
        }
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x + (size - metrics.stringWidth(text)) / 2,
                y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
    }

    /** คอมโพเนนต์แสดงรูปทรงแบบคงสัดส่วน */
    private static class Picture extends JPanel {
        private final String shapeName; // ชื่อรูปทรง
        private final int size;         // ขนาดของรูปภาพ

        // ชื่อรูปทรงและขนาดภาพ
        Picture(String shapeName, int size) {
            this.shapeName = shapeName;
            this.size = size;
            setOpaque(false); // โปร่งใส ไม่วาดพื้นหลังเริ่มต้น
        }

        /** ขนาดคอมโพเนนต์ตามภาพและขอบ */
        @Override
        public Dimension getPreferredSize() {
            Insets insets = getInsets();
            return new Dimension(size + insets.left + insets.right, size + insets.top + insets.bottom);
        }

        // ขนาดภาพตาม preferred size
        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        // ขนาดภาพตาม preferred size
        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        /** วาดภาพให้อยู่กลางคอมโพเนนต์ */
        @Override
        protected void paintComponent(Graphics g) {
            drawShape(g, this, shapeName, (getWidth() - size) / 2, (getHeight() - size) / 2, size);
        }
    }

    // =====================================================================
    //  คอมโพเนนต์สำหรับเลือกรูปทรง (Objects picker)
    // =====================================================================

    /** ช่องเลือกรูปทรงหนึ่งช่อง */
    public static class Picker extends JPanel {
        public final String shapeName;   // ชื่อรูปทรงประจำช่องนี้
        private final JLabel nameLabel;  // ป้ายแสดงชื่อรูปทรง
        private final int padV;          // ระยะขอบแนวตั้ง
        private final int padH;          // ระยะขอบแนวนอน
        private final boolean hoverable; // เปิดใช้เอฟเฟกต์เมื่อเมาส์ชี้หรือไม่
        private boolean selected;        // สถานะว่ากำลังถูกเลือกอยู่หรือไม่
        private boolean hovered;         // สถานะว่าเมาส์กำลังชี้อยู่หรือไม่

        /** สร้างช่องเลือกรูปทรง */
        public Picker(String shapeName, int icon, int padV, int padH, boolean hoverable) {
            super(new BorderLayout());
            this.shapeName = shapeName;
            this.padV = padV;
            this.padH = padH;
            this.hoverable = hoverable;
            nameLabel = AppDialog.label(shapeName, AppDialog.SMALL_BOLD, AppDialog.TEXT_DARK, JLabel.CENTER);
            add(shape(shapeName, icon), BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
            // เหตุการณ์เมาส์ของช่อง
            if (hoverable) {
                addMouseListener(new MouseAdapter() {
                    // เมาส์เข้าช่อง
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        hovered = true;
                        refresh();
                    }

                    // เมาส์ออกจากช่อง
                    @Override
                    public void mouseExited(MouseEvent event) {
                        hovered = false;
                        refresh();
                    }
                });
            }
            refresh();
        }

        /** กำหนดสถานะเลือก */
        public void setSelected(boolean isSelected) {
            selected = isSelected;
            refresh();
        }

        /** อัปเดตสีตามสถานะ selected/hovered */
        private void refresh() {
            int line;
            if (selected) {
                line = 2;
            } else {
                line = 1;
            }
            Color edge;
            if (selected) {
                edge = AppDialog.PRIMARY_BLUE;
            } else if (hovered) {
                edge = AppDialog.BLUE_BORDER;
            } else {
                edge = AppDialog.CARD_BORDER;
            }
            Color background;
            if (selected) {
                background = AppDialog.BLUE_LIGHT_BG;
            } else if (hovered) {
                background = AppDialog.ROW_BG;
            } else {
                background = Color.WHITE;
            }
            setBackground(background);
            setBorder(AppDialog.box(edge, line, padV - line, padH - line));
            Color nameColor;
            if (selected) {
                nameColor = AppDialog.PRIMARY_BLUE;
            } else {
                nameColor = AppDialog.TEXT_DARK;
            }
            nameLabel.setForeground(nameColor);
            repaint();
        }
    }

    // =====================================================================
    //  ระบบลากและวาง (Drag and drop)
    // =====================================================================

    /** ระบบลากวางของรูปทรง */
    public static class DragBoard {
        private final JPanel host;  // หน้าจอหลักที่เป็นเจ้าของระบบลากวาง
        private final int ghost;    // ขนาดของรูปที่ลอยตามเมาส์
        private String shape;        // ชื่อรูปทรงที่กำลังถูกลากอยู่ขณะนี้ (null หากไม่มีการลาก)
        private boolean fromBoard;   // จริงหากการลากเริ่มจากบนกระดาน ไม่ใช่จากแถบเลือกรูปทรง
        private int ghostX;          // พิกัด X ของรูปที่ลอยตามเมาส์
        private int ghostY;          // พิกัด Y ของรูปที่ลอยตามเมาส์
        private DragSourceMotionListener motion; // ตัวตรวจจับการเคลื่อนที่ของเมาส์ขณะลาก

        // -----------------------------------------------------------------
        // Callback ของระบบลากวาง
        // -----------------------------------------------------------------

        /** สถานะการล็อกอินพุต */
        public interface DragLock {
            boolean isLocked();
        }

        /** เงื่อนไขการรับรูปทรง */
        public interface DropCheck {
            boolean accepts();
        }

        /** ไฮไลต์พื้นที่ปลายทาง */
        public interface DropMark {
            void mark(boolean on);
        }

        /** เหตุการณ์เมื่อวางรูปทรง */
        public interface DropHandler {
            void dropped(String shapeName, Point where);
        }

        /** สร้าง DragBoard */
        public DragBoard(JPanel host, int ghostSize) {
            this.host = host;
            this.ghost = ghostSize;
        }

        // ดึงชื่อรูปทรงที่กำลังถูกลาก
        public String shape() {
            return shape;
        }

        // ตรวจสอบว่ารูปทรงถูกลากมาจากบนกระดานหรือไม่
        public boolean fromBoard() {
            return fromBoard;
        }

        /** กำหนดคอมโพเนนต์ที่ลากได้ */
        public void draggable(JPanel source, String shapeName, boolean boardSource, DragLock locked) {
            source.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            // ตัวตรวจจับท่าทางการลาก
            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    source, DnDConstants.ACTION_MOVE, gesture -> {
                        // สถานะล็อกอินพุต
                        if (locked.isLocked()) {
                            return;
                        }
                        shape = shapeName;
                        fromBoard = boardSource;
                        Point origin = gesture.getDragOrigin();
                        Point onScreen = source.getLocationOnScreen();
                        moveGhost(origin.x + onScreen.x, origin.y + onScreen.y);
                        motion = event -> moveGhost(event.getLocation().x, event.getLocation().y);
                        DragSource.getDefaultDragSource().addDragSourceMotionListener(motion);
                        try {
                            // Payload ของการลากเป็น StringSelection
                            gesture.startDrag(null, new StringSelection(shapeName), new DragSourceAdapter() {
                                /** จบการลาก */
                                @Override
                                public void dragDropEnd(DragSourceDropEvent event) {
                                    endDrag(); // สิ้นสุดการลาก
                                }
                            });
                        } catch (InvalidDnDOperationException ignored) {
                            endDrag();
                        }
                    });
        }

        /** กำหนดพื้นที่รับการวาง */
        public void dropZone(JPanel target, DropCheck accepts, DropMark mark, DropHandler onDrop) {
            new DropTarget(target, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                // เงื่อนไขการวาง
                private boolean allowed() {
                    return shape != null && accepts.accepts();
                }

                /** ตรวจสอบการรับรูปทรง */
                @Override
                public void dragEnter(DropTargetDragEvent event) {
                    dragOver(event);
                    if (allowed()) {
                        mark.mark(true); // ไฮไลต์พื้นที่ตอบรับ
                    }
                }

                /** ตอบรับการลาก */
                @Override
                public void dragOver(DropTargetDragEvent event) {
                    if (allowed()) {
                        event.acceptDrag(DnDConstants.ACTION_MOVE);
                    } else {
                        event.rejectDrag();
                    }
                }

                /** เมาส์ออกจากพื้นที่ */
                @Override
                public void dragExit(DropTargetEvent event) {
                    mark.mark(false); // ยกเลิกการไฮไลต์เมื่อเมาส์ออกนอกพื้นที่
                }

                /** จัดการรูปทรงที่ถูกวาง */
                @Override
                public void drop(DropTargetDropEvent event) {
                    mark.mark(false);
                    if (!allowed()) {
                        event.rejectDrop();
                        return;
                    }
                    String name = shape;
                    Point where = event.getLocation();
                    event.acceptDrop(DnDConstants.ACTION_MOVE);
                    event.dropComplete(true);
                    onDrop.dropped(name, where); // ดำเนินการเมื่อวางสำเร็จ
                }
            }, true);
        }

        /** วาดรูปทรงที่ลอยตามเมาส์ */
        public void paintGhost(Graphics g) {
            if (shape != null) {
                drawShape(g, host, shape, ghostX, ghostY, ghost);
            }
        }

        /** อัปเดตตำแหน่ง ghost */
        private void moveGhost(int screenX, int screenY) {
            if (!host.isShowing()) {
                return;
            }
            Point origin = host.getLocationOnScreen();
            ghostX = screenX - origin.x - ghost / 2;
            ghostY = screenY - origin.y - ghost / 2;
            // ล้าง ghost ตำแหน่งเดิม
            host.repaint();
        }

        /** จบการลาก */
        private void endDrag() {
            if (motion != null) {
                DragSource.getDefaultDragSource().removeDragSourceMotionListener(motion);
                motion = null;
            }
            shape = null;
            // ล้าง ghost ตำแหน่งสุดท้าย
            host.repaint();
        }
    }
}
