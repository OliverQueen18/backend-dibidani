package ml.dibidani.common;

import java.security.SecureRandom;
import java.util.Base64;

public final class Tokens {
  private static final SecureRandom RANDOM = new SecureRandom();
  /** Sans 0/O ni 1/I pour pouvoir être lu au téléphone. */
  private static final char[] READABLE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

  private Tokens() {}

  public static String readable(int length) {
    char[] out = new char[length];
    for (int i = 0; i < length; i++) out[i] = READABLE[RANDOM.nextInt(READABLE.length)];
    return new String(out);
  }

  public static String digits(int length) {
    int bound = 1;
    for (int i = 0; i < length; i++) bound *= 10;
    return String.format("%0" + length + "d", RANDOM.nextInt(bound));
  }

  /** 256 bits d'aléa encodés en base64 URL (43 caractères). */
  public static String secure() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
