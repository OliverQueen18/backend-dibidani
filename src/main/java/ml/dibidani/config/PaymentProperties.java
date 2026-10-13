package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Identifiants des opérateurs : uniquement via variables d'environnement, aucune valeur par défaut secrète. */
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(Mock mock, OrangeMoney orangeMoney, MoovMoney moovMoney) {

  public record Mock(boolean enabled, String webhookSecret) {}

  public record OrangeMoney(String baseUrl, String webpayPath, String clientId, String clientSecret, String merchantKey, String currency) {
    public boolean configured() { return present(baseUrl) && present(webpayPath) && present(clientId) && present(clientSecret) && present(merchantKey); }
  }

  public record MoovMoney(String baseUrl, String initiatePath, String merchantId, String apiKey, String webhookSecret) {
    public boolean configured() { return present(baseUrl) && present(initiatePath) && present(merchantId) && present(apiKey) && present(webhookSecret); }
  }

  static boolean present(String v) { return v != null && !v.isBlank(); }
}
