package ml.dibidani.config;

import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Référentiel catalogue (catégories, marques, vendeurs, zones, publicités).
 * Tourne aussi en production : indépendant de {@code SEED_DEMO_DATA}.
 * Une fois le drapeau {@code catalog.reference.seeded} posé, plus rien n'est recréé
 * (une suppression définitive reste donc définitive).
 */
@Configuration
@ConditionalOnProperty(name = "app.catalog-seed.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder {

  static final String SEEDED_KEY = "catalog.reference.seeded";

  private record Cat(String name, String slug, String icon, boolean featured, String description) {}
  private record Sub(String parentSlug, String name, String slug) {}
  private record Zone(String commune, String district, int standard, Integer express, String standardTime, String expressTime) {}

  private static final List<Cat> CATEGORIES = List.of(
      new Cat("Pièces détachées", "pieces-detachees", "⚙️", true, "Pièces auto et moto : batteries, huiles, filtres, freinage."),
      new Cat("Quincaillerie", "quincaillerie", "🛠️", true, "Outillage, visserie, plomberie et matériaux."),
      new Cat("Alimentation", "alimentation", "🛒", true, "Produits alimentaires du marché livrés chez vous."),
      new Cat("Boissons & Eaux", "boissons-eaux", "🥤", false, "Eaux minérales, jus et boissons."),
      new Cat("Vêtements", "vetements", "👕", false, "Mode homme, femme et enfant."),
      new Cat("Électronique", "electronique", "📱", true, "Téléphones, accessoires, TV et audio."),
      new Cat("Électroménager", "electromenager", "🧺", false, "Réfrigérateurs, ventilateurs, petits appareils."),
      new Cat("Maison", "maison", "🏠", false, "Équipement et décoration de la maison."),
      new Cat("Énergie solaire", "energie-solaire", "☀️", false, "Panneaux, batteries solaires et équipements électriques."));

  private static final List<Sub> SUBCATEGORIES = List.of(
      new Sub("pieces-detachees", "Batteries automobiles", "batteries-automobiles"),
      new Sub("pieces-detachees", "Pièces moto", "pieces-moto"),
      new Sub("pieces-detachees", "Huiles et lubrifiants", "huiles-lubrifiants"),
      new Sub("electronique", "Téléphones", "telephones"),
      new Sub("electronique", "Accessoires", "accessoires-electronique"),
      new Sub("quincaillerie", "Outillage électroportatif", "outillage-electroportatif"),
      new Sub("energie-solaire", "Kits solaires", "kits-solaires"),
      new Sub("alimentation", "Céréales et riz", "cereales-riz"));

  private static final List<String> VENDORS = List.of(
      "Olive Services", "Auto Pièces Dibidani", "Auto Pièces Bamako",
      "Quincaillerie Dibidani", "Dibidani Distribution", "Electro Mali");

  private static final List<String> BRANDS = List.of(
      "Samsung", "Bosch", "TotalEnergies", "Tecno", "Infinix", "Générique", "Varta");

  private static final List<Zone> ZONES = List.of(
      new Zone("Commune III", "Dibidani", 1000, 2000, "Sous 24 h", "Sous 2 h"),
      new Zone("Commune III", "Badialan", 1000, 2000, "Sous 24 h", "Sous 2 h"),
      new Zone("Commune III", "Bamako-Coura", 1000, 2000, "Sous 24 h", "Sous 2 h"),
      new Zone("Commune III", "Dravéla", 1000, 2000, "Sous 24 h", "Sous 2 h"),
      new Zone("Commune III", "Point G", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune II", "Médina Coura", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune II", "Missira", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune II", "Hippodrome", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune II", "Niarela", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune I", "Korofina", 2000, 3000, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune I", "Djélibougou", 2000, 3000, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune I", "Banconi", 2000, 3500, "24 à 48 h", "Sous 4 h"),
      new Zone("Commune I", "Boulkassoumbougou", 2000, 3500, "24 à 48 h", "Sous 4 h"),
      new Zone("Commune IV", "Hamdallaye", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune IV", "Lafiabougou", 2000, 3000, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune IV", "Djicoroni Para", 2000, 3000, "Sous 24 h", "Sous 4 h"),
      new Zone("Commune IV", "Sébénikoro", 2500, 3500, "24 à 48 h", "Sous 4 h"),
      new Zone("Commune V", "Badalabougou", 1500, 2500, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune V", "Quartier Mali", 2000, 3000, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune V", "Kalaban Coura", 2000, 3500, "24 à 48 h", "Sous 4 h"),
      new Zone("Commune V", "Daoudabougou", 2000, 3000, "Sous 24 h", "Sous 4 h"),
      new Zone("Commune VI", "Sogoniko", 2000, 3000, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune VI", "Faladié", 2000, 3000, "Sous 24 h", "Sous 3 h"),
      new Zone("Commune VI", "Magnambougou", 2000, 3500, "24 à 48 h", "Sous 4 h"),
      new Zone("Commune VI", "Niamakoro", 2500, 3500, "24 à 48 h", "Sous 4 h"),
      new Zone("Commune VI", "Sénou", 3000, null, "24 à 48 h", null));

  @Bean
  @Order(-10)
  CommandLineRunner seedCatalog(CategoryRepository categories, BrandRepository brands, VendorRepository vendors,
                                DeliveryZoneRepository zones, AdvertisementRepository ads,
                                ShopSettingRepository settings, TransactionTemplate tx) {
    return args -> tx.executeWithoutResult(status -> {
      if (settings.findById(SEEDED_KEY).isPresent()) return;
      seedCategories(categories);
      seedVendors(vendors);
      seedBrands(brands);
      seedZones(zones);
      seedAds(ads);
      settings.save(new ShopSetting(SEEDED_KEY, "true"));
    });
  }

  private static void seedCategories(CategoryRepository categories) {
    int order = 0;
    for (Cat c : CATEGORIES) {
      int position = order++;
      var cat = categories.findBySlug(c.slug()).orElseGet(() -> {
        var n = new Category();
        n.setName(c.name());
        n.setSlug(c.slug());
        n.setIcon(c.icon());
        n.setDisplayOrder(position);
        return n;
      });
      if (cat.getDescription() == null) cat.setDescription(c.description());
      if (cat.getId() == null) cat.setFeatured(c.featured());
      categories.save(cat);
    }
    int subOrder = 0;
    for (Sub s : SUBCATEGORIES) {
      if (categories.existsBySlug(s.slug())) continue;
      var n = new Category();
      n.setName(s.name());
      n.setSlug(s.slug());
      n.setParent(categories.findBySlug(s.parentSlug()).orElseThrow());
      n.setDisplayOrder(subOrder++);
      categories.save(n);
    }
  }

  private static void seedVendors(VendorRepository vendors) {
    for (String name : VENDORS) {
      String slug = Slugs.slugify(name);
      if (vendors.existsBySlug(slug)) continue;
      var v = new Vendor();
      v.setName(name);
      v.setSlug(slug);
      v.setDistrict("Dibidani, Commune III");
      v.setCity("Bamako");
      v.setPhone("+223 76 12 34 56");
      v.setDescription(name + " est un commerçant référencé du marché de Dibidani, sélectionné pour la qualité de ses produits.");
      vendors.save(v);
    }
  }

  private static void seedBrands(BrandRepository brands) {
    for (String name : BRANDS) {
      if (brands.existsByNameIgnoreCase(name)) continue;
      var b = new Brand();
      b.setName(name);
      brands.save(b);
    }
  }

  private static void seedZones(DeliveryZoneRepository zones) {
    int order = 0;
    for (Zone z : ZONES) {
      int position = order++;
      if (zones.existsByCityIgnoreCaseAndCommuneIgnoreCaseAndDistrictIgnoreCase("Bamako", z.commune(), z.district())) continue;
      var n = new DeliveryZone();
      n.setCity("Bamako");
      n.setCommune(z.commune());
      n.setDistrict(z.district());
      n.setName(z.district() + " (" + z.commune() + ")");
      n.setStandardPrice(BigDecimal.valueOf(z.standard()));
      n.setExpressPrice(z.express() == null ? null : BigDecimal.valueOf(z.express()));
      n.setEstimatedStandardTime(z.standardTime());
      n.setEstimatedExpressTime(z.expressTime());
      n.setDisplayOrder(position);
      zones.save(n);
    }
  }

  private static void seedAds(AdvertisementRepository ads) {
    Instant now = Instant.now();
    if (!ads.existsByPosition(AdPosition.HOME_HERO)) {
      ads.save(ad("Le grand marché de Bamako, à portée de main", "Commandez les produits du marché de Dibidani et recevez-les chez vous, au bureau ou partout à Bamako.",
          "assets/hero-market.jpg", "/category/all", "Découvrir nos offres", AdPosition.HOME_HERO, AdType.SLIDER, 0, now));
      ads.save(ad("Pièces auto et moto au meilleur prix", "Batteries, huiles et filtres des meilleures marques, livrés en 24 h.",
          null, "/category/pieces-detachees", "Voir les pièces", AdPosition.HOME_HERO, AdType.SLIDER, 1, now));
      ads.save(ad("Passez à l'énergie solaire", "Kits solaires et équipements électriques pour la maison et le commerce.",
          null, "/category/energie-solaire", "Je m'équipe", AdPosition.HOME_HERO, AdType.SLIDER, 2, now));
    }
    seedAd(ads, AdPosition.HOME_TOP, ad("Votre publicité ici", "Touchez des milliers de clients chaque jour à Bamako.", null, "/category/all", "En savoir plus", AdPosition.HOME_TOP, AdType.BANNER, 0, now));
    seedAd(ads, AdPosition.HOME_MIDDLE, ad("Panneaux solaires pour un Mali plus lumineux", "Installation, garantie et livraison à Bamako.", null, "/category/energie-solaire", "Découvrir", AdPosition.HOME_MIDDLE, AdType.BANNER, 0, now));
    seedAd(ads, AdPosition.HOME_BOTTOM, ad("Des marques fiables, disponibles à Dibidani", "Samsung, Bosch, Varta, TotalEnergies et bien d'autres.", null, "/category/all", "Voir les offres", AdPosition.HOME_BOTTOM, AdType.BANNER, 0, now));
    seedAd(ads, AdPosition.CATEGORY_TOP, ad("Des offres spéciales au meilleur prix à Bamako", null, null, "/category/all?promo=true", "Voir les offres", AdPosition.CATEGORY_TOP, AdType.BANNER, 0, now));
    seedAd(ads, AdPosition.PRODUCT_SIDEBAR, ad("Livraison offerte dès 100 000 FCFA", "Sur toute la ville de Bamako.", null, "/category/all", "J'en profite", AdPosition.PRODUCT_SIDEBAR, AdType.CARD, 0, now));
    seedAd(ads, AdPosition.CART, ad("Des prix imbattables", "Profitez des promotions des commerçants de Dibidani.", null, "/category/all?promo=true", "Découvrir", AdPosition.CART, AdType.CARD, 0, now));
  }

  private static void seedAd(AdvertisementRepository ads, AdPosition position, Advertisement ad) {
    if (!ads.existsByPosition(position)) ads.save(ad);
  }

  private static Advertisement ad(String title, String description, String image, String target, String cta, AdPosition position, AdType type, int order, Instant now) {
    var a = new Advertisement();
    a.setTitle(title);
    a.setDescription(description);
    a.setImageUrl(image);
    a.setTargetUrl(target);
    a.setCtaLabel(cta);
    a.setPosition(position);
    a.setType(type);
    a.setDisplayOrder(order);
    a.setStartDate(now.minus(1, ChronoUnit.DAYS));
    a.setEndDate(now.plus(365, ChronoUnit.DAYS));
    return a;
  }
}
