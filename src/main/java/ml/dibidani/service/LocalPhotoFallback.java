package ml.dibidani.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cadrage blanc, détourage simple des fonds unis, et signature si le service Python est indisponible. */
@Component
public class LocalPhotoFallback {
  private static final Logger log = LoggerFactory.getLogger(LocalPhotoFallback.class);

  public Map<String, String> process(Path original, Path destDir, String stem, Map<String, Object> options) throws IOException {
    BufferedImage src = ImageIO.read(original.toFile());
    if (src == null) {
      throw new IllegalArgumentException("Image illisible. Envoyez un JPEG ou un PNG (les photos HEIC d’iPhone ne sont pas lues ici).");
    }
    boolean flatten = flag(options, "removeBackground") || flag(options, "whiteBackground");
    if (flatten) src = flattenBackground(src);
    int side = 1200;
    BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = canvas.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, side, side);
    double fill = options.get("fillRatio") instanceof Number n ? n.doubleValue() : 0.82;
    double zoom = options.get("zoom") instanceof Number n ? n.doubleValue() : 1;
    double ox = options.get("offsetX") instanceof Number n ? n.doubleValue() : 0;
    double oy = options.get("offsetY") instanceof Number n ? n.doubleValue() : 0;
    int target = (int) (side * fill * zoom);
    double scale = Math.min(target / (double) src.getWidth(), target / (double) src.getHeight());
    int dw = Math.max(1, (int) (src.getWidth() * scale));
    int dh = Math.max(1, (int) (src.getHeight() * scale));
    int x = (side - dw) / 2 + (int) (ox * side);
    int y = (side - dh) / 2 + (int) (oy * side);
    g.drawImage(src, x, y, dw, dh, null);
    if (flag(options, "watermark")) drawMark(g, side, options);
    g.dispose();
    Files.createDirectories(destDir);
    writeJpeg(canvas, destDir.resolve(stem + "-master.jpg"), 0.86f);
    writeJpeg(scaleTo(canvas, 600), destDir.resolve(stem + "-catalog.jpg"), 0.84f);
    writeJpeg(scaleTo(canvas, 300), destDir.resolve(stem + "-thumb.jpg"), 0.82f);
    Map<String, String> files = new LinkedHashMap<>();
    files.put("masterJpeg", stem + "-master.jpg");
    files.put("catalogJpeg", stem + "-catalog.jpg");
    files.put("thumbJpeg", stem + "-thumb.jpg");
    return files;
  }

  /** Remplace un fond assez uni (coins similaires) par du blanc, sans moteur IA. */
  static BufferedImage flattenBackground(BufferedImage src) {
    int w = src.getWidth();
    int h = src.getHeight();
    int[] corners = {
        sample(src, 0, 0, 12), sample(src, w - 12, 0, 12),
        sample(src, 0, h - 12, 12), sample(src, w - 12, h - 12, 12)
    };
    if (maxSpread(corners) > 70) return src;
    int bg = average(corners);
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    int thresh = 42 + Math.min(36, brightness(bg) / 8);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int p = src.getRGB(x, y);
        if (distance(p, bg) <= thresh) out.setRGB(x, y, 0x00FFFFFF);
        else out.setRGB(x, y, p | 0xFF000000);
      }
    }
    return out;
  }

  private void drawMark(Graphics2D g, int side, Map<String, Object> options) {
    double sizeRatio = Math.max(0.11, options.get("watermarkSize") instanceof Number n ? n.doubleValue() : 0.11);
    double marginRatio = options.get("watermarkMargin") instanceof Number n ? n.doubleValue() : 0.03;
    double opacity = options.get("watermarkOpacity") instanceof Number n ? n.doubleValue() : 0.82;
    int tw = Math.max(48, (int) (side * sizeRatio));
    int margin = (int) (side * marginRatio);
    String pos = String.valueOf(options.getOrDefault("watermarkPosition", "BOTTOM_RIGHT"));
    try (InputStream in = getClass().getResourceAsStream("/photo-studio/favicon-dibidani.png")) {
      BufferedImage mark = in == null ? null : ImageIO.read(in);
      if (mark != null) {
        int th = Math.max(16, (int) (mark.getHeight() * (tw / (double) mark.getWidth())));
        int x = pos.contains("RIGHT") ? side - tw - margin : margin;
        int y = pos.contains("BOTTOM") ? side - th - margin : margin;
        if (pos.contains("CENTER") && !pos.contains("RIGHT") && !pos.contains("LEFT")) x = (side - tw) / 2;
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, Math.max(0.35, opacity))));
        g.drawImage(mark, x, y, tw, th, null);
        g.setComposite(old);
        return;
      }
    } catch (IOException e) {
      log.debug("Signature image absente : {}", e.getMessage());
    }
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(22, tw / 3)));
    FontMetrics fm = g.getFontMetrics();
    String label = "Dibidani";
    int x = pos.contains("RIGHT") ? side - fm.stringWidth(label) - margin : margin;
    int y = pos.contains("BOTTOM") ? side - margin : margin + fm.getAscent();
    g.setColor(new Color(8, 122, 71, (int) (Math.min(1, Math.max(0.45, opacity)) * 255)));
    g.drawString(label, x, y);
  }

  private static boolean flag(Map<String, Object> options, String key) {
    Object v = options.get(key);
    if (v instanceof Boolean b) return b;
    return v != null && !"false".equalsIgnoreCase(String.valueOf(v));
  }

  private static int sample(BufferedImage img, int x, int y, int size) {
    int w = img.getWidth(), h = img.getHeight();
    long r = 0, g = 0, b = 0;
    int n = 0;
    for (int yy = y; yy < Math.min(h, y + size); yy++) {
      for (int xx = x; xx < Math.min(w, x + size); xx++) {
        int p = img.getRGB(xx, yy);
        r += (p >> 16) & 255;
        g += (p >> 8) & 255;
        b += p & 255;
        n++;
      }
    }
    if (n == 0) return 0xFFFFFF;
    return (0xFF << 24) | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
  }

  private static int average(int[] colors) {
    long r = 0, g = 0, b = 0;
    for (int c : colors) {
      r += (c >> 16) & 255;
      g += (c >> 8) & 255;
      b += c & 255;
    }
    return (0xFF << 24) | ((int) (r / colors.length) << 16) | ((int) (g / colors.length) << 8) | (int) (b / colors.length);
  }

  private static int maxSpread(int[] colors) {
    int max = 0;
    for (int i = 0; i < colors.length; i++) {
      for (int j = i + 1; j < colors.length; j++) max = Math.max(max, distance(colors[i], colors[j]));
    }
    return max;
  }

  private static int distance(int a, int b) {
    int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
    int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
    int db = (a & 255) - (b & 255);
    return (int) Math.sqrt(dr * dr + dg * dg + db * db);
  }

  private static int brightness(int c) {
    return (((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255)) / 3;
  }

  private static BufferedImage scaleTo(BufferedImage src, int size) {
    BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(src, 0, 0, size, size, null);
    g.dispose();
    return out;
  }

  private static void writeJpeg(BufferedImage img, Path dest, float quality) throws IOException {
    var writers = ImageIO.getImageWritersByFormatName("jpg");
    if (!writers.hasNext()) {
      ImageIO.write(img, "jpg", dest.toFile());
      return;
    }
    var writer = writers.next();
    try (var out = ImageIO.createImageOutputStream(dest.toFile())) {
      writer.setOutput(out);
      var param = writer.getDefaultWriteParam();
      if (param.canWriteCompressed()) {
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
      }
      writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
    } finally {
      writer.dispose();
    }
  }
}
