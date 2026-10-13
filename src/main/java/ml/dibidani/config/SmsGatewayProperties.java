package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** SMS Gateway for Android (sms-gate.app) — identifiants uniquement via variables d'environnement. */
@ConfigurationProperties(prefix = "app.sms-gateway")
public record SmsGatewayProperties(
    String baseUrl,
    String username,
    String password,
    String token,
    String deviceId,
    String simNumber,
    boolean mockEnabled) {

  public boolean configured() {
    return present(token) || (present(username) && present(password));
  }

  public String apiBase() {
    String url = present(baseUrl) ? baseUrl.trim() : "https://api.sms-gate.app/3rdparty/v1";
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  public static boolean present(String v) { return v != null && !v.isBlank(); }
}
