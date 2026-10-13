package ml.dibidani.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Jetons des réseaux sociaux : uniquement via variables d'environnement. */
@ConfigurationProperties(prefix = "app.social")
public record SocialProperties(boolean mockEnabled, Facebook facebook, Tiktok tiktok, Whatsapp whatsapp) {

  public record Facebook(String pageId, String pageAccessToken) {
    public boolean configured() { return present(pageId) && present(pageAccessToken); }
  }

  public record Tiktok(String accessToken) {
    public boolean configured() { return present(accessToken); }
  }

  public record Whatsapp(
      String cloudPhoneNumberId,
      String cloudAccessToken,
      String twilioAccountSid,
      String twilioAuthToken,
      String twilioFrom,
      String publishTo) {
    public boolean cloudConfigured() { return present(cloudPhoneNumberId) && present(cloudAccessToken); }
    public boolean twilioConfigured() { return present(twilioAccountSid) && present(twilioAuthToken) && present(twilioFrom); }
    public boolean configured() { return cloudConfigured() || twilioConfigured(); }
  }

  static boolean present(String v) { return v != null && !v.isBlank(); }
}
