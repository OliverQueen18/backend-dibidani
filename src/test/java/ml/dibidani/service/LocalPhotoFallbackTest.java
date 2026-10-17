package ml.dibidani.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LocalPhotoFallbackTest {
  @TempDir Path tmp;

  @Test
  void squaresWithoutDistortionAndKeepsWhiteCorners() throws Exception {
    BufferedImage src = new BufferedImage(200, 80, BufferedImage.TYPE_INT_RGB);
    var g = src.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 200, 80);
    g.setColor(new Color(180, 20, 20));
    g.fillRect(40, 10, 120, 60);
    g.dispose();
    Path original = tmp.resolve("in.jpg");
    ImageIO.write(src, "jpg", original.toFile());
    Path dest = tmp.resolve("out");
    var files = new LocalPhotoFallback().process(original, dest, "t", Map.of(
        "fillRatio", 0.82, "zoom", 1.0, "offsetX", 0, "offsetY", 0, "watermark", false
    ));
    assertTrue(dest.resolve(files.get("masterJpeg")).toFile().isFile());
    BufferedImage master = ImageIO.read(dest.resolve(files.get("masterJpeg")).toFile());
    assertEquals(1200, master.getWidth());
    assertEquals(1200, master.getHeight());
    int c = master.getRGB(8, 8);
    int r = (c >> 16) & 255, gv = (c >> 8) & 255, b = c & 255;
    assertTrue((r + gv + b) / 3 > 240, "coin non blanc");
  }

  @Test
  void flattensUniformBackgroundToWhite() throws Exception {
    BufferedImage src = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
    var g = src.createGraphics();
    g.setColor(new Color(210, 210, 205));
    g.fillRect(0, 0, 200, 200);
    g.setColor(new Color(20, 90, 200));
    g.fillOval(50, 50, 100, 100);
    g.dispose();
    BufferedImage flat = LocalPhotoFallback.flattenBackground(src);
    int corner = flat.getRGB(4, 4);
    assertEquals(0, (corner >> 24) & 255, "le fond doit devenir transparent");
    int center = flat.getRGB(100, 100);
    assertEquals(255, (center >> 24) & 255);
    assertEquals(20, (center >> 16) & 255);
  }
}
