import java.awt.Image;
import java.awt.Toolkit;
import java.io.File;
import javax.swing.ImageIcon;

/**
 * ShapeImages: the single place that maps a shape name to its PNG in <project>/shape.
 *
 * The mapping used to be copy-pasted into QuestionerScreen, GuesserScreen and LobbyScreen.
 * New screens (RoundResultScreen) use this helper instead, so a name change or a renamed
 * file only has to be corrected here.
 *
 * Note the two lower-case file names: Diamond -> diamond.png and Star -> star.png.
 * Loading follows the same "returns null when the file is missing" contract the older
 * screens use, so callers can fall back to a plain grey placeholder box.
 */
public final class ShapeImages {

    /** Folder that holds every PNG picture: <project folder>/shape */
    private static final String SHAPE_DIR = System.getProperty("user.dir") + "/shape/";

    /** {display name, picture file} - keep in step with GameServer.SHAPE_NAMES. */
    private static final String[][] AVAILABLE_SHAPES = {
        {"Circle", "Circle.png"}, {"Triangle", "Triangle.png"}, {"Rectangle", "Rectangle.png"}, {"Oval", "Oval.png"},
        {"Hexagon", "Hexagon.png"}, {"Diamond", "diamond.png"}, {"Star", "star.png"}, {"Square", "Square.png"}
    };

    private ShapeImages() {
        // static helper only
    }

    /** File name for a shape, falling back to Circle.png when the name is unknown. */
    public static String fileNameFor(String shapeName) {
        if (shapeName != null) {
            for (String[] shape : AVAILABLE_SHAPES) {
                if (shape[0].equalsIgnoreCase(shapeName)) {
                    return shape[1];
                }
            }
        }
        return "Circle.png";
    }

    /** Scaled icon for a shape, or null when the picture file cannot be found. */
    public static ImageIcon icon(String shapeName, int size) {
        ImageIcon result = null;
        File file = new File(SHAPE_DIR + fileNameFor(shapeName));
        if (file.exists() && file.isFile()) {
            Image raw = Toolkit.getDefaultToolkit().createImage(file.getPath());
            Image scaled = raw.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            result = new ImageIcon(scaled);
        }
        return result;
    }
}
