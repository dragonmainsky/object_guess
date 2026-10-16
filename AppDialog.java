import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/**
 * AppDialog: the shared look of the whole game (palette, fonts, small component factories) and the
 * alert / confirmation dialogs every screen shows, so all of them stay visually identical.
 */
public final class AppDialog {

    // ===== Palette =====
    static final Color BG_LIGHT = new Color(241, 245, 249), CARD_BG = Color.WHITE;
    static final Color CARD_BORDER = new Color(226, 232, 240), ROW_BG = new Color(248, 250, 252);
    static final Color SLOT_BORDER = new Color(203, 213, 225), PLACEHOLDER_BG = new Color(220, 230, 242);

    static final Color PRIMARY_BLUE = new Color(37, 99, 235), PRIMARY_HOVER = new Color(29, 78, 216);
    static final Color PRIMARY_PRESS = new Color(30, 64, 175);
    static final Color BLUE_LIGHT_BG = new Color(239, 246, 255), BLUE_BORDER = new Color(191, 219, 254);

    static final Color SUCCESS_BG = new Color(220, 252, 231), SUCCESS_TEXT = new Color(22, 101, 52);
    static final Color SUCCESS_BORDER = new Color(187, 247, 208);
    static final Color WARNING_BG = new Color(254, 249, 195), WARNING_TEXT = new Color(133, 77, 14);
    static final Color WARNING_BORDER = new Color(254, 240, 138);
    static final Color DANGER_BG = new Color(254, 226, 226), DANGER_TEXT = new Color(153, 27, 27);
    static final Color DANGER_BORDER = new Color(254, 202, 202);
    static final Color GOLD_BG = new Color(254, 243, 199), GOLD_TEXT = new Color(180, 83, 9);
    static final Color GOLD_BORDER = new Color(253, 230, 138);

    static final Color TEXT_DARK = new Color(15, 23, 42), TEXT_MUTED = new Color(100, 116, 139);
    static final Color TEXT_LIGHT = new Color(148, 163, 184);

    /** Avatar colours, cycled by the player's order in the room. */
    private static final Color[] AVATARS = {
        new Color(37, 99, 235), new Color(139, 92, 246), new Color(236, 72, 153), new Color(16, 185, 129),
        new Color(245, 158, 11), new Color(14, 165, 233), new Color(244, 63, 94), new Color(99, 102, 241)
    };

    /** Dialog colours and badge sizes that only the dialogs use. */
    private static final Color ERROR_ACCENT = new Color(185, 28, 28);
    private static final Font DIALOG_TITLE = new Font("Tahoma", Font.BOLD, 17);
    private static final int BADGE_SIZE = 44, BODY_WIDTH = 380, CARD_ARC = 16, ACCENT_HEIGHT = 6;
    private static final int TYPE_INFO = 0, TYPE_WARNING = 1, TYPE_ERROR = 2, TYPE_CONFIRM = 3;

    // ===== Fonts =====
    static Font f(int style, int size) {
        return new Font("Tahoma", style, size);
    }

    static final Font TITLE_LARGE = f(Font.BOLD, 26), TITLE_24 = f(Font.BOLD, 24), TITLE = f(Font.BOLD, 18);
    static final Font STRONG = f(Font.BOLD, 16), SUBTITLE = f(Font.BOLD, 14), BODY_BOLD = f(Font.BOLD, 13);
    static final Font SMALL_BOLD_12 = f(Font.BOLD, 12), SMALL_BOLD = f(Font.BOLD, 11), KEYCAP = f(Font.BOLD, 9);
    static final Font SCREEN_SUB = f(Font.PLAIN, 15), PLAIN_14 = f(Font.PLAIN, 14), BODY = f(Font.PLAIN, 13);
    static final Font SMALL_12 = f(Font.PLAIN, 12), SMALL = f(Font.PLAIN, 11);

    private AppDialog() {
    }

    // =====================================================================
    //  Alerts and confirmation (drop-in replacement for the old pop-ups)
    // =====================================================================

    /** Blue information alert with a single OK button. */
    public static void showInfo(Container parent, String title, String message) {
        show(TYPE_INFO, parent, title, message, null);
    }

    /** Amber warning alert with a single OK button. */
    public static void showWarning(Container parent, String title, String message) {
        show(TYPE_WARNING, parent, title, message, null);
    }

    /** Red error alert with a single OK button. */
    public static void showError(Container parent, String title, String message) {
        show(TYPE_ERROR, parent, title, message, null);
    }

    /** Blue Yes/No confirmation; true only when the primary (Yes) button is chosen. */
    public static boolean showConfirm(Container parent, String title, String message, String yesText, String noText) {
        return show(TYPE_CONFIRM, parent, title, message, new String[] {yesText, noText});
    }

