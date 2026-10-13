package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.AdvertisementDtos.AdvertisementResponse;
import ml.dibidani.api.dto.BrandDtos.BrandResponse;
import ml.dibidani.api.dto.CategoryDtos.CategoryResponse;
import ml.dibidani.api.dto.HomeDtos.*;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductDetail;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.VendorDtos.VendorResponse;
import ml.dibidani.domain.AdPosition;
import ml.dibidani.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Agrégats publics (accueil, paramètres, newsletter) et anciennes routes /public conservées pour compatibilité. */
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {
  private final HomeService home;
  private final ShopService shop;
  private final ProductService products;
  private final CategoryService categories;
  private final VendorService vendors;
  private final BrandService brands;
  private final AdvertisementService ads;

  public PublicController(HomeService home, ShopService shop, ProductService products, CategoryService categories,
                          VendorService vendors, BrandService brands, AdvertisementService ads) {
    this.home = home;
    this.shop = shop;
    this.products = products;
    this.categories = categories;
    this.vendors = vendors;
    this.brands = brands;
    this.ads = ads;
  }

  @GetMapping("/home")
  public HomeResponse home() { return home.home(); }

  @GetMapping("/settings")
  public SettingsResponse settings() { return shop.settings(); }

  @PostMapping("/newsletter")
  public NewsletterResponse subscribe(@Valid @RequestBody NewsletterRequest r) { return shop.subscribe(r.email()); }

  @GetMapping("/products")
  public PageResponse<ProductSummary> products(@ModelAttribute ProductQuery query) {
    return products.page(query.toFilter(false), query.pageOrDefault(), query.sizeOrDefault());
  }

  @GetMapping("/products/{slug}")
  public ProductDetail product(@PathVariable String slug) { return products.publicBySlug(slug); }

  @GetMapping("/categories")
  public List<CategoryResponse> categories() { return categories.publicTree(); }

  @GetMapping("/vendors")
  public List<VendorResponse> vendors() { return vendors.publicList(); }

  @GetMapping("/brands")
  public List<BrandResponse> brands() { return brands.publicList(); }

  @GetMapping("/ads/{position}")
  public List<AdvertisementResponse> ads(@PathVariable AdPosition position) { return ads.visible(position); }
}
