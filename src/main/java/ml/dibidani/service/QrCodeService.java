package ml.dibidani.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

@Service
public class QrCodeService {
  /** Vert pétrole Dibidani : contraste suffisant pour tous les lecteurs. */
  private static final int DARK = 0xFF0E4C43;
  private static final int LIGHT = 0xFFFFFFFF;

  public byte[] png(String content, int size) {
    try {
      BitMatrix m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size,
          Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2, EncodeHintType.CHARACTER_SET, "UTF-8"));
      BufferedImage img = new BufferedImage(m.getWidth(), m.getHeight(), BufferedImage.TYPE_INT_RGB);
      for (int x = 0; x < m.getWidth(); x++) {
        for (int y = 0; y < m.getHeight(); y++) img.setRGB(x, y, m.get(x, y) ? DARK : LIGHT);
      }
      var out = new ByteArrayOutputStream();
      ImageIO.write(img, "png", out);
      return out.toByteArray();
    } catch (WriterException e) {
      throw new IllegalArgumentException("QR code impossible à générer", e);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