    /** Builds one modal card, shows it and blocks until the player answers. */
    private static boolean show(int type, Container parent, String title, String message, String[] confirmText) {
        var answer = new boolean[1];
        var dialog = new JDialog(ownerOf(parent), text(title), JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        var card = buildCard(type, text(title), text(message));
        var yes = new RoundedButton(text(confirmText == null ? "OK" : confirmText[0]), PRIMARY_BLUE, PRIMARY_HOVER,
                PRIMARY_PRESS, Color.WHITE, PRIMARY_BLUE);
        yes.addActionListener(event -> {
            answer[0] = true;
            dialog.dispose();
        });

        var row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        if (confirmText != null) {
            var no = new RoundedButton(text(confirmText[1]), CARD_BG, BG_LIGHT, CARD_BORDER, TEXT_DARK, CARD_BORDER);
            no.addActionListener(event -> {
                answer[0] = false;
                dialog.dispose();
            });
            row.add(no);
        }
        row.add(yes);
        card.add(row, BorderLayout.SOUTH);

        var root = new JPanel(new BorderLayout());
        root.setBackground(BG_LIGHT);
        root.add(card, BorderLayout.CENTER);
        dialog.setContentPane(root);

        // ENTER answers yes and ESC answers no, whatever holds the focus inside the dialog.
        var manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher keys = event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                answer[0] = false;
                dialog.dispose();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                answer[0] = true;
                dialog.dispose();
                return true;
            }
            return false;
        };
        manager.addKeyEventDispatcher(keys);
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                yes.requestFocusInWindow();   // focus lands on the primary button
            }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);   // modal: blocks here until the player answers
        manager.removeKeyEventDispatcher(keys);
        return answer[0];
    }

    /** Nearest window above the component that opened the dialog, or null for an unowned dialog. */
    private static Window ownerOf(Container parent) {
        for (Container c = parent; c != null; c = c.getParent()) {
            if (c instanceof Window) {
                return (Window) c;
            }
        }
        return null;
    }

    // =====================================================================
    //  Card content
    // =====================================================================

    private static RoundedCard buildCard(int type, String title, String message) {
        var card = new RoundedCard(accentOf(type), borderOf(type));
        card.setLayout(new BorderLayout(0, 16));
        // The extra top padding leaves room for the accent bar along the top edge.
        card.setBorder(new EmptyBorder(ACCENT_HEIGHT + 18, 24, 20, 24));

        var header = panel();
        header.setLayout(new BorderLayout(14, 0));
        header.add(new CircleBadge(letterOf(type), accentOf(type)), BorderLayout.WEST);
        header.add(label(title, DIALOG_TITLE, TEXT_DARK), BorderLayout.CENTER);
        card.add(header, BorderLayout.NORTH);

        // The body is indented past the badge column so it lines up with the title.
        var bodyHolder = panel();
        bodyHolder.setLayout(new BorderLayout());
        bodyHolder.setBorder(new EmptyBorder(0, BADGE_SIZE + 14, 0, 0));
        bodyHolder.add(wrappedBody(message), BorderLayout.CENTER);
        card.add(bodyHolder, BorderLayout.CENTER);
        return card;
    }

    /** Non-editable text area that wraps long messages instead of stretching the dialog. */
    private static JTextArea wrappedBody(String message) {
        var body = new JTextArea(message);
        body.setFont(PLAIN_14);
        body.setForeground(TEXT_MUTED);
        body.setOpaque(false);
        body.setEditable(false);
        body.setFocusable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setBorder(null);
        // Measure at the maximum width first, then keep the height that wrapping produced.
        body.setSize(new Dimension(BODY_WIDTH, Short.MAX_VALUE));
        var wrapped = body.getPreferredSize();
        body.setPreferredSize(wrapped);
        body.setMinimumSize(wrapped);
        return body;
    }

    private static Color accentOf(int type) {
        return type == TYPE_WARNING ? WARNING_TEXT : (type == TYPE_ERROR ? ERROR_ACCENT : PRIMARY_BLUE);
    }

    private static Color borderOf(int type) {
        return type == TYPE_WARNING ? WARNING_BORDER : (type == TYPE_ERROR ? DANGER_BORDER : BLUE_BORDER);
    }

    private static String letterOf(int type) {
        return type == TYPE_WARNING ? "!" : (type == TYPE_ERROR ? "x" : (type == TYPE_CONFIRM ? "?" : "i"));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    // =====================================================================
    //  Small factories shared by the screens
    // =====================================================================

    /** Transparent panel; the caller sets the layout straight afterwards. */
    static JPanel panel() {
        var p = new JPanel();
        p.setOpaque(false);
        return p;
    }

    /** White card with a thin border and 16px padding; the caller sets the layout afterwards. */
    static JPanel card() {
        var card = new JPanel();
        card.setBackground(CARD_BG);
        card.setBorder(box(CARD_BORDER, 1, 16, 16));
        return card;
    }

    /** A 1px line border plus inner padding, in one call. */
    static CompoundBorder box(Color line, int thickness, int padV, int padH) {
        return new CompoundBorder(new LineBorder(line, thickness), new EmptyBorder(padV, padH, padV, padH));
    }

    /** Fixed-height transparent filler for a vertical BoxLayout stack. */
    static JPanel strut(int height) {
        return filler(0, height, height);
    }

    /** Transparent filler that soaks up the leftover BoxLayout space. */
    static JPanel glue() {
        return filler(0, 0, Integer.MAX_VALUE);
    }

    private static JPanel filler(int width, int height, int maxHeight) {
        var filler = panel();
        var fixed = new Dimension(width, height);
        filler.setMinimumSize(fixed);
        filler.setPreferredSize(fixed);
        filler.setMaximumSize(new Dimension(Short.MAX_VALUE, maxHeight));
        return filler;
    }

    /** Gives a panel a BoxLayout that stacks its children top to bottom. */
    static JPanel stack(JPanel panel) {
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    /** Transparent panel that keeps c at its natural size at the given edge (NORTH or SOUTH). */
    static JPanel pin(Container c, String edge) {
        var holder = panel();
        holder.setLayout(new BorderLayout());
        holder.add(c, edge);
        return holder;
    }

    static JLabel label(String text, Font font, Color color) {
        return label(text, font, color, JLabel.LEADING);
    }

    static JLabel label(String text, Font font, Color color, int align) {
        var label = new JLabel(text, align);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    /** Small label with a fill and a border; line may be null for "no border". */
    static JLabel badge(String text, Color bg, Color fg, Color line, int padV, int padH) {
        return paintBadge(new JLabel(text, JLabel.CENTER), text, bg, fg, line, padV, padH);
    }

    /** Re-colours an existing badge in place; the padding stays, so the badge keeps its size. */
    static JLabel paintBadge(JLabel badge, String text, Color bg, Color fg, Color line, int padV, int padH) {
        badge.setText(text);
        badge.setFont(SMALL_BOLD);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(line == null ? new EmptyBorder(padV + 1, padH + 1, padV + 1, padH + 1)
                : box(line, 1, padV, padH));
        badge.repaint();
        return badge;
    }

    /** Header badge of the four game screens; strong = the bigger countdown badge. */
    static JLabel topBadge(String text, Color bg, Color fg, Color line, boolean strong) {
        return paintTopBadge(new JLabel(text, JLabel.CENTER), text, bg, fg, line, strong);
    }

    /** Re-colours a header badge in place; the padding stays at its own size, so it never shrinks. */
    static JLabel paintTopBadge(JLabel badge, String text, Color bg, Color fg, Color line, boolean strong) {
        badge.setText(text);
        badge.setFont(strong ? STRONG : SUBTITLE);
        badge.setForeground(fg);
        badge.setBackground(bg);
        badge.setOpaque(true);
        badge.setBorder(box(line, 1, strong ? 10 : 8, strong ? 20 : 16));
        badge.repaint();
        return badge;
    }

    /** Square avatar tile with a letter or number, locked to size x size. */
    static JLabel avatar(String text, Color bg, int size) {
        var tile = label(text, f(Font.BOLD, size / 2), Color.WHITE, JLabel.CENTER);
        tile.setOpaque(true);
        tile.setBackground(bg);
        var fixed = new Dimension(size, size);
        tile.setPreferredSize(fixed);
        tile.setMinimumSize(fixed);
        tile.setMaximumSize(fixed);
        return tile;
    }

    /** Small keycap-looking label (for the ENTER hint). */
    static JLabel keyCap(String text) {
        var key = label(text, KEYCAP, new Color(51, 65, 85), JLabel.CENTER);
        key.setOpaque(true);
        key.setBackground(Color.WHITE);
        key.setBorder(box(SLOT_BORDER, 1, 3, 6));
        return key;
    }

    /** Two-line card header: title, optional muted sub line, optional widget pinned right of the title. */
    static JPanel header(String title, Font titleFont, String sub, Font subFont, JLabel right) {
        var titleRow = panel();
        titleRow.setLayout(new BorderLayout());
        titleRow.add(label(title, titleFont, TEXT_DARK), BorderLayout.WEST);
        if (right != null) {
            var holder = panel();
            holder.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            holder.add(right);
            titleRow.add(holder, BorderLayout.EAST);
        }
        var header = panel();
        header.setLayout(new BorderLayout(0, 2));
        header.add(titleRow, BorderLayout.NORTH);
        if (sub != null) {
            header.add(label(sub, subFont, TEXT_MUTED), BorderLayout.CENTER);
        }
        return header;
    }

    /** Flat button whose fill follows hover and press; line == null means "no border at all". */
    static JButton button(String text, Color bg, Color hover, Color pressed, Color fg, Color line, int padV, int padH) {
        var button = new JButton(text);
        button.setFont(BODY_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setContentAreaFilled(false);   // call this first: some look and feels reset opaque when it changes
        button.setOpaque(true);
        button.setBorderPainted(line != null);
        button.setBorder(line == null ? new EmptyBorder(padV, padH, padV, padH) : box(line, 1, padV, padH));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new ButtonHover(button, bg, hover, pressed));
        return button;
    }

    /** First letter of a player name, shown on the avatars. */
    static String initialOf(String name) {
        return (name == null || name.isEmpty()) ? "?" : name.substring(0, 1).toUpperCase();
    }

    /** Avatar colour of the player at this position in the room. */
    static Color avatarColor(int index) {
        return AVATARS[Math.abs(index) % AVATARS.length];
    }

    /** "Time: mm:ss" for the shared countdown badge, never below zero. */
    static String timeText(int seconds) {
        int safe = Math.max(0, seconds);
        return String.format("Time: %02d:%02d", safe / 60, safe % 60);
    }

    /** "Target: N Shapes" for the header badge of both game screens. */
    static String targetText(int shapes) {
        if (shapes <= 0) {
            return "Target: ?";
        }
        return "Target: " + shapes + (shapes == 1 ? " Shape" : " Shapes");
    }

    /** Colours a button from mouse events: hover (mouse inside) and pressed. Disabled buttons are left alone. */
    static class ButtonHover extends MouseAdapter {
        private final JButton button;
        private final Color normal;
        private final Color hover;
        private final Color pressed;
        private boolean inside;

        ButtonHover(JButton button, Color normal, Color hover, Color pressed) {
            this.button = button;
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
        }

        @Override
        public void mouseEntered(MouseEvent event) {
            inside = true;
            apply(hover);
        }

        @Override
        public void mouseExited(MouseEvent event) {
            inside = false;
            apply(normal);
        }

        @Override
        public void mousePressed(MouseEvent event) {
            apply(pressed);
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            apply(inside ? hover : normal);
        }

        private void apply(Color color) {
            if (color != null && button.isEnabled()) {
                button.setBackground(color);
            }
        }
    }

    // =====================================================================
    //  Custom painted parts (rounded card, circle badge, rounded button)
    // =====================================================================

    /** White card with rounded corners, a thin border and a coloured accent bar along the top. */
    private static class RoundedCard extends JPanel {
        private final Color accent;
        private final Color edge;

        RoundedCard(Color accent, Color edge) {
            this.accent = accent;
            this.edge = edge;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            int w = getWidth();
            int h = getHeight();
            g.setColor(CARD_BG);
            g.fillRoundRect(0, 0, w - 1, h - 1, CARD_ARC, CARD_ARC);

            // The accent bar is clipped to the rounded card so it follows the top corners.
            var clip = g.getClip();
            g.setClip(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, CARD_ARC, CARD_ARC));
            g.setColor(accent);
            g.fillRect(0, 0, w, ACCENT_HEIGHT);
            g.setClip(clip);

            g.setColor(edge);
            g.drawRoundRect(1, 1, w - 3, h - 3, CARD_ARC, CARD_ARC);
        }
    }

    /** Round coloured badge with a plain white letter (no picture files, no emoji). */
    private static class CircleBadge extends JLabel {
        private final Color fill;

        CircleBadge(String letter, Color fill) {
            super(letter, JLabel.CENTER);
            this.fill = fill;
            setOpaque(false);
            setFont(new Font("Tahoma", Font.BOLD, 18));
            setForeground(Color.WHITE);
            var size = new Dimension(BADGE_SIZE, BADGE_SIZE);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(fill);
            g.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            super.paintComponent(g);
        }
    }

    /** Flat rounded button whose fill follows hover and pressed states. */
    private static class RoundedButton extends JButton {
        private static final int ARC = 12;

        private final Color normal;
        private final Color hover;
        private final Color pressed;
        private final Color edge;
        private boolean down;
        private boolean over;

        RoundedButton(String text, Color normal, Color hover, Color pressed, Color fg, Color edge) {
            super(text);
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
            this.edge = edge;
            setFont(BODY_BOLD);
            setForeground(fg);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 22, 10, 22));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    over = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    over = false;
                    down = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    down = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    down = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(down ? pressed : (over ? hover : normal));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g.setColor(isEnabled() ? edge : CARD_BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            super.paintComponent(g);
        }
    }
}
