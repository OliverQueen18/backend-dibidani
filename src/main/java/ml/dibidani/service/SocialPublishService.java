package ml.dibidani.service;

import ml.dibidani.api.dto.SocialDtos.ProductShare;
import ml.dibidani.api.dto.SocialDtos.SocialPostResponse;
import ml.dibidani.api.dto.SocialDtos.SocialStatus;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.config.SocialProperties;
import ml.dibidani.domain.*;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.SocialPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SocialPublishService {
  private static final Logger log = LoggerFactory.getLogger(SocialPublishService.class);

  private final ProductRepository products;
  private final SocialPostRepository posts;
  private final AppUserRepository users;
  private final ShopService shop;
  private final SocialProperties social;
  private final String publicUrl;
  private final RestClient http = RestClient.create();

  public SocialPublishService(ProductRepository products, SocialPostRepository posts, AppUserRepository users,
                              ShopService shop, SocialProperties social, @Value("${app.public-url}") String publicUrl) {
    this.products = products;
    this.posts = posts;
    this.users = users;
    this.shop = shop;
    this.social = social;
    this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/$", "");
  }

  public SocialStatus status() {
    return new SocialStatus(
        social.facebook() != null && social.facebook().configured(),
        social.tiktok() != null && social.tiktok().configured(),
        social.whatsapp() != null && social.whatsapp().configured(),
        social.mockEnabled());
  }

  public ProductShare share(String slug) {
    Product p = productBySlug(slug);
    return shareOf(p, caption(p, null));
  }

  public List<SocialPostResponse> history(Long productId) {
    findProduct(productId);
    return posts.findByProductIdOrderByCreatedAtDesc(productId).stream().map(SocialPostResponse::of).toList();
  }

  @Transactional
  public List<SocialPostResponse> publish(Long productId, List<SocialChannel> channels, String customMessage, String actorEmail) {
    Product product = findProduct(productId);
    AppUser actor = actorEmail == null ? null : users.findByEmailIgnoreCase(actorEmail).orElse(null);
    String caption = caption(product, customMessage);
    List<SocialPostResponse> out = new ArrayList<>();
    for (SocialChannel channel : channels) {
      if (channel == null) continue;
      out.add(SocialPostResponse.of(publishOne(product, channel, caption, actor)));
    }
    if (out.isEmpty()) throw new IllegalArgumentException("Choisissez au moins un réseau");
    return out;
  }

  private SocialPost publishOne(Product product, SocialChannel channel, String caption, AppUser actor) {
    var post = new SocialPost();
    post.setProduct(product);
    post.setChannel(channel);
    post.setMessage(caption);
    post.setProductUrl(productUrl(product));
    post.setPublishedBy(actor);
    try {
      switch (channel) {
        case FACEBOOK -> facebook(post, product, caption);
        case TIKTOK -> tiktok(post, product, caption);
        case WHATSAPP -> whatsapp(post, product, caption);
      }
    } catch (RuntimeException e) {
      log.warn("Publication {} échouée pour {} : {}", channel, product.getSlug(), e.getMessage());
      post.setStatus(SocialPostStatus.FAILED);
      post.setErrorMessage(trim(e.getMessage(), 1000));
    }
    return posts.save(post);
  }

  private void facebook(SocialPost post, Product product, String caption) {
    post.setShareUrl(facebookShareUrl(productUrl(product)));
    if (social.facebook() != null && social.facebook().configured()) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("message", caption);
      body.put("link", productUrl(product));
      body.put("access_token", social.facebook().pageAccessToken());
      JsonNode res = http.post()
          .uri("https://graph.facebook.com/v21.0/{pageId}/feed", social.facebook().pageId())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
      if (res == null) throw new RestClientException("Réponse Facebook vide");
      post.setExternalId(text(res, "id"));
      post.setStatus(SocialPostStatus.PUBLISHED);
      post.setPublishedAt(Instant.now());
      post.setMock(false);
      return;
    }
    fallback(post, "Facebook Page non configurée : ouverture du partage.");
  }

  private void tiktok(SocialPost post, Product product, String caption) {
    post.setShareUrl("https://www.tiktok.com/upload?lang=fr");
    if (social.tiktok() != null && social.tiktok().configured() && absoluteImage(product) != null) {
      Map<String, Object> postInfo = new LinkedHashMap<>();
      postInfo.put("title", trim(caption, 150));
      postInfo.put("privacy_level", "PUBLIC_TO_EVERYONE");
      Map<String, Object> source = new LinkedHashMap<>();
      source.put("source", "PULL_FROM_URL");
      source.put("photo_cover_index", 0);
      source.put("photo_images", List.of(absoluteImage(product)));
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("post_info", postInfo);
      body.put("source_info", source);
      body.put("post_mode", "DIRECT_POST");
      body.put("media_type", "PHOTO");
      JsonNode res = http.post()
          .uri("https://open.tiktokapis.com/v2/post/publish/content/init/")
          .contentType(MediaType.APPLICATION_JSON)
          .header("Authorization", "Bearer " + social.tiktok().accessToken())
          .body(body)
          .retrieve()
          .body(JsonNode.class);
      if (res == null) throw new RestClientException("Réponse TikTok vide");
      post.setExternalId(text(res.path("data"), "publish_id"));
      post.setStatus(SocialPostStatus.PUBLISHED);
      post.setPublishedAt(Instant.now());
      post.setMock(false);
      return;
    }
    fallback(post, "TikTok : copie du texte puis publication depuis le compte boutique.");
  }

  private void whatsapp(SocialPost post, Product product, String caption) {
    String shopWa = shop.get("whatsapp");
    post.setShareUrl(waMe(shopWa, caption));
    var wa = social.whatsapp();
    if (wa != null && wa.cloudConfigured() && present(wa.publishTo())) {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("messaging_product", "whatsapp");
      body.put("to", wa.publishTo().replaceAll("[^0-9]", ""));
      body.put("type", "text");
      body.put("text", Map.of("preview_url", true, "body", caption));
      JsonNode res = http.post()
          .uri("https://graph.facebook.com/v21.0/{id}/messages", wa.cloudPhoneNumberId())
          .header("Authorization", "Bearer " + wa.cloudAccessToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(JsonNode.class);
      if (res == null) throw new RestClientException("Réponse WhatsApp vide");
      JsonNode first = res.path("messages");
      if (first.isArray() && first.size() > 0) post.setExternalId(text(first.get(0), "id"));
      post.setStatus(SocialPostStatus.PUBLISHED);
      post.setPublishedAt(Instant.now());
      post.setMock(false);
      return;
    }
    if (wa != null && wa.twilioConfigured() && present(wa.publishTo())) {
      String sid = wa.twilioAccountSid();
      http.post()
          .uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", sid)
          .headers(h -> h.setBasicAuth(sid, wa.twilioAuthToken()))
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body("From=" + enc(wa.twilioFrom()) + "&To=" + enc(toWhatsApp(wa.publishTo())) + "&Body=" + enc(caption))
          .retrieve()
          .toBodilessEntity();
      post.setStatus(SocialPostStatus.PUBLISHED);
      post.setPublishedAt(Instant.now());
      post.setMock(false);
      return;
    }
    fallback(post, "WhatsApp : envoi depuis le compte boutique via le lien prérempli.");
  }

  private void fallback(SocialPost post, String note) {
    if (!social.mockEnabled()) {
      throw new IllegalStateException(note + " Ajoutez les jetons API du réseau.");
    }
    post.setStatus(SocialPostStatus.SHARED);
    post.setPublishedAt(Instant.now());
    post.setMock(true);
    post.setErrorMessage(note);
  }

  public String caption(Product p, String custom) {
    if (present(custom)) return trim(custom, 1500);
    String price = money(p.getEffectivePrice());
    StringBuilder b = new StringBuilder();
    b.append(p.getName()).append('\n');
    if (p.isOnPromotion()) b.append("🔥 Promo ").append(price).append(" FCFA (au lieu de ").append(money(p.getPrice())).append(")\n");
    else b.append(price).append(" FCFA\n");
    if (present(p.getShortDescription())) b.append(p.getShortDescription()).append('\n');
    b.append("Livraison à Bamako · Dibidani\n");
    b.append(productUrl(p)).append('\n');
    b.append("#Dibidani #Bamako #Mali");
    return trim(b.toString(), 1500);
  }

  private ProductShare shareOf(Product p, String caption) {
    String url = productUrl(p);
    String order = "Bonjour Dibidani, je souhaite commander : " + p.getName() + " (" + money(p.getEffectivePrice()) + " FCFA). " + url;
    return new ProductShare(caption, url, waMe(null, caption), facebookShareUrl(url), "https://www.tiktok.com/upload?lang=fr", waMe(shop.get("whatsapp"), order));
  }

  private Product findProduct(Long id) {
    return products.findById(id).orElseThrow(() -> new NotFoundException("Produit introuvable"));
  }

  private Product productBySlug(String slug) {
    return products.findBySlugAndActiveTrue(slug).orElseThrow(() -> new NotFoundException("Produit introuvable"));
  }

  private String productUrl(Product p) { return publicUrl + "/product/" + p.getSlug(); }

  private String absoluteImage(Product p) {
    String img = p.getMainImageUrl();
    if (!present(img) || img.startsWith("assets/")) return null;
    if (img.startsWith("http://") || img.startsWith("https://")) return img;
    if (img.startsWith("/")) return publicUrl + img;
    return publicUrl + "/" + img;
  }

  private static String facebookShareUrl(String url) {
    return "https://www.facebook.com/sharer/sharer.php?u=" + enc(url);
  }

  private static String waMe(String phone, String text) {
    String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
    String base = digits.isEmpty() ? "https://wa.me/" : "https://wa.me/" + digits;
    return base + "?text=" + enc(text);
  }

  private static String toWhatsApp(String n) {
    String v = n.trim();
    if (v.startsWith("whatsapp:")) return v;
    String digits = v.replaceAll("[^0-9+]", "");
    if (!digits.startsWith("+")) digits = "+" + digits.replaceFirst("^00", "");
    return "whatsapp:" + digits;
  }

  private static String money(BigDecimal v) {
    if (v == null) return "0";
    return String.format(Locale.FRANCE, "%,.0f", v);
  }

  private static String enc(String v) {
    return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static boolean present(String v) { return v != null && !v.isBlank(); }

  private static String trim(String v, int max) {
    if (v == null) return "";
    return v.length() <= max ? v : v.substring(0, max);
  }

  private static String text(JsonNode n, String field) {
    if (n == null) return null;
    String v = n.path(field).asString(null);
    return v == null || v.isBlank() ? null : v;
  }
}
