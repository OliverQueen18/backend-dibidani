package ml.dibidani.service;

import jakarta.persistence.criteria.*;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.VehicleDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import ml.dibidani.search.ProductFilter;
import ml.dibidani.search.ProductSpecs;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

/** Parcours « Trouver ma pièce » : type → marque → modèle → année → motorisation → catégorie → produits compatibles. */
@Service
@Transactional(readOnly = true)
public class VehicleService {
  private final VehicleBrandRepository brands;
  private final VehicleModelRepository models;
  private final VehicleEngineRepository engines;
  private final VehicleCompatibilityRepository compatibilities;
  private final ProductRepository products;

  public VehicleService(VehicleBrandRepository brands, VehicleModelRepository models, VehicleEngineRepository engines,
                        VehicleCompatibilityRepository compatibilities, ProductRepository products) {
    this.brands = brands;
    this.models = models;
    this.engines = engines;
    this.compatibilities = compatibilities;
    this.products = products;
  }

  public List<TypeOption> types() { return Arrays.stream(VehicleType.values()).map(t -> new TypeOption(t, t.label())).toList(); }

  public List<BrandResponse> brands(VehicleType type) {
    var list = type == null ? brands.findAllByOrderByDisplayOrderAscNameAsc().stream().filter(VehicleBrand::isActive).toList() : brands.findActiveByType(type);
    return list.stream().map(BrandResponse::of).toList();
  }

  public List<ModelResponse> models(Long brandId, VehicleType type) {
    var list = type == null ? models.findByBrandIdAndActiveTrueOrderByNameAsc(brandId) : models.findByBrandIdAndVehicleTypeAndActiveTrueOrderByNameAsc(brandId, type);
    return list.stream().map(ModelResponse::of).toList();
  }

  /** Années couvertes par au moins une motorisation active, de la plus récente à la plus ancienne. */
  public List<Integer> years(Long modelId) {
    int current = java.time.Year.now().getValue();
    TreeSet<Integer> years = new TreeSet<>(Comparator.reverseOrder());
    for (VehicleEngine e : engines.findByModelIdAndActiveTrueOrderByYearFromAscNameAsc(modelId)) {
      int to = e.getYearTo() == null ? current : Math.min(e.getYearTo(), current + 1);
      for (int y = e.getYearFrom(); y <= to; y++) years.add(y);
    }
    return new ArrayList<>(years);
  }

  public List<EngineResponse> engines(Long modelId, Integer year) {
    return engines.findByModelIdAndActiveTrueOrderByYearFromAscNameAsc(modelId).stream()
        .filter(e -> year == null || e.coversYear(year)).map(EngineResponse::of).toList();
  }

  /** Catégories de pièces ayant au moins un produit compatible, avec leur nombre de produits. */
  public List<PartCategory> partCategories(Long modelId, Long engineId, Integer year) {
    Specification<Product> spec = compatible(modelId, engineId, year).and((root, q, cb) -> cb.isTrue(root.get("active")));
    Map<Long, PartCategory> out = new LinkedHashMap<>();
    for (Product p : products.findAll(spec, PageRequest.of(0, 2000)).getContent()) {
      Category c = p.getSubcategory() != null ? p.getSubcategory() : p.getCategory();
      out.merge(c.getId(), new PartCategory(c.getId(), c.getName(), c.getSlug(), c.getIcon(), 1),
          (a, b) -> new PartCategory(a.id(), a.name(), a.slug(), a.icon(), a.productCount() + 1));
    }
    return out.values().stream().sorted(Comparator.comparing(PartCategory::productCount).reversed().thenComparing(PartCategory::name)).toList();
  }

