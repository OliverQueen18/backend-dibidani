package ml.dibidani.service;

import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductDetail;
import ml.dibidani.api.dto.ProductDtos.ProductRequest;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.ProductDtos.VariantRequest;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.Category;
import ml.dibidani.domain.Product;
import ml.dibidani.domain.ProductSpecification;
import ml.dibidani.domain.ProductVariant;
import ml.dibidani.domain.Vendor;
import ml.dibidani.repository.CategoryRepository;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.ProductVariantRepository;
import ml.dibidani.search.ProductFilter;
import ml.dibidani.search.SearchProvider;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProductService {
  public static final int MAX_PAGE_SIZE = 60;

  private final ProductRepository products;
  private final ProductVariantRepository variants;
  private final CategoryRepository categories;
  private final CategoryService categoryService;
  private final BrandService brandService;
  private final VendorService vendorService;
  private final SearchProvider search;
  private final VehicleService vehicles;
  private final StockService stock;

  public ProductService(ProductRepository products, ProductVariantRepository variants, CategoryRepository categories, CategoryService categoryService,
                        BrandService brandService, VendorService vendorService, SearchProvider search, VehicleService vehicles, StockService stock) {
    this.products = products;
    this.variants = variants;
    this.categories = categories;
    this.categoryService = categoryService;
    this.brandService = brandService;
    this.vendorService = vendorService;
    this.search = search;
    this.vehicles = vehicles;
    this.stock = stock;
  }

  public PageResponse<ProductSummary> page(ProductFilter filter, int page, int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)), sort(filter));
    Page<Long> ids = search.searchProductIds(filter, pageable);
    return PageResponse.of(new PageImpl<>(loadOrdered(ids.getContent()).stream().map(ProductSummary::of).toList(), pageable, ids.getTotalElements()));
  }

  public PageResponse<ProductSummary> byCategory(String slug, ProductFilter filter, int page, int size) {
    if (categories.findBySlug(slug).filter(Category::isActive).isEmpty()) throw new NotFoundException("Catégorie introuvable");
    return page(filter.withCategory(slug), page, size);
  }

  public ProductDetail publicById(Long id) {
    return publicView(products.findByIdAndActiveTrue(id).orElseThrow(() -> new NotFoundException("Produit introuvable")));
  }

  public ProductDetail publicBySlug(String slug) {
    return publicView(products.findBySlugAndActiveTrue(slug).orElseThrow(() -> new NotFoundException("Produit introuvable")));
  }

  public List<ProductSummary> similar(Long id, int limit) {
    var p = products.findByIdAndActiveTrue(id).orElseThrow(() -> new NotFoundException("Produit introuvable"));
    Long subId = p.getSubcategory() == null ? null : p.getSubcategory().getId();
    return summaries(products.findSimilar(p.getId(), p.getCategory().getId(), subId, limit(limit)));
  }

  public List<ProductSummary> featured(int limit) { return summaries(products.findByFeaturedTrueAndActiveTrueOrderByCreatedAtDesc(limit(limit))); }
  public List<ProductSummary> promotions(int limit) { return summaries(products.findPromotions(limit(limit))); }
  public List<ProductSummary> latest(int limit) { return summaries(products.findByActiveTrueOrderByNewArrivalDescCreatedAtDesc(limit(limit))); }
  public List<ProductSummary> bestSellers(int limit) { return summaries(products.findByBestSellerTrueAndActiveTrueOrderByCreatedAtDesc(limit(limit))); }
  public List<ProductSummary> forCategory(Long categoryId, int limit) { return summaries(products.findForCategory(categoryId, limit(limit))); }

  public ProductDetail adminGet(Long id) { return adminView(find(id)); }

  public PageResponse<ProductSummary> forShop(Vendor shop, String q, int page, int size) {
    return page(new ProductFilter(q, null, List.of(), List.of(shop.getSlug()), null, null, null, null, null, null, "newest", true), page, size);
  }

  public ProductDetail sellerGet(Vendor shop, Long id) { return adminView(owned(shop, id)); }

  @Transactional
  public ProductDetail sellerCreate(Vendor shop, ProductRequest r, String by) {
    return create(sellerRequest(r, shop.getId(), false), by);
  }

  @Transactional
  public ProductDetail sellerUpdate(Vendor shop, Long id, ProductRequest r, String by) {
    Product p = owned(shop, id);
    return update(id, sellerRequest(r, shop.getId(), p.isFeatured()), by);
  }

  @Transactional
  public ProductDetail sellerSetActive(Vendor shop, Long id, boolean active) {
    return setActive(owned(shop, id).getId(), active);
  }

  @Transactional
  public ProductDetail sellerDuplicate(Vendor shop, Long id, String by) {
    return duplicate(owned(shop, id).getId(), by);
  }

  private Product owned(Vendor shop, Long id) {
    Product p = find(id);
    if (shop == null || p.getVendor() == null || !shop.getId().equals(p.getVendor().getId())) {
      throw new SecurityException("Accès refusé");
    }
    return p;
  }

  /** Le vendeur publie uniquement pour sa boutique, sans mise en avant d’accueil. */
  private static ProductRequest sellerRequest(ProductRequest r, Long vendorId, boolean featured) {
    return new ProductRequest(
        r.sku(), r.barcode(), r.name(), r.slug(), r.shortDescription(), r.description(),
        r.categoryId(), r.subcategoryId(), r.brandId(), vendorId,
        r.price(), r.promoPrice(), r.costPrice(), r.unit(), r.stock(), r.lowStockThreshold(),
        r.weightKg(), r.lengthCm(), r.widthCm(), r.heightCm(),
        r.mainImageUrl(), r.images(), r.variants(), r.specifications(),
        r.oemReference(), r.manufacturerReference(), r.compatibilities(),
        r.active(), featured, r.newArrival(), false);
  }

  private ProductDetail publicView(Product p) { return ProductDetail.publicView(p, vehicles.forProduct(p.getId())); }
  private ProductDetail adminView(Product p) { return ProductDetail.adminView(p, vehicles.forProduct(p.getId())); }

  @Transactional
  public ProductDetail create(ProductRequest r, String by) {
    var p = new Product();
    apply(p, r);
    p = products.save(p);
    vehicles.replaceCompatibilities(p, r.compatibilities());
    if (p.getStock() > 0) stock.log(p, null, ml.dibidani.domain.StockMovementType.PURCHASE, 0, p.getStock(), p.getStock(), "Stock initial", p.getSku(), by);
    return adminView(p);
  }

  @Transactional
  public ProductDetail update(Long id, ProductRequest r, String by) {
    var p = find(id);
    int productBefore = p.getStock();
    java.util.Map<Long, Integer> variantBefore = p.getVariants().stream().filter(v -> v.getId() != null)
        .collect(java.util.stream.Collectors.toMap(ProductVariant::getId, ProductVariant::getStock));
    apply(p, r);
    p = products.save(p);
    vehicles.replaceCompatibilities(p, r.compatibilities());
    for (ProductVariant v : p.getVariants()) {
      int before = v.getId() == null ? 0 : variantBefore.getOrDefault(v.getId(), 0);
      if (v.getStock() != before) {
        stock.log(p, v, ml.dibidani.domain.StockMovementType.ADJUSTMENT, before, v.getStock() - before, v.getStock(), "Modification de la fiche produit", p.getSku(), by);
      }
    }
    if (p.getVariants().isEmpty() && p.getStock() != productBefore) {
      stock.log(p, null, ml.dibidani.domain.StockMovementType.ADJUSTMENT, productBefore, p.getStock() - productBefore, p.getStock(), "Modification de la fiche produit", p.getSku(), by);
    }
    return adminView(p);
  }

  /** Désactivation logique : le produit reste référencé par les commandes passées. */
  @Transactional
  public ProductDetail deactivate(Long id) {
    return setActive(id, false);
  }

  @Transactional
  public ProductDetail setActive(Long id, boolean active) {
    var p = find(id);
    p.setActive(active);
    return adminView(p);
  }

  @Transactional
  public ProductDetail duplicate(Long id, String by) {
    Product src = find(id);
    Product p = new Product();
    Set<String> taken = new HashSet<>();
    p.setSku(uniqueSku(src.getSku(), taken));
    p.setName(copyName(src.getName()));
    p.setSlug(Slugs.resolve(null, p.getName(), products::existsBySlug));
    p.setShortDescription(src.getShortDescription());
    p.setDescription(src.getDescription());
    p.setPrice(src.getPrice());
    p.setPromoPrice(src.getPromoPrice());
    p.setCostPrice(src.getCostPrice());
    p.setStock(src.getStock());
    p.setLowStockThreshold(src.getLowStockThreshold());
    p.setUnit(src.getUnit());
    p.setWeightKg(src.getWeightKg());
    p.setLengthCm(src.getLengthCm());
    p.setWidthCm(src.getWidthCm());
    p.setHeightCm(src.getHeightCm());
    p.setMainImageUrl(src.getMainImageUrl());
    p.setOemReference(src.getOemReference());
    p.setManufacturerReference(src.getManufacturerReference());
    p.getImages().addAll(src.getImages());
    for (ProductSpecification s : src.getSpecifications()) {
      p.getSpecifications().add(new ProductSpecification(s.getLabel(), s.getValue()));
    }
    p.setCategory(src.getCategory());
    p.setSubcategory(src.getSubcategory());
    p.setBrand(src.getBrand());
    p.setVendor(src.getVendor());
    p.setActive(false);
    p.setFeatured(false);
    p.setNewArrival(false);
    p.setBestSeller(false);
    int pos = 0;
    for (ProductVariant sv : src.getVariants()) {
      ProductVariant v = new ProductVariant();
      v.setProduct(p);
      v.setName(sv.getName());
      v.setSku(sv.getSku() == null || sv.getSku().isBlank() ? null : uniqueSku(sv.getSku(), taken));
      v.setPrice(sv.getPrice());
      v.setStock(sv.getStock());
      v.setActive(sv.isActive());
      v.setPosition(pos++);
      p.getVariants().add(v);
    }
    p = products.save(p);
    var compat = vehicles.forProduct(src.getId()).stream()
        .map(c -> new ml.dibidani.api.dto.VehicleDtos.CompatibilityRequest(c.engineId(), c.yearFrom(), c.yearTo(), c.notes()))
        .toList();
    vehicles.replaceCompatibilities(p, compat);
    if (p.getStock() > 0) {
      stock.log(p, null, ml.dibidani.domain.StockMovementType.PURCHASE, 0, p.getStock(), p.getStock(), "Copie du produit " + src.getSku(), p.getSku(), by);
    }
    return adminView(p);
  }

  private String uniqueSku(String source, Set<String> taken) {
    String base = source == null || source.isBlank() ? "SKU" : source.trim();
    if (base.length() > 88) base = base.substring(0, 88);
    String sku = base.endsWith("-COPIE") ? base : base + "-COPIE";
    for (int i = 2; taken.contains(sku) || products.existsBySku(sku) || variants.existsBySku(sku); i++) {
      sku = base + "-C" + i;
    }
    taken.add(sku);
    return sku;
  }

  private static String copyName(String name) {
    String n = name == null ? "Produit" : name.trim();
    if (n.endsWith(" (copie)")) return n;
    if (n.length() > 245) n = n.substring(0, 245).trim();
    return n + " (copie)";
  }

  private void apply(Product p, ProductRequest r) {
    String sku = r.sku().trim();
    if (p.getId() == null ? products.existsBySku(sku) : products.existsBySkuAndIdNot(sku, p.getId())) throw new ConflictException("Le SKU « " + sku + " » est déjà utilisé");
    String requestedSlug = (r.slug() == null || r.slug().isBlank()) && p.getId() != null ? p.getSlug() : r.slug();
    String slug = Slugs.resolve(requestedSlug, r.name(), s -> p.getId() == null ? products.existsBySlug(s) : products.existsBySlugAndIdNot(s, p.getId()));
    if (p.getId() == null ? products.existsBySlug(slug) : products.existsBySlugAndIdNot(slug, p.getId())) throw new ConflictException("Le slug « " + slug + " » est déjà utilisé");

    Category category = categoryService.find(r.categoryId());
    Category subcategory = r.subcategoryId() == null ? null : categoryService.find(r.subcategoryId());
    if (subcategory != null && (subcategory.getParent() == null || !subcategory.getParent().getId().equals(category.getId()))) {
      throw new IllegalArgumentException("La sous-catégorie doit appartenir à la catégorie choisie");
    }

    p.setSku(sku);
    String barcode = Slugs.blankToNull(r.barcode());
    if (barcode != null) {
      barcode = barcode.replace(" ", "");
      if (p.getId() == null ? products.existsByBarcodeIgnoreCase(barcode) : products.existsByBarcodeIgnoreCaseAndIdNot(barcode, p.getId())) {
        throw new ConflictException("Le code-barres « " + barcode + " » est déjà utilisé");
      }
    }
    p.setBarcode(barcode);
    p.setName(r.name().trim());
    p.setSlug(slug);
    p.setShortDescription(Slugs.blankToNull(r.shortDescription()));
    p.setDescription(Slugs.blankToNull(r.description()));
    p.setCategory(category);
    p.setSubcategory(subcategory);
    p.setBrand(r.brandId() == null ? null : brandService.find(r.brandId()));
    p.setVendor(r.vendorId() == null ? null : vendorService.find(r.vendorId()));
    p.setPrice(r.price());
    p.setPromoPrice(r.promoPrice());
    p.setCostPrice(r.costPrice());
    p.setUnit(r.unit() == null || r.unit().isBlank() ? "unité" : r.unit().trim());
    if (r.stock() != null) p.setStock(r.stock());
    p.setLowStockThreshold(r.lowStockThreshold() == null ? 5 : r.lowStockThreshold());
    p.setWeightKg(r.weightKg());
    p.setLengthCm(r.lengthCm());
    p.setWidthCm(r.widthCm());
    p.setHeightCm(r.heightCm());
    p.setOemReference(Slugs.blankToNull(r.oemReference()));
    p.setManufacturerReference(Slugs.blankToNull(r.manufacturerReference()));
    List<String> gallery = new ArrayList<>();
    if (r.images() != null) {
      r.images().stream().map(String::trim).filter(s -> !s.isEmpty()).distinct().forEach(gallery::add);
    }
    String main = Slugs.blankToNull(r.mainImageUrl());
    if (main != null) {
      gallery.remove(main);
      gallery.add(0, main);
    }
    if (main == null && !gallery.isEmpty()) main = gallery.get(0);
    p.setMainImageUrl(main);
    p.getImages().clear();
    p.getImages().addAll(gallery);
    p.getSpecifications().clear();
    if (r.specifications() != null) r.specifications().forEach(s -> p.getSpecifications().add(new ProductSpecification(s.label().trim(), s.value().trim())));
    syncVariants(p, r.variants() == null ? List.of() : r.variants());
    p.setActive(r.active() == null || r.active());
    p.setFeatured(Boolean.TRUE.equals(r.featured()));
    p.setNewArrival(Boolean.TRUE.equals(r.newArrival()));
    p.setBestSeller(Boolean.TRUE.equals(r.bestSeller()));
  }

  /** Met à jour les variantes existantes par id, ajoute les nouvelles, retire celles absentes de la requête. */
  private void syncVariants(Product p, List<VariantRequest> requested) {
    Map<Long, ProductVariant> existing = p.getVariants().stream().filter(v -> v.getId() != null)
        .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
    Set<Long> kept = requested.stream().map(VariantRequest::id).filter(Objects::nonNull).collect(Collectors.toSet());
    p.getVariants().removeIf(v -> v.getId() != null && !kept.contains(v.getId()));
    int position = 0;
    for (VariantRequest vr : requested) {
      ProductVariant v = vr.id() != null ? existing.get(vr.id()) : null;
      if (v == null) {
        v = new ProductVariant();
        v.setProduct(p);
        p.getVariants().add(v);
      }
      v.setName(vr.name().trim());
      v.setSku(Slugs.blankToNull(vr.sku()));
      v.setPrice(vr.price());
      if (vr.stock() != null) v.setStock(vr.stock());
      v.setActive(vr.active() == null || vr.active());
      v.setPosition(position++);
    }
  }

  private Product find(Long id) { return products.findById(id).orElseThrow(() -> new NotFoundException("Produit introuvable")); }

  private List<Product> loadOrdered(List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    Map<Long, Product> byId = products.findAllById(ids).stream().collect(Collectors.toMap(Product::getId, Function.identity()));
    return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
  }

  private static List<ProductSummary> summaries(List<Product> list) { return list.stream().map(ProductSummary::of).toList(); }

  private static Pageable limit(int limit) { return PageRequest.of(0, Math.max(1, Math.min(limit, 24))); }

  private static Sort sort(ProductFilter f) {
    String s = f.sort() == null ? "" : f.sort();
    return switch (s) {
      case "price_asc" -> Sort.by("price").ascending();
      case "price_desc" -> Sort.by("price").descending();
      case "name" -> Sort.by("name").ascending();
      case "popular" -> Sort.by(Sort.Order.desc("bestSeller"), Sort.Order.desc("reviewCount"));
      case "newest" -> Sort.by("createdAt").descending();
      default -> f.hasQuery() ? Sort.unsorted() : Sort.by("createdAt").descending();
    };
  }
}
