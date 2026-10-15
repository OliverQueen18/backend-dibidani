package ml.dibidani.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import ml.dibidani.common.Slugs;
import java.util.Arrays;
import java.util.List;

/** Normalisation des termes et expressions SQL alignées sur les index trigrammes de la migration V2. */
public final class SearchText {
  public static final char ESCAPE = '\\';

  private SearchText() {}

  public static List<String> terms(String q) {
    return Arrays.stream(Slugs.normalize(q).split("\\s+"))
        .filter(t -> !t.isBlank())
        .map(SearchText::singular)
        .distinct()
        .limit(6)
        .toList();
  }

  /** « perceuses » doit encore matcher « perceuse ». */
  static String singular(String t) {
    if (t.length() >= 5 && t.endsWith("s") && !t.endsWith("ss")) return t.substring(0, t.length() - 1);
    return t;
  }

  public static String contains(String term) { return "%" + escape(term) + "%"; }
  public static String prefix(String term) { return escape(term) + "%"; }

  private static String escape(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  /** lower(f_unaccent(expr)) */
  public static Expression<String> norm(CriteriaBuilder cb, Expression<String> expr) {
    return cb.lower(cb.function("f_unaccent", String.class, expr));
  }

  /** lower(f_unaccent(coalesce(expr, ''))) pour les colonnes nullables indexées ainsi. */
  public static Expression<String> normNullable(CriteriaBuilder cb, Expression<String> expr) {
    return norm(cb, cb.coalesce(expr, cb.literal("")));
  }
}