  public PageResponse<ProductSummary> compatibleProducts(Long modelId, Long engineId, Integer year, String category, int page, int size) {
    if (modelId == null && engineId == null) throw new IllegalArgumentException("Choisissez au moins un modèle de véhicule");
    var filter = new ProductFilter(null, category, List.of(), List.of(), null, null, null, null, null, null, null, false, null);
    Specification<Product> spec = ProductSpecs.of(filter, false).and(compatible(modelId, engineId, year));
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 60)), Sort.by(Sort.Order.desc("bestSeller"), Sort.Order.asc("name")));
    return PageResponse.of(products.findAll(spec, pageable).map(ProductSummary::of));
  }

  /** exists(compatibilité sur la motorisation — ou sur n'importe quelle motorisation du modèle — couvrant l'année). */
  private static Specification<Product> compatible(Long modelId, Long engineId, Integer year) {
    return (root, query, cb) -> {
      Subquery<Long> sq = query.subquery(Long.class);
      Root<VehicleCompatibility> c = sq.from(VehicleCompatibility.class);
      Join<VehicleCompatibility, VehicleEngine> e = c.join("engine");
      List<Predicate> and = new ArrayList<>();
      and.add(cb.equal(c.get("product"), root));
      and.add(cb.isTrue(e.get("active")));
      if (engineId != null) and.add(cb.equal(e.get("id"), engineId));
      else and.add(cb.equal(e.get("model").get("id"), modelId));
      if (year != null) {
        Expression<Integer> from = cb.coalesce(c.get("yearFrom"), e.get("yearFrom"));
        Expression<Integer> to = cb.coalesce(c.get("yearTo"), cb.coalesce(e.<Integer>get("yearTo"), 9999));
        and.add(cb.lessThanOrEqualTo(from, year));
        and.add(cb.greaterThanOrEqualTo(to, year));
      }
      sq.select(c.get("id")).where(and.toArray(Predicate[]::new));
      return cb.exists(sq);
    };
  }

  public List<CompatibilityResponse> forProduct(Long productId) {
    return compatibilities.findByProductIdOrderByIdAsc(productId).stream()
        .filter(c -> c.getEngine().isActive())
        .map(CompatibilityResponse::of)
        .sorted(Comparator.comparing(CompatibilityResponse::label)).toList();
  }

  /** Remplace les compatibilités d'un produit par celles de la requête (édition depuis la fiche produit). */
  @Transactional
  public void replaceCompatibilities(Product p, List<CompatibilityRequest> requested) {
    if (requested == null) return;
    var existing = compatibilities.findByProductIdOrderByIdAsc(p.getId()).stream()
        .collect(Collectors.toMap(c -> c.getEngine().getId(), c -> c, (a, b) -> a));
    Set<Long> kept = new HashSet<>();
    for (CompatibilityRequest r : requested) {
      if (!kept.add(r.engineId())) continue;
      var c = existing.get(r.engineId());
      if (c == null) {
        c = new VehicleCompatibility();
        c.setProduct(p);
        c.setEngine(engines.findById(r.engineId()).orElseThrow(() -> new IllegalArgumentException("Motorisation introuvable : " + r.engineId())));
      }
      c.setYearFrom(r.yearFrom());
      c.setYearTo(r.yearTo());
      c.setNotes(Slugs.blankToNull(r.notes()));
      compatibilities.save(c);
    }
    existing.values().stream().filter(c -> !kept.contains(c.getEngine().getId())).forEach(compatibilities::delete);
  }

  // ---------- Back-office ----------

  public List<BrandResponse> adminBrands() { return brands.findAllByOrderByDisplayOrderAscNameAsc().stream().map(BrandResponse::of).toList(); }

  public List<ModelResponse> adminModels(Long brandId) {
    return (brandId == null ? models.findAllByOrderByNameAsc() : models.findByBrandIdOrderByNameAsc(brandId)).stream().map(ModelResponse::of).toList();
  }

  public List<EngineResponse> adminEngines(Long modelId) {
    if (modelId == null) return engines.findAll(Sort.by("model.name", "yearFrom")).stream().map(EngineResponse::of).toList();
    return engines.findByModelIdOrderByYearFromAscNameAsc(modelId).stream().map(EngineResponse::of).toList();
  }

  @Transactional
  public BrandResponse saveBrand(Long id, BrandRequest r) {
    VehicleBrand b = id == null ? new VehicleBrand() : brands.findById(id).orElseThrow(() -> new NotFoundException("Marque introuvable"));
    String slug = Slugs.resolve(r.slug(), r.name(), s -> id == null ? brands.existsBySlug(s) : brands.existsBySlugAndIdNot(s, id));
    if (id == null ? brands.existsBySlug(slug) : brands.existsBySlugAndIdNot(slug, id)) throw new ConflictException("Cette marque existe déjà");
    b.setName(r.name().trim());
    b.setSlug(slug);
    b.setLogoUrl(Slugs.blankToNull(r.logoUrl()));
    b.setActive(r.active() == null || r.active());
    b.setDisplayOrder(r.displayOrder() == null ? 0 : r.displayOrder());
    return BrandResponse.of(brands.save(b));
  }

  @Transactional
  public ModelResponse saveModel(Long id, ModelRequest r) {
    VehicleModel m = id == null ? new VehicleModel() : models.findById(id).orElseThrow(() -> new NotFoundException("Modèle introuvable"));
    var brand = brands.findById(r.brandId()).orElseThrow(() -> new IllegalArgumentException("Marque introuvable"));
    String slug = Slugs.slugify(r.name());
    if (id == null ? models.existsByBrandIdAndSlug(brand.getId(), slug) : models.existsByBrandIdAndSlugAndIdNot(brand.getId(), slug, id)) {
      throw new ConflictException("Ce modèle existe déjà pour " + brand.getName());
    }
    m.setBrand(brand);
    m.setName(r.name().trim());
    m.setSlug(slug);
    m.setVehicleType(r.vehicleType());
    m.setActive(r.active() == null || r.active());
    return ModelResponse.of(models.save(m));
  }

  @Transactional
  public EngineResponse saveEngine(Long id, EngineRequest r) {
    VehicleEngine e = id == null ? new VehicleEngine() : engines.findById(id).orElseThrow(() -> new NotFoundException("Motorisation introuvable"));
    e.setModel(models.findById(r.modelId()).orElseThrow(() -> new IllegalArgumentException("Modèle introuvable")));
    e.setName(r.name().trim());
    e.setFuelType(r.fuelType());
    e.setDisplacementCc(r.displacementCc());
    e.setPowerHp(r.powerHp());
    e.setYearFrom(r.yearFrom());
    e.setYearTo(r.yearTo());
    e.setActive(r.active() == null || r.active());
    return EngineResponse.of(engines.save(e));
  }

  /** Désactivation logique : les compatibilités et l'historique restent intacts. */
  @Transactional
  public void deactivate(String kind, Long id) {
    switch (kind) {
      case "brands" -> brands.findById(id).orElseThrow(() -> new NotFoundException("Marque introuvable")).setActive(false);
      case "models" -> models.findById(id).orElseThrow(() -> new NotFoundException("Modèle introuvable")).setActive(false);
      case "engines" -> engines.findById(id).orElseThrow(() -> new NotFoundException("Motorisation introuvable")).setActive(false);
      default -> throw new NotFoundException("Ressource inconnue");
    }
  }
}
