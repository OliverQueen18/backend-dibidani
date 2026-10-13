package ml.dibidani.common;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

public final class Slugs {
  private Slugs() {}

  /** Minuscules sans accents : la même normalisation que f_unaccent/lower côté PostgreSQL. */
  public static String normalize(String value) {
    if (value == null) return "";
    String n = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return n.toLowerCase(Locale.ROOT).trim();
  }

  public static String slugify(String value) {
    String s = normalize(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    return s.isEmpty() ? "element" : s;
  }

  /** Slug explicite tel quel, sinon slug dérivé du nom rendu unique par suffixe numérique. */
  public static String resolve(String requested, String name, Predicate<String> taken) {
    if (requested != null && !requested.isBlank()) return slugify(requested);
    String base = slugify(name);
    String candidate = base;
    for (int i = 2; taken.test(candidate); i++) candidate = base + "-" + i;
    return candidate;
  }

  public static String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
}
