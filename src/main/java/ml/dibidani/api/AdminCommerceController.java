package ml.dibidani.api;

import jakarta.validation.Valid;
import ml.dibidani.api.dto.DeliveryDtos.ZoneRequest;
import ml.dibidani.api.dto.DeliveryDtos.ZoneResponse;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.PromoDtos.PromoCodeRequest;
import ml.dibidani.api.dto.PromoDtos.PromoCodeResponse;
import ml.dibidani.api.dto.StockDtos.AdjustRequest;
import ml.dibidani.api.dto.StockDtos.MovementResponse;
import ml.dibidani.api.dto.StockDtos.StockRow;
import ml.dibidani.api.dto.VehicleDtos.*;
import ml.dibidani.service.*;
import ml.dibidani.service.UnitService.UnitRequest;
import ml.dibidani.service.UnitService.UnitResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;

/** Stocks, unités, codes promo, zones de livraison et référentiel véhicules du back-office. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminCommerceController {
  private final StockService stock;
  private final UnitService units;
  private final PromoCodeService promos;
  private final DeliveryService delivery;
  private final VehicleService vehicles;

  public AdminCommerceController(StockService stock, UnitService units, PromoCodeService promos, DeliveryService delivery, VehicleService vehicles) {
    this.stock = stock;
    this.units = units;
    this.promos = promos;
    this.delivery = delivery;
    this.vehicles = vehicles;
  }

  // ---------- Stocks ----------

  @GetMapping("/stocks")
  @PreAuthorize("hasAuthority('PERM_STOCK_VIEW')")
  public PageResponse<StockRow> stocks(@RequestParam(required = false) String q, @RequestParam(defaultValue = "all") String filter,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return stock.page(q, filter, page, size);
  }

  @GetMapping("/stocks/overview")
  @PreAuthorize("hasAuthority('PERM_STOCK_VIEW')")
  public ml.dibidani.api.dto.StockDtos.StockOverview stockOverview() { return stock.overview(); }

  @GetMapping("/stocks/types")
  @PreAuthorize("hasAuthority('PERM_STOCK_VIEW')")
  public List<ml.dibidani.api.dto.StockDtos.TypeOption> stockTypes() { return stock.types(); }

  @PostMapping("/stocks/adjust")
  @PreAuthorize("hasAuthority('PERM_STOCK_MANAGE')")
  public StockRow adjust(Authentication a, @Valid @RequestBody AdjustRequest r) { return stock.adjust(r, a.getName()); }

  @PutMapping("/stocks/{productId}/threshold")
  @PreAuthorize("hasAuthority('PERM_STOCK_MANAGE')")
  public StockRow threshold(@PathVariable Long productId, @Valid @RequestBody ml.dibidani.api.dto.StockDtos.ThresholdRequest r) {
    return stock.setThreshold(productId, r.lowStockThreshold());
  }

  @GetMapping("/stocks/movements")
  @PreAuthorize("hasAuthority('PERM_STOCK_VIEW')")
  public PageResponse<MovementResponse> movements(@RequestParam(required = false) Long productId,
                                                  @RequestParam(required = false) ml.dibidani.domain.StockMovementType type,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                  @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
    return stock.movements(productId, type, from, to, page, size);
  }

  // ---------- Unités ----------

  @GetMapping("/units")
  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  public List<UnitResponse> units() { return units.list(false); }

  @PostMapping("/units")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public UnitResponse createUnit(@Valid @RequestBody UnitRequest r) { return units.save(null, r); }

  @PutMapping("/units/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  public UnitResponse updateUnit(@PathVariable Long id, @Valid @RequestBody UnitRequest r) { return units.save(id, r); }

  @DeleteMapping("/units/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  public UnitResponse deleteUnit(@PathVariable Long id) { return units.deactivate(id); }

  // ---------- Codes promo ----------

  @GetMapping("/promo-codes")
  @PreAuthorize("hasAuthority('PERM_PROMOTIONS_MANAGE')")
  public List<PromoCodeResponse> promoCodes() { return promos.list(); }

  @PostMapping("/promo-codes")
  @PreAuthorize("hasAuthority('PERM_PROMOTIONS_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public PromoCodeResponse createPromo(@Valid @RequestBody PromoCodeRequest r) { return promos.create(r); }

  @PutMapping("/promo-codes/{id}")
  @PreAuthorize("hasAuthority('PERM_PROMOTIONS_MANAGE')")
  public PromoCodeResponse updatePromo(@PathVariable Long id, @Valid @RequestBody PromoCodeRequest r) { return promos.update(id, r); }

  @DeleteMapping("/promo-codes/{id}")
  @PreAuthorize("hasAuthority('PERM_PROMOTIONS_MANAGE')")
  public PromoCodeResponse deletePromo(@PathVariable Long id) { return promos.deactivate(id); }

  // ---------- Zones de livraison ----------

  @GetMapping("/delivery-zones")
  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  public List<ZoneResponse> zones() { return delivery.allZones(); }

  @PostMapping("/delivery-zones")
  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ZoneResponse createZone(@Valid @RequestBody ZoneRequest r) { return delivery.createZone(r); }

  @PutMapping("/delivery-zones/{id}")
  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  public ZoneResponse updateZone(@PathVariable Long id, @Valid @RequestBody ZoneRequest r) { return delivery.updateZone(id, r); }

  @DeleteMapping("/delivery-zones/{id}")
  @PreAuthorize("hasAuthority('PERM_DELIVERIES_MANAGE')")
  public ZoneResponse deleteZone(@PathVariable Long id) { return delivery.deactivateZone(id); }

  // ---------- Véhicules (Trouver ma pièce) ----------

  @GetMapping("/vehicles/brands")
  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  public List<BrandResponse> vehicleBrands() { return vehicles.adminBrands(); }

  @PostMapping("/vehicles/brands")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public BrandResponse createVehicleBrand(@Valid @RequestBody BrandRequest r) { return vehicles.saveBrand(null, r); }

  @PutMapping("/vehicles/brands/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  public BrandResponse updateVehicleBrand(@PathVariable Long id, @Valid @RequestBody BrandRequest r) { return vehicles.saveBrand(id, r); }

  @GetMapping("/vehicles/models")
  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  public List<ModelResponse> vehicleModels(@RequestParam(required = false) Long brandId) { return vehicles.adminModels(brandId); }

  @PostMapping("/vehicles/models")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ModelResponse createVehicleModel(@Valid @RequestBody ModelRequest r) { return vehicles.saveModel(null, r); }

  @PutMapping("/vehicles/models/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  public ModelResponse updateVehicleModel(@PathVariable Long id, @Valid @RequestBody ModelRequest r) { return vehicles.saveModel(id, r); }

  @GetMapping("/vehicles/engines")
  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  public List<EngineResponse> vehicleEngines(@RequestParam(required = false) Long modelId) { return vehicles.adminEngines(modelId); }

  @PostMapping("/vehicles/engines")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @ResponseStatus(HttpStatus.CREATED)
  public EngineResponse createVehicleEngine(@Valid @RequestBody EngineRequest r) { return vehicles.saveEngine(null, r); }

  @PutMapping("/vehicles/engines/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  public EngineResponse updateVehicleEngine(@PathVariable Long id, @Valid @RequestBody EngineRequest r) { return vehicles.saveEngine(id, r); }

  /** {@code kind} : brands, models ou engines. Désactivation logique, les compatibilités restent en base. */
  @DeleteMapping("/vehicles/{kind}/{id}")
  @PreAuthorize("hasAuthority('PERM_CATALOG_MANAGE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteVehicleItem(@PathVariable String kind, @PathVariable Long id) { vehicles.deactivate(kind, id); }

  @GetMapping("/products/{id}/compatibilities")
  @PreAuthorize("hasAuthority('PERM_CATALOG_VIEW')")
  public List<CompatibilityResponse> compatibilities(@PathVariable Long id) { return vehicles.forProduct(id); }
}
