package ml.dibidani.domain;

public enum SocialChannel {
  FACEBOOK("Facebook"),
  TIKTOK("TikTok"),
  WHATSAPP("WhatsApp");

  private final String label;
  SocialChannel(String label) { this.label = label; }
  public String label() { return label; }
}
