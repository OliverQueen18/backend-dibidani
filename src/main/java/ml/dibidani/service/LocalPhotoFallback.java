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

/** Cadrage blanc + signature sans moteur IA, si le service Python est indisponible. */
@Component
public class LocalPhotoFallback {
  private static final Logger log = LoggerFactory.getLogger(LocalPhotoFallback.class);

  public Map<String, String> process(Path original, Path destDir, String stem, Map<String, Object> options) throws IOException {
    BufferedImage src = ImageIO.read(original.toFile());
    if (src == null) throw new IllegalArgumentException("Image illisible");
    int side = 1200;
    BufferedImage canvas = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = canvas.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
    if (Boolean.TRUE.equals(options.get("watermark"))) {
      drawMark(g, side, options);
    }
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

  private void drawMark(Graphics2D g, int side, Map<String, Object> options) {
    try (InputStream in = getClass().getResourceAsStream("/photo-studio/favicon-dibidani.png")) {
      if (in == null) return;
      BufferedImage mark = ImageIO.read(in);
      if (mark == null) return;
      double sizeRatio = options.get("watermarkSize") instanceof Number n ? n.doubleValue() : 0.06;
      double marginRatio = options.get("watermarkMargin") instanceof Number n ? n.doubleValue() : 0.025;
      double opacity = options.get("watermarkOpacity") instanceof Number n ? n.doubleValue() : 0.65;
      int tw = Math.max(16, (int) (side * sizeRatio));
      int th = (int) (mark.getHeight() * (tw / (double) mark.getWidth()));
      int margin = (int) (side * marginRatio);
      String pos = String.valueOf(options.getOrDefault("watermarkPosition", "BOTTOM_RIGHT"));
      int x = margin, y = margin;
      if (pos.contains("RIGHT")) x = side - tw - margin;
      if (pos.contains("BOTTOM")) y = side - th - margin;
      Composite old = g.getComposite();
      g.setComposite(AlphaComposite.SrcOver.derive((float) Math.min(1, Math.max(0.2, opacity))));
      g.drawImage(mark, x, y, tw, th, null);
      g.setComposite(old);
    } catch (IOException e) {
      log.debug("Signature Dibidani absente : {}", e.getMessage());
    }
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
