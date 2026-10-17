package ml.dibidani.config;

import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Comptes, produits et réglages de démonstration. Désactivable via SEED_DEMO_DATA=false. Le référentiel catalogue est dans {@link CatalogSeeder}. */
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

  private record P(String sku, String name, String slug, String price, String promo, String category, String sub, String brand, String vendor,
                   int stock, boolean featured, boolean bestSeller, boolean newArrival, Map<String, String> specs, List<String[]> variants) {}

  private static final List<P> PRODUCTS = List.of(
      new P("DBD-EL-001", "Samsung Galaxy A54 5G", "samsung-galaxy-a54-5g", "180000", "165000", "electronique", "telephones", "Samsung", "Electro Mali", 25, true, true, true,
          Map.of("Écran", "6,4\" Super AMOLED", "Batterie", "5000 mAh", "Appareil photo", "50 MP"), List.of(new String[]{"128 Go - Noir", "", "12"}, new String[]{"256 Go - Noir", "195000", "6"})),
      new P("DBD-QC-001", "Perceuse Bosch 750W", "perceuse-bosch-750w", "75000", "65000", "quincaillerie", "outillage-electroportatif", "Bosch", "Quincaillerie Dibidani", 30, true, true, false, Map.of("Puissance", "750 W"), List.of()),
      new P("DBD-AL-001", "Riz local 50 kg", "riz-local-50kg", "25000", "23000", "alimentation", "cereales-riz", "Générique", "Dibidani Distribution", 35, true, true, false, Map.of("Poids", "50 kg"), List.of()),
      new P("DBD-BV-001", "Eau minérale pack 6", "eau-minerale-pack-6", "4000", "3500", "boissons-eaux", null, "Générique", "Dibidani Distribution", 40, true, true, false, Map.of(), List.of()),
      new P("DBD-PD-001", "Huile moteur Total 5W40", "huile-moteur-total-5w40", "28000", "25000", "pieces-detachees", "huiles-lubrifiants", "TotalEnergies", "Auto Pièces Dibidani", 45, true, false, false, Map.of("Viscosité", "5W40", "Contenance", "4 L"), List.of()),
      new P("DBD-EN-001", "Kit solaire domestique 1000W", "kit-solaire-1000w", "275000", "250000", "energie-solaire", "kits-solaires", "Générique", "Olive Services", 50, true, false, true, Map.of("Puissance", "1000 W"), List.of()),
      new P("DBD-VT-001", "Boubou homme premium", "boubou-homme-premium", "35000", "30000", "vetements", null, "Générique", "Olive Services", 55, false, false, true, Map.of(),
          List.of(new String[]{"Taille M", "", "10"}, new String[]{"Taille L", "", "10"}, new String[]{"Taille XL", "", "8"})),
      new P("DBD-MA-001", "Mixeur multifonction", "mixeur-multifonction", "45000", "39000", "maison", null, "Générique", "Electro Mali", 60, false, false, false, Map.of(), List.of()),
      new P("DBD-PD-002", "Batterie 70 Ah", "batterie-70ah-varta", "85000", "79000", "pieces-detachees", "batteries-automobiles", "Varta", "Auto Pièces Dibidani", 18, true, true, true,
          Map.of("Capacité", "70 Ah", "Tension", "12 V", "Garantie", "12 mois"), List.of()),
      new P("DBD-PD-003", "Batterie moto 12V 9Ah", "batterie-moto-12v-9ah", "18000", null, "pieces-detachees", "pieces-moto", "Varta", "Auto Pièces Dibidani", 4, false, false, true,
          Map.of("Capacité", "9 Ah", "Tension", "12 V"), List.of()),
      new P("DBD-EM-001", "Ventilateur sur pied 16\"", "ventilateur-sur-pied-16", "22000", "19500", "electromenager", null, "Générique", "Electro Mali", 20, false, true, true, Map.of("Diamètre", "40 cm"), List.of()));

  @Bean
  @Order(0)
  CommandLineRunner seed(AppUserRepository users, CategoryRepository categories, BrandRepository brands, VendorRepository vendors,
                         ProductRepository products, ShopSettingRepository shopSettings,
                         PasswordEncoder enc, TransactionTemplate tx) {
    return args -> tx.executeWithoutResult(status -> {
      if (users.findByEmailIgnoreCase("admin@dibidani.ml").isEmpty()) {
        users.save(user("Administrateur Dibidani", "admin@dibidani.ml", "+22370000000", enc.encode("Admin123!"), Role.ADMIN));
      }
      if (users.findByEmailIgnoreCase("client@dibidani.ml").isEmpty()) {
        users.save(user("Client Démo", "client@dibidani.ml", "+22371000000", enc.encode("Client123!"), Role.CUSTOMER));
      }
      if (users.findByEmailIgnoreCase("vendeur@dibidani.ml").isEmpty()) {
        users.save(user("Vendeur Démo", "vendeur@dibidani.ml", "+22372000000", enc.encode("Vendeur123!"), Role.SELLER));
      }
      if (users.findByEmailIgnoreCase("catalogue@dibidani.ml").isEmpty()) {
        users.save(user("Responsable catalogue", "catalogue@dibidani.ml", "+22373000000", enc.encode("Catalogue123!"), Role.CATALOG_MANAGER));
      }

      users.findByEmailIgnoreCase("vendeur@dibidani.ml").ifPresent(seller -> {
        if (vendors.findByOwnerId(seller.getId()).isPresent()) return;
        var shop = vendors.findBySlug("electro-mali").orElse(null);
        if (shop != null && shop.getOwner() == null) {
          shop.setOwner(seller);
          vendors.save(shop);
          return;
        }
        var demo = new Vendor();
        demo.setName("Boutique Démo Dibidani");
        demo.setSlug("boutique-demo-dibidani");
        demo.setDistrict("Dibidani, Commune III");
        demo.setCity("Bamako");
        demo.setPhone("+223 72 00 00 00");
        demo.setOwner(seller);
        demo.setDescription("Boutique de démonstration pour un compte vendeur.");
        vendors.save(demo);
      });

      for (P d : PRODUCTS) {
        if (products.findBySku(d.sku()).isPresent() || products.existsBySlug(d.slug())) continue;
        var p = new Product();
        p.setSku(d.sku());
        p.setName(d.name());
        p.setSlug(d.slug());
        p.setShortDescription(d.name() + " disponible au marché de Dibidani, livré à Bamako.");
        p.setDescription("Produit sélectionné auprès de vendeurs vérifiés du marché de Dibidani. Commandez en ligne, payez en Mobile Money ou à la livraison, et faites-vous livrer à Bamako.");
        p.setPrice(new BigDecimal(d.price()));
        p.setPromoPrice(d.promo() == null ? null : new BigDecimal(d.promo()));
        p.setStock(d.stock());
        p.setCategory(categories.findBySlug(d.category()).orElseThrow());
        p.setSubcategory(d.sub() == null ? null : categories.findBySlug(d.sub()).orElse(null));
        p.setBrand(brands.findAll().stream().filter(b -> b.getName().equalsIgnoreCase(d.brand())).findFirst().orElse(null));
        p.setVendor(vendors.findBySlug(Slugs.slugify(d.vendor())).orElse(null));
        p.setFeatured(d.featured());
        p.setBestSeller(d.bestSeller());
        p.setNewArrival(d.newArrival());
        p.setRating(4.5);
        p.setReviewCount(12 + d.stock() % 40);
        p.setMainImageUrl("assets/product-placeholder.svg");
        p.getImages().add("assets/product-placeholder.svg");
        p.getImages().add("assets/hero-market.jpg");
        d.specs().forEach((k, v) -> p.getSpecifications().add(new ProductSpecification(k, v)));
        int pos = 0;
        for (String[] vr : d.variants()) {
          var v = new ProductVariant();
          v.setProduct(p);
          v.setName(vr[0]);
          v.setPrice(vr[1].isBlank() ? null : new BigDecimal(vr[1]));
          v.setStock(Integer.parseInt(vr[2]));
          v.setPosition(pos++);
          p.getVariants().add(v);
        }
        products.save(p);
      }
      products.findAll().forEach(existing -> {
        if (!existing.getImages().isEmpty()) return;
        if (existing.getMainImageUrl() != null && !existing.getMainImageUrl().isBlank()) existing.getImages().add(existing.getMainImageUrl());
        if (!existing.getImages().contains("assets/hero-market.jpg")) existing.getImages().add("assets/hero-market.jpg");
        products.save(existing);
      });

      seedSetting(shopSettings, "facebookUrl", "https://www.facebook.com/dibidani");
      seedSetting(shopSettings, "tiktokUrl", "https://www.tiktok.com/@dibidani");
      seedSetting(shopSettings, "whatsapp", "+223 76 12 34 56");
    });
  }

  private static void seedSetting(ShopSettingRepository settings, String key, String value) {
    if (settings.findById(key).isEmpty()) settings.save(new ShopSetting(key, value));
  }

  private static AppUser user(String name, String email, String phone, String hash, Role role) {
    var u = new AppUser();
    u.setFullName(name);
    u.setEmail(email);
    u.setPhone(phone);
    u.setPasswordHash(hash);
    u.setRole(role);
    return u;
  }

}
