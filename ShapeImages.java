import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.util.function.*;
import javax.swing.*;
/**
 * ShapeImages: คลาสจัดการรูปทรงเรขาคณิตทั้งหมดในเกม
 * ทำหน้าที่โหลดไฟล์รูปภาพจากโฟลเดอร์ shape ของโปรเจกต์ และให้บริการระบบเลือกรูปทรง (Picker)
 * รวมถึงระบบลากและวาง (Drag and Drop) ที่ใช้งานร่วมกันระหว่างหน้าจอผู้ตั้งโจทย์ (Questioner) และผู้ทาย (Guesser)
 */
public final class ShapeImages {

    // โฟลเดอร์ที่เก็บไฟล์รูปภาพรูปทรงเรขาคณิต
    private static final String DIR = System.getProperty("user.dir") + "/shape/";
    // สีพื้นหลังสำหรับกล่องสำรองเมื่อหาไฟล์รูปภาพไม่เจอ
    private static final Color PLACEHOLDER_BG = new Color(220, 230, 242);
    // สีกรอบสำหรับกล่องสำรองเมื่อหาไฟล์รูปภาพไม่เจอ
    private static final Color PLACEHOLDER_LINE = new Color(70, 90, 120);
    // รายชื่อไฟล์ทั้งหมดในโฟลเดอร์ shape อ่านขึ้นมาเก็บไว้รอบเดียว
    private static final String[] extraFiles = filesOf();

    // รายชื่อรูปทรง 8 แบบที่ผู้เล่นสามารถเลือกเล่นได้ เรียงตามลำดับเดียวกับที่ GameServer กำหนด
    public static final String[] NAMES = GameServer.SHAPE_NAMES;

    // คอนสตรักเตอร์แบบไพรเวต เพื่อป้องกันไม่ให้สร้างออบเจกต์จากคลาสนี้โดยตรง (เป็น Utility class)
    private ShapeImages() {
    }

    // ฟังก์ชันอ่านรายชื่อไฟล์ทั้งหมดจากโฟลเดอร์ shape
    private static String[] filesOf() {
        var files = new File(DIR).list();
        return (files == null) ? new String[0] : files;
    }

    // =====================================================================
    //  การจัดการและแสดงผลรูปภาพ (Pictures)
    // =====================================================================

    /**
     * ค้นหาชื่อไฟล์รูปภาพที่ตรงกับชื่อรูปทรง
     * หากไม่มีรูปภาพตรงกับชื่อ จะใช้รูปทรงแรกเป็นค่าเริ่มต้น (fallback)
     */
    public static String fileNameFor(String name) {
        // วนลูปตรวจสอบกับชื่อรูปทรงหลักของเกม
        for (String shape : NAMES) {
            if (shape.equalsIgnoreCase(name)) {
                return fileOf(shape);
            }
        }
        // วนลูปตรวจสอบกับไฟล์อื่นๆ ในโฟลเดอร์ เช่น trophy หรือ logo
        for (String file : extraFiles) {
            if (file.equalsIgnoreCase(name + ".png")) {
                return file;   // รูปภาพพิเศษ (เช่น ถ้วยรางวัล, โลโก้) ที่ไม่ใช่รูปทรงที่ใช้เล่น
            }
        }
        return fileOf(NAMES[0]);
    }

    /**
     * แปลงชื่อรูปทรงเป็นชื่อไฟล์ .png
     * ไฟล์ diamond.png และ star.png เป็นตัวพิมพ์เล็ก จึงต้องจัดการเป็นพิเศษ
     */
    private static String fileOf(String shape) {
        boolean lowerCase = shape.equalsIgnoreCase("Diamond") || shape.equalsIgnoreCase("Star");
        return (lowerCase ? shape.toLowerCase() : shape) + ".png";
    }

    /**
     * สร้าง JPanel ที่ใช้วาดรูปทรงตามขนาดที่ระบุ (size x size)
     */
    public static JPanel shape(String shapeName, int size) {
        return new Picture(shapeName, size);
    }

