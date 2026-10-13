package ml.dibidani.notify;

public final class Phones {
  private Phones() {}

  /** E.164, Mali (+223) par défaut pour un numéro local à 8 chiffres. */
  public static String e164(String raw) {
    if (raw == null) return null;
    String trimmed = raw.trim();
    if (trimmed.isEmpty()) return null;
    String digits = trimmed.replaceAll("[^0-9]", "");
    if (digits.isEmpty()) return null;
    if (trimmed.startsWith("+")) return "+" + digits;
    if (digits.startsWith("00")) digits = digits.substring(2);
    if (digits.startsWith("223") && digits.length() >= 11) return "+" + digits;
    if (digits.length() == 8) return "+223" + digits;
    return "+" + digits;
  }

  public static String digits(String raw) {
    if (raw == null) return "";
    return raw.replaceAll("[^0-9]", "");
  }

  public static String last8(String raw) {
    String d = digits(raw);
    return d.length() <= 8 ? d : d.substring(d.length() - 8);
  }
}
