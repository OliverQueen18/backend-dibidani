package ml.dibidani.service;

import ml.dibidani.api.dto.CategoryDtos.CategoryResponse;
import ml.dibidani.api.dto.HomeDtos.CategorySection;
import ml.dibidani.api.dto.HomeDtos.HomeResponse;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.domain.AdPosition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class HomeService {
  private static final int SECTION_SIZE = 8;
  private static final int MAX_CATEGORY_SECTIONS = 4;

  private final CategoryService categories;
  private final ProductService products;
  private final BrandService brands;
  private final VendorService vendors;
  private final AdvertisementService ads;

  public HomeService(CategoryService categories, ProductService products, BrandService brands, VendorService vendors, AdvertisementService ads) {
    this.categories = categories;
    this.products = products;
    this.brands = brands;
    this.vendors = vendors;
    this.ads = ads;
  }

  public HomeResponse home() {
    List<CategoryResponse> tree = categories.publicTree();
    List<ProductSummary> popular = products.bestSellers(SECTION_SIZE);
    if (popular.isEmpty()) popular = products.featured(SECTION_SIZE);

    List<CategoryResponse> sectionCategories = categories.featured().stream().filter(c -> c.parentId() == null).toList();
    if (sectionCategories.isEmpty()) sectionCategories = tree;
    List<CategorySection> sections = sectionCategories.stream()
        .map(c -> new CategorySection(c, products.forCategory(c.id(), SECTION_SIZE)))
        .filter(s -> !s.products().isEmpty())
        .limit(MAX_CATEGORY_SECTIONS)
        .toList();

    return new HomeResponse(
        ads.visible(AdPosition.HOME_HERO),
        tree,
        ads.visible(AdPosition.HOME_TOP),
        products.promotions(SECTION_SIZE),
        popular,
        ads.visible(AdPosition.HOME_MIDDLE),
        products.latest(SECTION_SIZE),
        sections,
        brands.publicList(),
        vendors.publicList().stream().filter(v -> v.productCount() > 0).toList(),
        ads.visible(AdPosition.HOME_BOTTOM));
  }
}
