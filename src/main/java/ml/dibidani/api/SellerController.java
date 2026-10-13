package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductDetail;
import ml.dibidani.api.dto.ProductDtos.ProductRequest;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.SellerDtos.SellerDashboard;
import ml.dibidani.api.dto.SellerDtos.SellerOrderLine;
import ml.dibidani.service.SellerShopService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seller")
@PreAuthorize("hasRole('SELLER')")
public class SellerController {
  private final SellerShopService seller;

  public SellerController(SellerShopService seller) { this.seller = seller; }

  @GetMapping("/dashboard")
  public SellerDashboard dashboard(Authentication a) { return seller.dashboard(a.getName()); }

  @GetMapping("/products")
  public PageResponse<ProductSummary> products(Authentication a,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
    return seller.products(a.getName(), q, page, size);
  }

  @GetMapping("/products/{id}")
  public ProductDetail product(Authentication a, @PathVariable Long id) { return seller.product(a.getName(), id); }

  @PostMapping("/products")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductDetail create(Authentication a, @Valid @RequestBody ProductRequest r) { return seller.create(a.getName(), r); }

  @PutMapping("/products/{id}")
  public ProductDetail update(Authentication a, @PathVariable Long id, @Valid @RequestBody ProductRequest r) {
    return seller.update(a.getName(), id, r);
  }

  @PostMapping("/products/{id}/active")
  public ProductDetail setActive(Authentication a, @PathVariable Long id, @RequestParam boolean active) {
    return seller.setActive(a.getName(), id, active);
  }

  @PostMapping("/products/{id}/duplicate")
  @ResponseStatus(HttpStatus.CREATED)
  public ProductDetail duplicate(Authentication a, @PathVariable Long id) { return seller.duplicate(a.getName(), id); }

  @GetMapping("/orders")
  public List<SellerOrderLine> orders(Authentication a) { return seller.orderedProducts(a.getName()); }
}
