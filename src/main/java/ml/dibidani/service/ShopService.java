package ml.dibidani.service;

import ml.dibidani.api.dto.HomeDtos.NewsletterResponse;
import ml.dibidani.api.dto.HomeDtos.SettingsResponse;
import ml.dibidani.config.ShopProperties;
import ml.dibidani.domain.NewsletterSubscriber;
import ml.dibidani.domain.ShopSetting;
import ml.dibidani.repository.NewsletterSubscriberRepository;
import ml.dibidani.repository.ShopSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/** Paramètres boutique : valeurs app.shop.* surchargées par la table shop_settings (back-office). */
@Service
public class ShopService {
  /** Clés modifiables depuis le back-office. */
  public static final List<String> KEYS = List.of(
      "name", "slogan", "phone", "whatsapp", "email", "address", "city", "openingHours",
      "deliveryFee", "freeDeliveryThreshold", "pickupAddress", "pickupHours", "bankTransferDetails",
      "facebookUrl", "instagramUrl", "tiktokUrl", "youtubeUrl", "linkedinUrl",
      "logoUrl", "topbarText", "heroLocation", "heroLocationSub", "floatingBanner", "navPromoLabel",
      "footerAbout", "footerCopyright", "testimonialsTitle", "testimonialsSubtitle",
      "partnersTitle", "partnersSubtitle");
  private static final Set<String> NUMERIC = Set.of("deliveryFee", "freeDeliveryThreshold");

  private final ShopProperties shop;
  private final NewsletterSubscriberRepository subscribers;
  private final ShopSettingRepository settings;
  private final AtomicReference<Map<String, String>> cache = new AtomicReference<>();

  public ShopService(ShopProperties shop, NewsletterSubscriberRepository subscribers, ShopSettingRepository settings) {
    this.shop = shop;
    this.subscribers = subscribers;
    this.settings = settings;
  }

  public SettingsResponse settings() {
    String whatsapp = get("whatsapp");
    String whatsappUrl = whatsapp == null ? null : "https://wa.me/" + whatsapp.replaceAll("[^0-9]", "");
    return new SettingsResponse(get("name"), get("slogan"), get("phone"), whatsapp, whatsappUrl, get("email"),
        get("address"), get("city"), get("openingHours"), deliveryFee(), freeDeliveryThreshold(),
        get("facebookUrl"), get("instagramUrl"), get("tiktokUrl"), get("youtubeUrl"), get("linkedinUrl"),
        pickupAddress(), pickupHours(), get("bankTransferDetails"),
        get("logoUrl"), get("topbarText"), get("heroLocation"), get("heroLocationSub"), get("floatingBanner"),
        get("navPromoLabel"), get("footerAbout"), get("footerCopyright"),
        get("testimonialsTitle"), get("testimonialsSubtitle"), get("partnersTitle"), get("partnersSubtitle"));
  }

  public BigDecimal deliveryFee() { return decimal("deliveryFee", BigDecimal.ZERO); }
  public BigDecimal freeDeliveryThreshold() { return decimal("freeDeliveryThreshold", null); }
  public String pickupAddress() { return Objects.requireNonNullElseGet(get("pickupAddress"), () -> get("address")); }
  public String pickupHours() { return Objects.requireNonNullElseGet(get("pickupHours"), () -> get("openingHours")); }
  public String name() { return Objects.requireNonNullElse(get("name"), "Dibidani"); }

  /** Frais forfaitaires des commandes passées sans quartier (ancienne API /orders). */
  public BigDecimal deliveryFeeFor(BigDecimal subtotal) {
    BigDecimal threshold = freeDeliveryThreshold();
    return threshold != null && subtotal.compareTo(threshold) >= 0 ? BigDecimal.ZERO : deliveryFee();
  }

  public String get(String key) {
    String v = values().get(key);
    return v == null || v.isBlank() ? null : v.trim();
  }

  /** Valeurs effectives de toutes les clés éditables. */
  public Map<String, String> editable() {
    Map<String, String> out = new LinkedHashMap<>();
    for (String k : KEYS) out.put(k, get(k));
    return out;
  }

  @Transactional
  public Map<String, String> update(Map<String, String> changes) {
    for (var e : changes.entrySet()) {
      String key = e.getKey();
      if (!KEYS.contains(key)) throw new IllegalArgumentException("Paramètre inconnu : " + key);
      String value = e.getValue() == null ? null : e.getValue().trim();
      if (value != null && value.length() > 8000) throw new IllegalArgumentException("Valeur trop longue pour " + key);
      if (NUMERIC.contains(key) && value != null && !value.isEmpty()) {
        try {
          if (new BigDecimal(value).signum() < 0) throw new IllegalArgumentException("Le montant ne peut pas être négatif : " + key);
        } catch (NumberFormatException ex) {
          throw new IllegalArgumentException("Montant invalide pour " + key);
        }
      }
      if (key.equals("name") && (value == null || value.isEmpty())) throw new IllegalArgumentException("Le nom de la boutique est obligatoire");
      settings.findById(key).ifPresentOrElse(s -> s.setValue(value), () -> settings.save(new ShopSetting(key, value)));
    }
    cache.set(null);
    return editable();
  }

  private Map<String, String> values() {
    Map<String, String> v = cache.get();
    if (v != null) return v;
    Map<String, String> merged = new HashMap<>(defaults());
    settings.findAll().forEach(s -> merged.put(s.getKey(), s.getValue()));
    cache.set(merged);
    return merged;
  }

  private Map<String, String> defaults() {
    Map<String, String> d = new HashMap<>();
    d.put("name", shop.name());
    d.put("slogan", shop.slogan());
    d.put("phone", shop.phone());
    d.put("whatsapp", shop.whatsapp());
    d.put("email", shop.email());
    d.put("address", shop.address());
    d.put("city", shop.city());
    d.put("openingHours", shop.openingHours());
    d.put("deliveryFee", shop.deliveryFee() == null ? null : shop.deliveryFee().toPlainString());
    d.put("freeDeliveryThreshold", shop.freeDeliveryThreshold() == null ? null : shop.freeDeliveryThreshold().toPlainString());
    d.put("facebookUrl", shop.facebookUrl());
    d.put("instagramUrl", shop.instagramUrl());
    d.put("tiktokUrl", shop.tiktokUrl());
    return d.entrySet().stream().filter(e -> e.getValue() != null).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private BigDecimal decimal(String key, BigDecimal fallback) {
    String v = get(key);
    if (v == null) return fallback;
    try { return new BigDecimal(v); } catch (NumberFormatException e) { return fallback; }
  }

  @Transactional
  public NewsletterResponse subscribe(String rawEmail) {
    String email = rawEmail.trim().toLowerCase(Locale.ROOT);
    var existing = subscribers.findByEmailIgnoreCase(email);
    if (existing.isPresent()) {
      existing.get().setActive(true);
      return new NewsletterResponse("Vous êtes déjà inscrit à la newsletter Dibidani.", true);
    }
    var s = new NewsletterSubscriber();
    s.setEmail(email);
    subscribers.save(s);
    return new NewsletterResponse("Merci ! Vous recevrez nos meilleures offres.", false);
  }
}
