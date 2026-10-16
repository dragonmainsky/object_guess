import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/**
 * AppDialog: one shared replacement for the default JOptionPane alerts.
 *
 * Every notification in the game goes through here, so all of them look the same and match
 * the screens: a white rounded card with a thin coloured border and an accent bar, a round
 * coloured icon badge with a plain letter, a bold title, a wrapped body line and styled
 * buttons. The colours follow the type (info = blue, warning = amber, error = red) and reuse
 * the same palette as GuesserScreen.
 *
 * All methods are static and mirror the old JOptionPane calls, so replacing a call site is a
 * one-line change. They build a modal JDialog centred on the parent window and only return
 * after the player answers. Like JOptionPane, they must be called from the Event Dispatch
 * Thread (which is where this app already shows every alert).
 */
public final class AppDialog {

    // ===== Palette (copied from the game screens) =====
    private static final Color CARD_BG        = Color.WHITE;
    private static final Color CARD_BORDER    = new Color(226, 232, 240);
    private static final Color DIALOG_BG      = new Color(241, 245, 249);

    private static final Color PRIMARY_BLUE   = new Color(37, 99, 235);
    private static final Color PRIMARY_HOVER  = new Color(29, 78, 216);
    private static final Color PRIMARY_PRESS  = new Color(30, 64, 175);
    private static final Color BLUE_BORDER    = new Color(191, 219, 254);

    private static final Color WARNING_TEXT   = new Color(133, 77, 14);
    private static final Color WARNING_BORDER = new Color(254, 240, 138);

    private static final Color DANGER_TEXT    = new Color(185, 28, 28);
    private static final Color DANGER_BORDER  = new Color(254, 202, 202);

    private static final Color TEXT_DARK      = new Color(15, 23, 42);
    private static final Color TEXT_MUTED     = new Color(100, 116, 139);
    private static final Color SECONDARY_HOVER   = new Color(241, 245, 249);
    private static final Color SECONDARY_PRESSED = new Color(226, 232, 240);

    // ===== Fonts =====
    private static final Font FONT_TITLE   = new Font("Tahoma", Font.BOLD, 17);
    private static final Font FONT_MESSAGE = new Font("Tahoma", Font.PLAIN, 14);
    private static final Font FONT_BUTTON  = new Font("Tahoma", Font.BOLD, 13);
    private static final Font FONT_BADGE   = new Font("Tahoma", Font.BOLD, 18);

    // ===== Sizes =====
    private static final int BADGE_SIZE = 44;
    private static final int BODY_MAX_WIDTH = 380;
    private static final int CARD_ARC = 16;
    private static final int ACCENT_HEIGHT = 6;

    // ===== Dialog types =====
    private static final int TYPE_INFO = 0;
    private static final int TYPE_WARNING = 1;
    private static final int TYPE_ERROR = 2;
    private static final int TYPE_CONFIRM = 3;

    private AppDialog() {
        // utility class: never instantiated
    }

    /** Blue information alert with a single OK button. */
    public static void showInfo(Component parent, String title, String message) {
        show(TYPE_INFO, parent, title, message, null);
    }

    /** Amber warning alert with a single OK button. */
    public static void showWarning(Component parent, String title, String message) {
        show(TYPE_WARNING, parent, title, message, null);
    }

    /** Red error alert with a single OK button. */
    public static void showError(Component parent, String title, String message) {
        show(TYPE_ERROR, parent, title, message, null);
    }

    /** Blue Yes/No confirmation. Returns true only when the primary (Yes) button is chosen. */
    public static boolean showConfirm(Component parent, String title, String message,
                                      String yesText, String noText) {
        return show(TYPE_CONFIRM, parent, title, message, new String[] {yesText, noText});
    }

    // =====================================================================
    //  Shared builder
    // =====================================================================