    /**
     * วาดรูปภาพรูปทรงลงบนกราฟิกที่ตำแหน่ง (x, y) ตามขนาด size x size
     * หากไม่พบไฟล์รูปภาพ จะวาดกล่องสี่เหลี่ยมสีเทาพร้อมชื่อรูปทรงทดแทน
     */
    static void drawShape(Graphics g, JPanel observer, String shapeName, int x, int y, int size) {
        String path = DIR + fileNameFor(shapeName);
        if (new File(path).isFile()) {
            // โหลดรูปภาพและวาดลงบน Canvas
            var picture = Toolkit.getDefaultToolkit().getImage(path);
            g.drawImage(picture, x, y, size, size, observer);
            return;
        }
        // หากไฟล์รูปภาพไม่มีอยู่: วาดกล่องสำรองสีเทาพร้อมตัวอักษรชื่อรูปทรง
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

    /**
     * คลาสคอมโพเนนต์สำหรับแสดงรูปภาพรูปทรงให้อยู่ตรงกลางของพื้นที่เสมอ เพื่อป้องกันภาพบิดเบี้ยว
     */
    private static class Picture extends JPanel {
        private final String shapeName; // ชื่อรูปทรง
        private final int size;         // ขนาดของรูปภาพ

        Picture(String shapeName, int size) {
            this.shapeName = shapeName;
            this.size = size;
            setOpaque(false); // โปร่งใส ไม่วาดพื้นหลังเริ่มต้น
        }

        /**
         * กำหนดขนาดที่ต้องการของคอมโพเนนต์ รวมขนาดรูปภาพและขอบ (Border) เพื่อไม่ให้รูปภาพถูกบีบ
         */
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

        /**
         * วาดรูปภาพให้อยู่ตรงกลางของคอมโพเนนต์เสมอ
         */
        @Override
        protected void paintComponent(Graphics g) {
            drawShape(g, this, shapeName, (getWidth() - size) / 2, (getHeight() - size) / 2, size);
        }
    }

    // =====================================================================
    //  คอมโพเนนต์สำหรับเลือกรูปทรง (Objects picker)
    // =====================================================================

    /**
     * แต่ละช่องในรายการรูปทรง (Objects picker): ประกอบด้วยรูปภาพและชื่อรูปทรง พร้อมรองรับการลาก (Drag)
     */
    public static class Picker extends JPanel {
        public final String shapeName;   // ชื่อรูปทรงประจำช่องนี้
        private final JLabel nameLabel;  // ป้ายแสดงชื่อรูปทรง
        private final int padV;          // ระยะขอบแนวตั้ง
        private final int padH;          // ระยะขอบแนวนอน
        private final boolean hoverable; // เปิดใช้เอฟเฟกต์เมื่อเมาส์ชี้หรือไม่
        private boolean selected;        // สถานะว่ากำลังถูกเลือกอยู่หรือไม่
        private boolean hovered;         // สถานะว่าเมาส์กำลังชี้อยู่หรือไม่

        /**
         * คอนสตรักเตอร์สำหรับสร้างช่องเลือกรูปทรง
         * padV และ padH คือระยะขอบรวม เพื่อไม่ให้ขนาดช่องเปลี่ยนเมื่อสถานะการเลือกเปลี่ยน
         */
        public Picker(String shapeName, int icon, int padV, int padH, boolean hoverable) {
            super(new BorderLayout());
            this.shapeName = shapeName;
            this.padV = padV;
            this.padH = padH;
            this.hoverable = hoverable;
            nameLabel = AppDialog.label(shapeName, AppDialog.SMALL_BOLD, AppDialog.TEXT_DARK, JLabel.CENTER);
            add(shape(shapeName, icon), BorderLayout.CENTER);
            add(nameLabel, BorderLayout.SOUTH);
            // ตรวจจับเหตุการณ์เมาส์ชี้เพื่อเปลี่ยนสีพื้นหลังและกรอบ
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

        /**
         * ตั้งค่าสถานะว่าช่องนี้ถูกเลือกอยู่หรือไม่ แล้วอัปเดตการแสดงผล
         */
        public void setSelected(boolean isSelected) {
            selected = isSelected;
            refresh();
        }

        /**
         * รีเฟรชสีพื้นหลัง เส้นขอบ และสีตัวอักษรตามสถานะถูกเลือก (selected) หรือเมาส์ชี้ (hovered)
         */
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
    //  ระบบลากและวาง (Drag and drop)
    // =====================================================================

    /**
     * คลาสช่วยจัดการระบบลากและวาง (Drag-and-Drop) สำหรับหน้าจอต่างๆ
     * ทำหน้าที่จำรูปทรงที่กำลังลาก วาดรูปโปร่งแสงตามตำแหน่งเมาส์ และกำหนดพื้นที่รับการวาง (Drop zone)
     */
    public static class DragBoard {
        private final JPanel host;  // หน้าจอหลักที่เป็นเจ้าของระบบลากวาง
        private final int ghost;    // ขนาดของรูปที่ลอยตามเมาส์
        private String shape;        // ชื่อรูปทรงที่กำลังถูกลากอยู่ขณะนี้ (null หากไม่มีการลาก)
        private boolean fromBoard;   // จริงหากการลากเริ่มจากบนกระดาน ไม่ใช่จากแถบเลือกรูปทรง
        private int ghostX;          // พิกัด X ของรูปที่ลอยตามเมาส์
        private int ghostY;          // พิกัด Y ของรูปที่ลอยตามเมาส์
        private DragSourceMotionListener motion; // ตัวตรวจจับการเคลื่อนที่ของเมาส์ขณะลาก

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

        /**
         * กำหนดให้คอมโพเนนต์ต้นทางสามารถลากได้ (draggable)
         */
        public void draggable(JPanel source, String shapeName, boolean boardSource, BooleanSupplier locked) {
            source.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            // สร้างตัวตรวจจับท่าทางการลาก (DragGestureRecognizer)
            DragSource.getDefaultDragSource().createDefaultDragGestureRecognizer(
                    source, DnDConstants.ACTION_MOVE, gesture -> {
                        // หากถูกล็อกอยู่ (เช่น หมดเวลาหรือยืนยันแล้ว) จะไม่ให้ลาก
                        if (locked.getAsBoolean()) {
                            return;
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
                                    endDrag(); // สิ้นสุดการลาก
                                }
                            });
                        } catch (InvalidDnDOperationException ignored) {
                            endDrag();
                        }
                    });
        }

        /**
         * กำหนดให้คอมโพเนนต์ปลายทางเป็นพื้นที่รับการวาง (drop zone)
         * accepts: ตรวจสอบว่าอนุญาตให้วางรูปทรงนี้หรือไม่
         * mark: ไฮไลต์พื้นที่เมื่อเมาส์ลากเข้ามา
         * onDrop: การทำงานเมื่อมีการปล่อยวางรูปทรงลงบนพื้นที่
         */
        public void dropZone(JPanel target, BooleanSupplier accepts, Consumer<Boolean> mark,
                             BiConsumer<String, Point> onDrop) {
            new DropTarget(target, DnDConstants.ACTION_MOVE, new DropTargetAdapter() {
                // ตรวจสอบเงื่อนไขว่าอนุญาตให้วางได้หรือไม่
                private boolean allowed() {
                    return shape != null && accepts.getAsBoolean();
                }

                @Override
                public void dragEnter(DropTargetDragEvent event) {
                    dragOver(event);
                    if (allowed()) {
                        mark.accept(true); // ไฮไลต์พื้นที่ตอบรับ
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
                    mark.accept(false); // ยกเลิกการไฮไลต์เมื่อเมาส์ออกนอกพื้นที่
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
                    onDrop.accept(name, where); // ดำเนินการเมื่อวางสำเร็จ
                }
            }, true);
        }

        /**
         * วาดรูปทรงที่ลอยตามเคอร์เซอร์เมาส์ขณะกำลังลาก
         */
        public void paintGhost(Graphics g) {
            if (shape != null) {
                drawShape(g, host, shape, ghostX, ghostY, ghost);
            }
        }

        /**
         * คำนวณพิกัดใหม่และ repaint ทั้งหน้าจอเพื่อล้าง ghost ตำแหน่งเดิม
         */
        private void moveGhost(int screenX, int screenY) {
            if (!host.isShowing()) {
                return;
            }
            var origin = host.getLocationOnScreen();
            ghostX = screenX - origin.x - ghost / 2;
            ghostY = screenY - origin.y - ghost / 2;
            // Repaint ทั้งหน้าจอเพื่อล้าง ghost ตำแหน่งเดิมให้หมดก่อนวาดตำแหน่งใหม่
            host.repaint();
        }

        /**
         * สิ้นสุดการลาก ล้างตัวตรวจจับตำแหน่งเมาส์และคืนค่าสถานะ
         */
        private void endDrag() {
            if (motion != null) {
                DragSource.getDefaultDragSource().removeDragSourceMotionListener(motion);
                motion = null;
            }
            shape = null;
            // ล้าง ghost ตำแหน่งสุดท้ายด้วยการ repaint ทั้งหน้าจอ
            host.repaint();
        }

        /**
         * เตรียมข้อมูล Payload ที่ส่งไปกับการลาก เป็นชื่อรูปทรงแบบข้อความธรรมดา
         */
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
