package ml.dibidani.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

final class Hmac {
  private Hmac() {}

  static String sha256Hex(String secret, String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC indisponible", e);
    }
  }

  /** Comparaison à temps constant (insensible à la casse hexadécimale). */
  static boolean matches(String expectedHex, String providedHex) {
    if (expectedHex == null || providedHex == null) return false;
    String provided = providedHex.trim().toLowerCase();
    if (provided.startsWith("sha256=")) provided = provided.substring(7);
    return MessageDigest.isEqual(expectedHex.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
  }

  static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
