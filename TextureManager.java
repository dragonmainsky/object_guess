import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * TextureManager loads and caches textures from the UI_ONLY folder.
 * Supports multiple lookup paths to work seamlessly whether run from UI_ONLY or project root.
 */
public class TextureManager {
    private static final Map<String, BufferedImage> cache = new HashMap<>();

    private static final String[] SEARCH_DIRS = {
        ".",
        "UI_ONLY",
        "object_texture/UI_ONLY",
        "object_texture",
        "../UI_ONLY",
        "../object_texture/UI_ONLY",
        "../../object_texture/UI_ONLY"
    };

    /**
     * Loads a BufferedImage with caching.
     */
    public static BufferedImage getImage(String filename) {
        if (cache.containsKey(filename)) {
            return cache.get(filename);
        }

        BufferedImage img = null;

        // Try file system across multiple search paths
        for (String dir : SEARCH_DIRS) {
            File f = new File(dir, filename);
            if (f.exists() && f.isFile()) {
                try {
                    img = ImageIO.read(f);
                    if (img != null) break;
                } catch (Exception ignored) {}
            }
        }

        // Try classpath resource as fallback
        if (img == null) {
            try {
                InputStream is = TextureManager.class.getResourceAsStream("/" + filename);
                if (is != null) {
                    img = ImageIO.read(is);
                }
            } catch (Exception ignored) {}
        }

        if (img != null) {
            cache.put(filename, img);
        }

        return img;
    }

    /**
     * Returns an ImageIcon scaled to the specified width and height.
     */
    public static ImageIcon getIcon(String filename, int width, int height) {
        BufferedImage img = getImage(filename);
        if (img == null) {
            return createPlaceholderIcon(filename, width, height);
        }
        Image scaled = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    /**
     * Fallback placeholder icon in case a texture file cannot be found.
     */
    private static ImageIcon createPlaceholderIcon(String name, int w, int h) {
        BufferedImage buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buf.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(220, 230, 242));
        g2.fillRoundRect(0, 0, w, h, 12, 12);
        g2.setColor(new Color(70, 90, 120));
        g2.drawRoundRect(0, 0, w - 1, h - 1, 12, 12);
        g2.setFont(new Font("Segoe UI", Font.BOLD, Math.max(10, w / 6)));
        FontMetrics fm = g2.getFontMetrics();
        String label = name.replace(".png", "");
        if (label.length() > 6) label = label.substring(0, 6);
        int tx = (w - fm.stringWidth(label)) / 2;
        int ty = ((h - fm.getHeight()) / 2) + fm.getAscent();
        g2.drawString(label, tx, ty);
        g2.dispose();
        return new ImageIcon(buf);
    }
}
