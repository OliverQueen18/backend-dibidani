package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connexion Google / Facebook : identifiants uniquement via l'environnement. */
@ConfigurationProperties(prefix = "app.oauth")
public record OAuthProperties(boolean mockEnabled, Google google, Facebook facebook) {

  public record Google(String clientId, String clientSecret) {
    public boolean configured() { return present(clientId); }
  }

  public record Facebook(String appId, String appSecret) {
    public boolean configured() { return present(appId); }
    public boolean secretConfigured() { return present(appId) && present(appSecret); }
  }

  static boolean present(String v) { return v != null && !v.isBlank(); }
}
