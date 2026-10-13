package ml.dibidani.service;

import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductDetail;
import ml.dibidani.api.dto.ProductDtos.ProductRequest;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.SellerDtos.SellerDashboard;
import ml.dibidani.api.dto.SellerDtos.SellerOrderLine;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.OrderStatus;
import ml.dibidani.domain.Role;
import ml.dibidani.domain.Vendor;
import ml.dibidani.repository.OrderItemRepository;
import ml.dibidani.repository.ProductRepository;
import ml.dibidani.repository.VendorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class SellerShopService {
  private final UserService users;
  private final VendorRepository vendors;
  private final ProductService productService;
  private final ProductRepository productRepo;
  private final OrderItemRepository orderItems;

  public SellerShopService(UserService users, VendorRepository vendors, ProductService productService, ProductRepository productRepo, OrderItemRepository orderItems) {
    this.users = users;
    this.vendors = vendors;
    this.productService = productService;
    this.productRepo = productRepo;
    this.orderItems = orderItems;
  }

  public SellerDashboard dashboard(String email) {
    Vendor shop = shopOf(email);
    var lines = orderedProducts(shop);
    long open = lines.stream().map(SellerOrderLine::orderId).distinct()
        .filter(id -> lines.stream().anyMatch(l -> l.orderId().equals(id) && isOpen(l.status())))
        .count();
    var recentProducts = productService.forShop(shop, null, 0, 6).content();
    return new SellerDashboard(shop.getId(), shop.getName(), shop.getSlug(), shop.getDistrict(),
        productRepo.countByVendorId(shop.getId()), productRepo.countByVendorIdAndActiveTrue(shop.getId()),
        lines.size(), open, recentProducts, lines.stream().limit(8).toList());
  }

  public PageResponse<ProductSummary> products(String email, String q, int page, int size) {
    return productService.forShop(shopOf(email), q, page, size);
  }

  public ProductDetail product(String email, Long id) { return productService.sellerGet(shopOf(email), id); }

  @Transactional
  public ProductDetail create(String email, ProductRequest r) { return productService.sellerCreate(shopOf(email), r, email); }

  @Transactional
  public ProductDetail update(String email, Long id, ProductRequest r) { return productService.sellerUpdate(shopOf(email), id, r, email); }

  @Transactional
  public ProductDetail setActive(String email, Long id, boolean active) { return productService.sellerSetActive(shopOf(email), id, active); }

  @Transactional
  public ProductDetail duplicate(String email, Long id) { return productService.sellerDuplicate(shopOf(email), id, email); }

  public List<SellerOrderLine> orderedProducts(String email) { return orderedProducts(shopOf(email)); }

  private List<SellerOrderLine> orderedProducts(Vendor shop) {
    return orderItems.findByVendorId(shop.getId()).stream().map(SellerOrderLine::of).toList();
  }

  private Vendor shopOf(String email) {
    var u = users.byEmail(email);
    if (u.getRole() != Role.SELLER) throw new SecurityException("Accès refusé");
    return vendors.findByOwnerId(u.getId()).orElseThrow(() -> new NotFoundException("Aucune boutique liée à ce compte"));
  }

  private static boolean isOpen(String status) {
    try { return OrderStatus.valueOf(status).isOpen(); }
    catch (IllegalArgumentException e) { return false; }
  }
}
