import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * UIStyle defines the modern visual theme, color palette, typography,
 * and custom Swing components resembling modern web/Figma designs.
 * 
 * Supports configurable corner radius (e.g. sharp rectangular corners vs rounded).
 */
public class UIStyle {

    // === Geometry & Corner Configuration ===
    // Set to false for sharp 90-degree rectangular corners (ไม่มีความโค้งมน)
    public static boolean ENABLE_ROUNDED_CORNERS = false;
    public static int DEFAULT_CARD_RADIUS = 0;
    public static int DEFAULT_BUTTON_RADIUS = 0;
    public static int DEFAULT_BADGE_RADIUS = 0;
    public static int DEFAULT_INPUT_RADIUS = 0;

    // === Color Palette ===
    public static final Color BG_DARK_HEADER = new Color(24, 34, 53);
    public static final Color BG_LIGHT       = new Color(241, 245, 249);
    public static final Color CARD_BG        = Color.WHITE;
    public static final Color CARD_BORDER    = new Color(226, 232, 240);

    // Inner "stage" surface: the tinted area nested inside a card (e.g. guess area)
    public static final Color STAGE_BG       = new Color(247, 250, 255);
    public static final Color STAGE_BORDER   = new Color(226, 232, 240);

    public static final Color PRIMARY_BLUE   = new Color(37, 99, 235);
    public static final Color PRIMARY_HOVER  = new Color(29, 78, 216);
    public static final Color BLUE_LIGHT_BG  = new Color(239, 246, 255);
    public static final Color BLUE_BORDER    = new Color(191, 219, 254);

    public static final Color SUCCESS_BG     = new Color(220, 252, 231);
    public static final Color SUCCESS_TEXT   = new Color(22, 101, 52);
    public static final Color SUCCESS_BORDER = new Color(187, 247, 208);

    public static final Color WARNING_BG     = new Color(254, 249, 195);
    public static final Color WARNING_TEXT   = new Color(133, 77, 14);
    public static final Color WARNING_BORDER = new Color(254, 240, 138);

    public static final Color DANGER_BG      = new Color(254, 226, 226);
    public static final Color DANGER_TEXT    = new Color(153, 27, 27);
    public static final Color DANGER_BORDER  = new Color(254, 202, 202);

    public static final Color GOLD_BG        = new Color(254, 243, 199);
    public static final Color GOLD_TEXT      = new Color(180, 83, 9);
    public static final Color GOLD_BORDER    = new Color(253, 230, 138);

    public static final Color PURPLE_BG      = new Color(79, 70, 229);
    public static final Color PURPLE_TEXT    = Color.WHITE;

    public static final Color TEXT_DARK      = new Color(15, 23, 42);
    public static final Color TEXT_MUTED     = new Color(100, 116, 139);
    public static final Color TEXT_LIGHT     = new Color(148, 163, 184);

    // === Typography ===
    public static final Font FONT_HEADER_TITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_HEADER_SUB   = new Font("Segoe UI", Font.BOLD, 10);
    public static final Font FONT_TITLE_LARGE  = new Font("Segoe UI", Font.BOLD, 24);
    public static final Font FONT_TITLE        = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE     = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BODY         = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD    = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL        = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD   = new Font("Segoe UI", Font.BOLD, 11);

    /**
     * CardPanel: A white container with optional border and configurable corner radius.
     */
    public static class CardPanel extends JPanel {
        private int cornerRadius = DEFAULT_CARD_RADIUS;
        private Color bgColor = CARD_BG;
        private Color borderColor = CARD_BORDER;
        private boolean showBorder = true;

        public CardPanel() {
            setOpaque(false);
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(16, 16, 16, 16));
        }

        public CardPanel(int radius, Color bg) {
            this();
            this.cornerRadius = radius;
            this.bgColor = bg;
        }

        public CardPanel(int radius, Color bg, Color border) {
            this();
            this.cornerRadius = radius;
            this.bgColor = bg;
            this.borderColor = border;
            this.showBorder = true;
        }

