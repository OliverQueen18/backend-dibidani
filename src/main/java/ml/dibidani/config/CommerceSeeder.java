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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Données de démonstration du parcours d'achat : quartiers de Bamako, véhicules, compatibilités, code promo, livreur. Idempotent. */
@Configuration
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CommerceSeeder {

  private record Zone(String commune, String district, int standard, Integer express, String standardTime, String expressTime) {}
  private record Engine(String brand, String model, VehicleType type, String name, FuelType fuel, Integer cc, Integer hp, int from, Integer to) {}
  private record Compat(String sku, String brand, String model, String engine, Integer from, Integer to, String notes) {}

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

  private static final List<Engine> ENGINES = List.of(
      new Engine("Toyota", "Corolla", VehicleType.CAR, "1.8 VVT-i", FuelType.PETROL, 1798, 140, 2014, 2019),
      new Engine("Toyota", "Corolla", VehicleType.CAR, "1.6 Valvematic", FuelType.PETROL, 1598, 132, 2014, 2019),
      new Engine("Toyota", "Hilux", VehicleType.TRUCK, "2.5 D-4D", FuelType.DIESEL, 2494, 144, 2005, 2015),
      new Engine("Toyota", "Hilux", VehicleType.TRUCK, "2.4 D-4D", FuelType.DIESEL, 2393, 150, 2016, null),
      new Engine("Toyota", "RAV4", VehicleType.CAR, "2.0 VVT-i", FuelType.PETROL, 1987, 152, 2013, 2018),
      new Engine("Peugeot", "206", VehicleType.CAR, "1.4 HDi", FuelType.DIESEL, 1398, 68, 2001, 2012),
      new Engine("Mercedes-Benz", "Classe C (W204)", VehicleType.CAR, "C 200 CDI", FuelType.DIESEL, 2148, 136, 2007, 2014),
      new Engine("Hyundai", "Tucson", VehicleType.CAR, "2.0 CRDi", FuelType.DIESEL, 1995, 136, 2015, 2020),
      new Engine("Honda", "CG 125", VehicleType.MOTORCYCLE, "125 cm³", FuelType.PETROL, 124, 11, 2005, null),
      new Engine("Jakarta", "JK 125", VehicleType.MOTORCYCLE, "125 cm³", FuelType.PETROL, 125, 10, 2010, null),
      new Engine("Yamaha", "Crypton", VehicleType.MOTORCYCLE, "110 cm³", FuelType.PETROL, 113, 9, 2008, null));

  private static final List<Compat> COMPATIBILITIES = List.of(
      new Compat("DBD-PD-004", "Toyota", "Corolla", "1.8 VVT-i", 2015, 2017, "Essieu avant"),
      new Compat("DBD-PD-004", "Toyota", "Corolla", "1.6 Valvematic", 2015, 2017, "Essieu avant"),
      new Compat("DBD-PD-002", "Toyota", "Corolla", "1.8 VVT-i", null, null, null),
      new Compat("DBD-PD-002", "Toyota", "RAV4", "2.0 VVT-i", null, null, null),
      new Compat("DBD-PD-002", "Hyundai", "Tucson", "2.0 CRDi", null, null, null),
      new Compat("DBD-PD-002", "Peugeot", "206", "1.4 HDi", null, null, "Vérifier l'encombrement du bac"),
      new Compat("DBD-PD-001", "Toyota", "Corolla", "1.8 VVT-i", null, null, null),
      new Compat("DBD-PD-001", "Toyota", "Corolla", "1.6 Valvematic", null, null, null),
      new Compat("DBD-PD-001", "Toyota", "RAV4", "2.0 VVT-i", null, null, null),
      new Compat("DBD-PD-001", "Mercedes-Benz", "Classe C (W204)", "C 200 CDI", null, null, null),
      new Compat("DBD-PD-003", "Honda", "CG 125", "125 cm³", null, null, null),
      new Compat("DBD-PD-003", "Jakarta", "JK 125", "125 cm³", null, null, null),
      new Compat("DBD-PD-003", "Yamaha", "Crypton", "110 cm³", null, null, null));

  @Bean
  @Order(10)
  CommandLineRunner seedCommerce(DeliveryZoneRepository zones, VehicleBrandRepository vehicleBrands, VehicleModelRepository vehicleModels,
                                 VehicleEngineRepository vehicleEngines, VehicleCompatibilityRepository compatibilities, ProductRepository products,
                                 CategoryRepository categories, BrandRepository brands, VendorRepository vendors, PromoCodeRepository promos,
                                 AppUserRepository users, PasswordEncoder enc, TransactionTemplate tx) {
    return args -> tx.executeWithoutResult(status -> {
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

      if (users.findByEmailIgnoreCase("livreur@dibidani.ml").isEmpty()) {
        var u = new AppUser();
        u.setFullName("Moussa Livreur");
        u.setEmail("livreur@dibidani.ml");
        u.setPhone("+22372000000");
        u.setPasswordHash(enc.encode("Livreur123!"));
        u.setRole(Role.COURIER);
        users.save(u);
      }

      if (!promos.existsByCodeIgnoreCase("BIENVENUE10")) {
        var p = new PromoCode();
        p.setCode("BIENVENUE10");
        p.setDescription("10 % de remise sur votre commande (plafonnée à 10 000 FCFA)");
        p.setType(PromoType.PERCENTAGE);
        p.setValue(BigDecimal.TEN);
        p.setMinSubtotal(BigDecimal.valueOf(10000));
        p.setMaxDiscount(BigDecimal.valueOf(10000));
        p.setStartDate(Instant.now().minus(1, ChronoUnit.DAYS));
        p.setEndDate(Instant.now().plus(365, ChronoUnit.DAYS));
        promos.save(p);
      }
      if (!promos.existsByCodeIgnoreCase("LIVRAISONOFFERTE")) {
        var p = new PromoCode();
        p.setCode("LIVRAISONOFFERTE");
        p.setDescription("Livraison standard offerte");
        p.setType(PromoType.FREE_DELIVERY);
        p.setValue(BigDecimal.ZERO);
        p.setMinSubtotal(BigDecimal.valueOf(20000));
        promos.save(p);
      }

      seedBrakePads(products, categories, brands, vendors);
      setReference(products, "DBD-PD-001", "08880-83389", "TOT-QUARTZ-9000-5W40-4L");
      setReference(products, "DBD-PD-002", "28800-0D150", "VARTA-E11-570144064");
      setReference(products, "DBD-PD-003", "31500-KTE-901", "VARTA-YTX9-BS");

      Map<String, VehicleEngine> engineIndex = new HashMap<>();
      Map<String, VehicleBrand> brandIndex = new HashMap<>();
      Map<String, VehicleModel> modelIndex = new HashMap<>();
      if (vehicleBrands.count() > 0) return;
      int brandOrder = 0;
      for (Engine e : ENGINES) {
        int bo = brandOrder;
        VehicleBrand brand = brandIndex.computeIfAbsent(e.brand(), name -> {
          var b = new VehicleBrand();
          b.setName(name);
          b.setSlug(Slugs.slugify(name));
          b.setDisplayOrder(bo);
          return vehicleBrands.save(b);
        });
        brandOrder++;
        VehicleModel model = modelIndex.computeIfAbsent(e.brand() + "|" + e.model(), k -> {
          var m = new VehicleModel();
          m.setBrand(brand);
          m.setName(e.model());
          m.setSlug(Slugs.slugify(e.model()));
          m.setVehicleType(e.type());
          return vehicleModels.save(m);
        });
        var engine = new VehicleEngine();
        engine.setModel(model);
        engine.setName(e.name());
        engine.setFuelType(e.fuel());
        engine.setDisplacementCc(e.cc());
        engine.setPowerHp(e.hp());
        engine.setYearFrom(e.from());
        engine.setYearTo(e.to());
        engineIndex.put(e.brand() + "|" + e.model() + "|" + e.name(), vehicleEngines.save(engine));
      }
      for (Compat c : COMPATIBILITIES) {
        var product = products.findBySku(c.sku()).orElse(null);
        var engine = engineIndex.get(c.brand() + "|" + c.model() + "|" + c.engine());
        if (product == null || engine == null) continue;
        var vc = new VehicleCompatibility();
        vc.setProduct(product);
        vc.setEngine(engine);
        vc.setYearFrom(c.from());
        vc.setYearTo(c.to());
        vc.setNotes(c.notes());
        compatibilities.save(vc);
      }
    });
  }

  private static void setReference(ProductRepository products, String sku, String oem, String manufacturer) {
    products.findBySku(sku).ifPresent(p -> {
      if (p.getOemReference() == null) p.setOemReference(oem);
      if (p.getManufacturerReference() == null) p.setManufacturerReference(manufacturer);
    });
  }

  private static void seedBrakePads(ProductRepository products, CategoryRepository categories, BrandRepository brands, VendorRepository vendors) {
    if (products.findBySku("DBD-PD-004").isPresent() || products.existsBySlug("plaquettes-frein-avant-bosch-bp102")) return;
    var category = categories.findBySlug("pieces-detachees").orElse(null);
    if (category == null) return;
    var p = new Product();
    p.setSku("DBD-PD-004");
    p.setName("Plaquettes de frein avant Bosch BP102");
    p.setSlug("plaquettes-frein-avant-bosch-bp102");
    p.setShortDescription("Jeu de 4 plaquettes avant, montage sur Toyota Corolla 2015-2017.");
    p.setDescription("Plaquettes de frein avant Bosch de qualité d'origine. Vérifiez la compatibilité de votre véhicule avant de commander.");
    p.setPrice(BigDecimal.valueOf(32000));
    p.setPromoPrice(BigDecimal.valueOf(29000));
    p.setStock(14);
    p.setUnit("jeu");
    p.setCategory(category);
    p.setBrand(brands.findAll().stream().filter(b -> b.getName().equalsIgnoreCase("Bosch")).findFirst().orElse(null));
    p.setVendor(vendors.findBySlug(Slugs.slugify("Auto Pièces Dibidani")).orElse(null));
    p.setOemReference("04465-02220");
    p.setManufacturerReference("BOSCH 0 986 BP1 02");
    p.setFeatured(true);
    p.setNewArrival(true);
    p.setRating(4.6);
    p.setReviewCount(9);
    p.setMainImageUrl("assets/product-placeholder.svg");
    p.getSpecifications().add(new ProductSpecification("Position", "Essieu avant"));
    p.getSpecifications().add(new ProductSpecification("Contenu", "4 plaquettes"));
    products.save(p);
  }
}