    private static boolean show(int type, Component parent, String title, String message, String[] confirmText) {
        final boolean[] answer = new boolean[] {false};
        final boolean confirm = (confirmText != null);

        Window owner = (parent == null) ? null : SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog;
        if (owner == null) {
            dialog = new JDialog((Frame) null, safe(title), JDialog.ModalityType.APPLICATION_MODAL);
        } else {
            dialog = new JDialog(owner, safe(title), JDialog.ModalityType.APPLICATION_MODAL);
        }
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        RoundedCard card = buildCard(type, title, message);
        final JButton primaryBtn;
        if (confirm) {
            primaryBtn = buildConfirmButtons(card, dialog, answer, confirmText[0], confirmText[1]);
        } else {
            primaryBtn = buildSingleButton(card, dialog, answer);
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(DIALOG_BG);
        root.add(card, BorderLayout.CENTER);
        dialog.setContentPane(root);

        installEscapeKey(dialog, answer);
        dialog.getRootPane().setDefaultButton(primaryBtn);
        // Focus lands on the primary button as soon as the window is up.
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent event) {
                primaryBtn.requestFocusInWindow();
            }
        });

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true); // modal: blocks here until the window is closed

        return answer[0];
    }

    private static JButton buildSingleButton(RoundedCard card, final JDialog dialog, final boolean[] answer) {
        RoundedButton okBtn = new RoundedButton("OK", PRIMARY_BLUE, PRIMARY_HOVER, PRIMARY_PRESS,
                Color.WHITE, PRIMARY_BLUE);
        okBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                answer[0] = true;
                dialog.dispose();
            }
        });
        card.add(buttonRow(okBtn), BorderLayout.SOUTH);
        return okBtn;
    }

    private static JButton buildConfirmButtons(RoundedCard card, final JDialog dialog, final boolean[] answer,
                                               String yesText, String noText) {
        RoundedButton noBtn = new RoundedButton(safeText(noText, "No"), CARD_BG, SECONDARY_HOVER,
                SECONDARY_PRESSED, TEXT_DARK, CARD_BORDER);
        noBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                answer[0] = false;
                dialog.dispose();
            }
        });

        RoundedButton yesBtn = new RoundedButton(safeText(yesText, "Yes"), PRIMARY_BLUE, PRIMARY_HOVER,
                PRIMARY_PRESS, Color.WHITE, PRIMARY_BLUE);
        yesBtn.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                answer[0] = true;
                dialog.dispose();
            }
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        row.add(noBtn);
        row.add(yesBtn);
        card.add(row, BorderLayout.SOUTH);
        return yesBtn;
    }

    private static JPanel buttonRow(JButton button) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        row.setOpaque(false);
        row.add(button);
        return row;
    }

    /** ESC cancels: it counts as "No" for a confirmation and simply closes the other dialogs. */
    private static void installEscapeKey(JDialog dialog, final boolean[] answer) {
        dialog.getRootPane().registerKeyboardAction(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                answer[0] = false;
                dialog.dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // =====================================================================
    //  Card content
    // =====================================================================

    private static RoundedCard buildCard(int type, String title, String message) {
        RoundedCard card = new RoundedCard(accentOf(type), borderOf(type));
        card.setLayout(new BorderLayout(0, 16));
        // Extra top padding leaves room for the accent bar the card paints along its top edge.
        card.setBorder(new EmptyBorder(ACCENT_HEIGHT + 18, 24, 20, 24));

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.add(new CircleBadge(letterOf(type), accentOf(type)), BorderLayout.WEST);

        JLabel titleLabel = new JLabel(safe(title));
        titleLabel.setFont(FONT_TITLE);
        titleLabel.setForeground(TEXT_DARK);
        header.add(titleLabel, BorderLayout.CENTER);
        card.add(header, BorderLayout.NORTH);

        // Body sits under the title, indented past the badge column so it lines up with the title.
        JPanel bodyHolder = new JPanel(new BorderLayout());
        bodyHolder.setOpaque(false);
        bodyHolder.setBorder(new EmptyBorder(0, BADGE_SIZE + 14, 0, 0));
        bodyHolder.add(wrappedBody(message), BorderLayout.CENTER);
        card.add(bodyHolder, BorderLayout.CENTER);

        return card;
    }

    /** Non-editable text area that wraps long messages at BODY_MAX_WIDTH instead of stretching the dialog. */
    private static JTextArea wrappedBody(String message) {
        JTextArea body = new JTextArea(safe(message));
        body.setFont(FONT_MESSAGE);
        body.setForeground(TEXT_MUTED);
        body.setOpaque(false);
        body.setEditable(false);
        body.setFocusable(false);
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setBorder(null);
        // Size it to the maximum width first, then ask for the height that wrapping produced.
        body.setSize(new Dimension(BODY_MAX_WIDTH, Short.MAX_VALUE));
        Dimension wrapped = body.getPreferredSize();
        body.setPreferredSize(wrapped);
        body.setMinimumSize(wrapped);
        return body;
    }

    // =====================================================================
    //  Colours, letters and small helpers
    // =====================================================================

    private static Color accentOf(int type) {
        if (type == TYPE_WARNING) {
            return WARNING_TEXT;
        }
        if (type == TYPE_ERROR) {
            return DANGER_TEXT;
        }
        return PRIMARY_BLUE;
    }

    private static Color borderOf(int type) {
        if (type == TYPE_WARNING) {
            return WARNING_BORDER;
        }
        if (type == TYPE_ERROR) {
            return DANGER_BORDER;
        }
        return BLUE_BORDER;
    }

    private static String letterOf(int type) {
        if (type == TYPE_WARNING) {
            return "!";
        }
        if (type == TYPE_ERROR) {
            return "x";
        }
        if (type == TYPE_CONFIRM) {
            return "?";
        }
        return "i";
    }

    private static String safe(String text) {
        return (text == null) ? "" : text;
    }

    private static String safeText(String text, String fallback) {
        if (text == null || text.isEmpty()) {
            return fallback;
        }
        return text;
    }

    // =====================================================================
    //  Custom-painted parts (rounded card, circle badge, rounded button)
    // =====================================================================

    /** White card with rounded corners, a thin border and a coloured accent bar along the top. */
    private static class RoundedCard extends JPanel {
        private final Color accent;
        private final Color border;

        RoundedCard(Color accent, Color border) {
            this.accent = accent;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            Shape shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, CARD_ARC, CARD_ARC);

            g2.setColor(CARD_BG);
            g2.fill(shape);

            // The accent bar is clipped to the rounded shape so it follows the top corners.
            Shape previousClip = g2.getClip();
            g2.setClip(shape);
            g2.setColor(accent);
            g2.fillRect(0, 0, w, ACCENT_HEIGHT);
            g2.setClip(previousClip);

            g2.setColor(border);
            g2.draw(new RoundRectangle2D.Float(1, 1, w - 3, h - 3, CARD_ARC, CARD_ARC));
            g2.dispose();
        }
    }

    /** Round coloured badge with a plain white letter (no image files, no emoji). */
    private static class CircleBadge extends JLabel {
        private final Color fill;

        CircleBadge(String letter, Color fill) {
            super(letter, JLabel.CENTER);
            this.fill = fill;
            setOpaque(false);
            setFont(FONT_BADGE);
            setForeground(Color.WHITE);
            Dimension size = new Dimension(BADGE_SIZE, BADGE_SIZE);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Flat rounded button whose fill follows hover and pressed states. */
    private static class RoundedButton extends JButton {
        private static final int ARC = 12;

        private final Color normal;
        private final Color hover;
        private final Color pressed;
        private final Color borderColor;

        RoundedButton(String text, Color normal, Color hover, Color pressed, Color fg, Color borderColor) {
            super(text);
            this.normal = normal;
            this.hover = hover;
            this.pressed = pressed;
            this.borderColor = borderColor;
            setFont(FONT_BUTTON);
            setForeground(fg);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 22, 10, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color fill = normal;
            if (getModel().isPressed()) {
                fill = pressed;
            } else if (getModel().isRollover()) {
                fill = hover;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.setColor(isEnabled() ? borderColor : CARD_BORDER);
            g2.drawRoundRect(0, 0, w - 1, h - 1, ARC, ARC);
            g2.dispose();

            super.paintComponent(g);
        }
    }
}