        public void setCornerRadius(int r) { this.cornerRadius = r; repaint(); }
        public void setBgColor(Color c) { this.bgColor = c; repaint(); }
        public void setBorderColor(Color c) { this.borderColor = c; repaint(); }
        public void setShowBorder(boolean show) { this.showBorder = show; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int r = ENABLE_ROUNDED_CORNERS ? cornerRadius : 0;

            // Fill card
            g2.setColor(bgColor);
            if (r > 0) {
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            } else {
                g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
            }

            // Border
            if (showBorder && borderColor != null) {
                g2.setColor(borderColor);
                if (r > 0) {
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
                } else {
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * ModernButton: Styled button with hover effect and configurable corner radius.
     */
    public static class ModernButton extends JButton {
        private Color normalBg;
        private Color hoverBg;
        private Color normalFg;
        private Color borderColor = null;
        private int cornerRadius = DEFAULT_BUTTON_RADIUS;
        private boolean isHovered = false;
        private VectorIcon leadingIcon = null;
        private int leadingIconSize = 16;
        private int leadingIconGap = 8;

        public ModernButton(String text, Color bg, Color hover, Color fg) {
            super(text);
            this.normalBg = bg;
            this.hoverBg = hover;
            this.normalFg = fg;

            setFont(FONT_BODY_BOLD);
            setForeground(fg);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 18, 10, 18));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        isHovered = true;
                        repaint();
                    }
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        public static ModernButton primary(String text) {
            return new ModernButton(text, PRIMARY_BLUE, PRIMARY_HOVER, Color.WHITE);
        }

        public static ModernButton secondary(String text) {
            ModernButton btn = new ModernButton(text, Color.WHITE, BG_LIGHT, TEXT_DARK);
            btn.setBorderColor(CARD_BORDER);
            return btn;
        }

        public static ModernButton success(String text) {
            return new ModernButton(text, SUCCESS_BG, new Color(187, 247, 208), SUCCESS_TEXT);
        }

        public static ModernButton ghost(String text) {
            return new ModernButton(text, new Color(0, 0, 0, 0), BG_LIGHT, TEXT_MUTED);
        }

        public void setBorderColor(Color border) {
            this.borderColor = border;
            repaint();
        }

        /**
         * Attaches a vector glyph in front of the label, e.g. the paper plane on SUBMIT GUESS.
         * The button paints it inline, so no extra component is added to the border.
         */
        public void setLeadingIcon(VectorIcon icon) {
            this.leadingIcon = icon;
            repaint();
        }

        public void setLeadingIconSize(int size) {
            this.leadingIconSize = size;
            repaint();
        }

        public void setLeadingIconGap(int gap) {
            this.leadingIconGap = gap;
            repaint();
        }

        public void setCornerRadius(int r) {
            this.cornerRadius = r;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color currentBg = isEnabled() ? (isHovered ? hoverBg : normalBg) : new Color(226, 232, 240);
            Color currentFg = isEnabled() ? normalFg : TEXT_LIGHT;

            int r = ENABLE_ROUNDED_CORNERS ? cornerRadius : 0;

            g2.setColor(currentBg);
            if (r > 0) {
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            } else {
                g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
            }

            if (borderColor != null) {
                g2.setColor(borderColor);
                if (r > 0) {
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
                } else {
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
            }

            // Text (+ optional leading icon) painted as one centered group
            g2.setColor(currentFg);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();

            String label = getText() == null ? "" : getText();
            boolean hasIcon = leadingIcon != null && !label.isEmpty();
            int textW = fm.stringWidth(label);
            int iconSize = hasIcon ? leadingIconSize : 0;
            int gap = hasIcon ? leadingIconGap : 0;
            int groupW = textW + (hasIcon ? iconSize + gap : 0);
            int groupH = Math.max(fm.getHeight(), iconSize);

            int groupX = (getWidth() - groupW) / 2;
            int groupY = (getHeight() - groupH) / 2;

            if (hasIcon) {
                leadingIcon.paintVector(g2, groupX, groupY + (groupH - iconSize) / 2, iconSize, iconSize);
                groupX += iconSize + gap;
            }
            if (!label.isEmpty()) {
                int ty = groupY + (groupH - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(label, groupX, ty);
            }

            g2.dispose();
        }
    }

    /**
     * PillBadge: Compact status badge with configurable corner radius.
     */
    public static class PillBadge extends JLabel {
        private Color bgColor;
        private Color borderColor = null;
        private int cornerRadius = DEFAULT_BADGE_RADIUS;

        public PillBadge(String text, Color bg, Color fg) {
            super(text, SwingConstants.CENTER);
            this.bgColor = bg;
            setForeground(fg);
            setFont(FONT_SMALL_BOLD);
            setOpaque(false);
            setBorder(new EmptyBorder(4, 10, 4, 10));
        }

        public PillBadge(String text, Color bg, Color fg, Color border) {
            this(text, bg, fg);
            this.borderColor = border;
        }

        public void setBadgeColors(Color bg, Color fg) {
            this.bgColor = bg;
            setForeground(fg);
            repaint();
        }

        public void setCornerRadius(int r) {
            this.cornerRadius = r;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int r = ENABLE_ROUNDED_CORNERS ? cornerRadius : 0;

            g2.setColor(bgColor);
            if (r > 0) {
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
            } else {
                g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
            }

            if (borderColor != null) {
                g2.setColor(borderColor);
                if (r > 0) {
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, r, r);
                } else {
                    g2.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * RoundedPanel: A rounded surface with its own background + border colors.
     * Unlike the global ENABLE_ROUNDED_CORNERS flag, the radius is always honoured,
     * so a single screen can opt into soft corners without affecting the others.
     */
    public static class RoundedPanel extends JPanel {
        protected Color bgColor;
        protected Color borderColor;
        protected int radius;
        protected float strokeWidth = 1f;

        public RoundedPanel(Color bgColor, Color borderColor, int radius) {
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            this.radius = radius;
            setOpaque(false);
        }

        public void setBgColor(Color c) { this.bgColor = c; repaint(); }
        public void setBorderColor(Color c) { this.borderColor = c; repaint(); }
        public void setCornerRadius(int r) { this.radius = r; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth() - 1;
            int h = getHeight() - 1;
            if (bgColor != null) {
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, w, h, radius, radius);
            }
            if (borderColor != null && w > 0 && h > 0) {
                float inset = strokeWidth / 2f;
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(strokeWidth));
                g2.drawRoundRect((int) inset, (int) inset,
                        Math.max(1, (int) (w - strokeWidth)),
                        Math.max(1, (int) (h - strokeWidth)), radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * VectorIcon: Crisp, font-independent vector glyph (check / cross / bulb / paper plane).
     * Can be painted inline inside other components (see ModernButton#setLeadingIcon).
     */
    public static class VectorIcon extends JComponent {
        public enum Glyph { CHECK, CROSS, BULB, SEND }

        private final Glyph glyph;
        private Color glyphColor;
        private Color circleColor;

        public VectorIcon(Glyph glyph, Color glyphColor) {
            this(glyph, glyphColor, null);
        }

        public VectorIcon(Glyph glyph, Color glyphColor, Color circleColor) {
            this.glyph = glyph;
            this.glyphColor = glyphColor;
            this.circleColor = circleColor;
            setOpaque(false);
            int s = circleColor == null ? 15 : 20;
            Dimension d = new Dimension(s, s);
            setPreferredSize(d);
            setMinimumSize(d);
            setMaximumSize(d);
        }

        public void setGlyphColor(Color c) { this.glyphColor = c; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            paintVector((Graphics2D) g.create(), 0, 0, getWidth(), getHeight());
        }

        /** Paints the glyph into an arbitrary box - lets buttons reuse it inline. */
        public void paintVector(Graphics2D target, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) target.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.translate(x, y);

            if (circleColor != null) {
                g.setColor(circleColor);
                g.fillOval(0, 0, w, h);
            }

            float sw = Math.max(1.6f, Math.min(w, h) * 0.11f);
            switch (glyph) {
                case CHECK: {
                    g.setColor(glyphColor);
                    g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(w * 0.28, h * 0.52);
                    p.lineTo(w * 0.44, h * 0.68);
                    p.lineTo(w * 0.74, h * 0.33);
                    g.draw(p);
                    break;
                }
                case CROSS: {
                    g.setColor(glyphColor);
                    g.setStroke(new BasicStroke(sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(w * 0.33, h * 0.33);
                    p.lineTo(w * 0.67, h * 0.67);
                    p.moveTo(w * 0.67, h * 0.33);
                    p.lineTo(w * 0.33, h * 0.67);
                    g.draw(p);
                    break;
                }
                case BULB: {
                    g.setColor(glyphColor);
                    double cx = w / 2.0;
                    double r = Math.min(w, h) * 0.30;
                    g.fill(new Ellipse2D.Double(cx - r, h * 0.08, r * 2, r * 2));
                    g.fill(new Rectangle2D.Double(cx - r * 0.42, h * 0.52, r * 0.84, h * 0.20));
                    g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(new Line2D.Double(cx - r * 0.55, h * 0.80, cx + r * 0.55, h * 0.80));
                    g.draw(new Line2D.Double(cx - r * 0.38, h * 0.93, cx + r * 0.38, h * 0.93));
                    break;
                }
                case SEND: {
                    g.setColor(glyphColor);
                    g.setStroke(new BasicStroke(Math.max(1.2f, Math.min(w, h) * 0.09f),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(w * 0.05, h * 0.48);
                    p.lineTo(w * 0.95, h * 0.08);
                    p.lineTo(w * 0.60, h * 0.92);
                    p.lineTo(w * 0.42, h * 0.55);
                    p.closePath();
                    g.draw(p);
                    g.draw(new Line2D.Double(w * 0.42, h * 0.55, w * 0.95, h * 0.08));
                    break;
                }
            }
            g.dispose();
        }
    }

    /**
     * KeyCap: Tiny keyboard-key chip used for keyboard hints (e.g. "ENTER").
     */
    public static class KeyCap extends JComponent {
        private final String text;

        public KeyCap(String text) {
            this.text = text;
            setFont(new Font("Segoe UI", Font.BOLD, 9));
            setPreferredSize(new Dimension(44, 19));
            setMinimumSize(new Dimension(44, 19));
            setMaximumSize(new Dimension(44, 19));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g2.setColor(new Color(203, 213, 225));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);

            g2.setColor(new Color(51, 65, 85));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int tx = (getWidth() - fm.stringWidth(text)) / 2;
            int ty = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(text, tx, ty);
            g2.dispose();
        }
    }

    /**
     * AvatarCircle: Circular avatar icon with letter.
     */
    public static class AvatarCircle extends JComponent {
        private final String letter;
        private final Color bgColor;
        private Color fgColor;
        private final int diameter;

        public AvatarCircle(String letter, Color bgColor, int diameter) {
            this(letter, bgColor, Color.WHITE, diameter);
        }

        public AvatarCircle(String letter, Color bgColor, Color fgColor, int diameter) {
            this.letter = letter;
            this.bgColor = bgColor;
            this.fgColor = fgColor;
            this.diameter = diameter;
            setPreferredSize(new Dimension(diameter, diameter));
            setMinimumSize(new Dimension(diameter, diameter));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(bgColor);
            g2.fillOval(0, 0, diameter - 1, diameter - 1);

            g2.setColor(fgColor);
            g2.setFont(new Font("Segoe UI", Font.BOLD, diameter / 2));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (diameter - fm.stringWidth(letter)) / 2;
            int ty = ((diameter - fm.getHeight()) / 2) + fm.getAscent();
            g2.drawString(letter, tx, ty);

            g2.dispose();
        }
    }

    /**
     * Top HeaderBar used across Lobby, Questioner, Guesser, and Scoreboard screens.
     */
    public static class HeaderBar extends JPanel {
        public HeaderBar(String modeBadgeText) {
            setLayout(new BorderLayout(15, 0));
            setBackground(BG_DARK_HEADER);
            setBorder(new EmptyBorder(10, 20, 10, 20));

            // Left side: Logo + Game Title
            JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            leftPanel.setOpaque(false);

            ImageIcon logoIcon = TextureManager.getIcon("logo.png", 32, 32);
            JLabel logoLabel = new JLabel(logoIcon);
            leftPanel.add(logoLabel);

            JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 1));
            titlePanel.setOpaque(false);
            JLabel titleLabel = new JLabel("Guess What It Looks Like?");
            titleLabel.setFont(FONT_HEADER_TITLE);
            titleLabel.setForeground(Color.WHITE);

            JLabel subLabel = new JLabel("MULTIPLAYER PARTY GAME");
            subLabel.setFont(FONT_HEADER_SUB);
            subLabel.setForeground(new Color(156, 163, 175));

            titlePanel.add(titleLabel);
            titlePanel.add(subLabel);
            leftPanel.add(titlePanel);

            add(leftPanel, BorderLayout.WEST);

            // Center: Optional Mode badge (e.g. QUESTIONER MODE)
            if (modeBadgeText != null && !modeBadgeText.isEmpty()) {
                JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
                centerPanel.setOpaque(false);
                PillBadge badge = new PillBadge(modeBadgeText, PURPLE_BG, PURPLE_TEXT);
                badge.setFont(new Font("Segoe UI", Font.BOLD, 12));
                badge.setBorder(new EmptyBorder(4, 16, 4, 16));
                centerPanel.add(badge);
                add(centerPanel, BorderLayout.CENTER);
            }

            // Right side: Room and Online Status
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
            rightPanel.setOpaque(false);

            JLabel roomLabel = new JLabel("Room #01");
            roomLabel.setFont(FONT_BODY_BOLD);
            roomLabel.setForeground(Color.WHITE);

            JLabel dotLabel = new JLabel("● ONLINE");
            dotLabel.setFont(FONT_SMALL_BOLD);
            dotLabel.setForeground(new Color(34, 197, 94)); // vibrant green

            rightPanel.add(roomLabel);
            rightPanel.add(dotLabel);
            add(rightPanel, BorderLayout.EAST);
        }
    }

    /**
     * Creates a styled text field with configurable corner radius.
     */
    public static JTextField createStyledTextField(String text) {
        int r = ENABLE_ROUNDED_CORNERS ? DEFAULT_INPUT_RADIUS : 0;
        JTextField field = new JTextField(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                int curR = ENABLE_ROUNDED_CORNERS ? DEFAULT_INPUT_RADIUS : 0;
                if (curR > 0) {
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, curR, curR);
                } else {
                    g2.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setOpaque(false);
        field.setBackground(Color.WHITE);
        field.setForeground(TEXT_DARK);
        field.setFont(FONT_BODY);
        field.setBorder(BorderFactory.createCompoundBorder(
            new CardBorder(CARD_BORDER, r),
            new EmptyBorder(8, 12, 8, 12)
        ));
        return field;
    }

    /**
     * CardBorder for border lines (sharp or rounded).
     */
    public static class CardBorder extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int radius;

        public CardBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            int r = ENABLE_ROUNDED_CORNERS ? radius : 0;
            if (r > 0) {
                g2.drawRoundRect(x, y, width - 1, height - 1, r, r);
            } else {
                g2.drawRect(x, y, width - 1, height - 1);
            }
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(2, 2, 2, 2);
        }
    }
}
