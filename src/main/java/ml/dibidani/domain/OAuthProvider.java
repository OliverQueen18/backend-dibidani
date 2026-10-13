package ml.dibidani.domain;

import java.util.Locale;

public enum OAuthProvider {
  GOOGLE, FACEBOOK;

  public static OAuthProvider parse(String raw) {
    if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Le fournisseur est obligatoire");
    try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
    catch (IllegalArgumentException e) { throw new IllegalArgumentException("Fournisseur inconnu. Utilisez Google ou Facebook."); }
  }
}
