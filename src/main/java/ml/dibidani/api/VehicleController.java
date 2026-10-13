package ml.dibidani.api;

import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.ProductDtos.ProductSummary;
import ml.dibidani.api.dto.VehicleDtos.*;
import ml.dibidani.domain.VehicleType;
import ml.dibidani.service.VehicleService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** Parcours « Trouver ma pièce » : type → marque → modèle → année → motorisation → pièces compatibles. */
@RestController
@RequestMapping("/api/v1/vehicles")
public class VehicleController {
  private final VehicleService vehicles;

  public VehicleController(VehicleService vehicles) { this.vehicles = vehicles; }

  @GetMapping("/types")
  public List<TypeOption> types() { return vehicles.types(); }

  @GetMapping("/brands")
  public List<BrandResponse> brands(@RequestParam(required = false) VehicleType type) { return vehicles.brands(type); }

  @GetMapping("/brands/{brandId}/models")
  public List<ModelResponse> models(@PathVariable Long brandId, @RequestParam(required = false) VehicleType type) { return vehicles.models(brandId, type); }

  @GetMapping("/models/{modelId}/years")
  public List<Integer> years(@PathVariable Long modelId) { return vehicles.years(modelId); }

  @GetMapping("/models/{modelId}/engines")
  public List<EngineResponse> engines(@PathVariable Long modelId, @RequestParam(required = false) Integer year) { return vehicles.engines(modelId, year); }

  @GetMapping("/models/{modelId}/categories")
  public List<PartCategory> categories(@PathVariable Long modelId, @RequestParam(required = false) Long engineId, @RequestParam(required = false) Integer year) {
    return vehicles.partCategories(modelId, engineId, year);
  }

  @GetMapping("/models/{modelId}/products")
  public PageResponse<ProductSummary> products(@PathVariable Long modelId, @RequestParam(required = false) Long engineId,
                                               @RequestParam(required = false) Integer year, @RequestParam(required = false) String category,
                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "24") int size) {
    return vehicles.compatibleProducts(modelId, engineId, year, category, page, size);
  }
}
