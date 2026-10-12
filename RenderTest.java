import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

public class RenderTest {
    public static void main(String[] args) throws Exception {
        render(new JoinScreen(), "render_join.png");
        render(new LobbyScreen(), "render_lobby.png");
        render(new QuestionerScreen(), "render_questioner.png");
        render(new GuesserScreen(), "render_guesser.png");
        render(new ScoreboardScreen(), "render_scoreboard.png");
        System.out.println("ALL RENDERED SUCCESSFULLY");
    }

    private static void render(JComponent comp, String filename) throws Exception {
        JFrame f = new JFrame();
        f.setSize(1360, 780);
        f.add(comp);
        f.addNotify();
        f.validate();
        comp.setSize(1360, 780);
        layoutTree(comp);

        BufferedImage img = new BufferedImage(1360, 780, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        comp.paint(g2);
        g2.dispose();
        ImageIO.write(img, "png", new File(filename));
        f.dispose();
    }

    private static void layoutTree(Component c) {
        c.doLayout();
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                layoutTree(child);
            }
        }
    }
}
