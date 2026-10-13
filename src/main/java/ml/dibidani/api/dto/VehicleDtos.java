package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.*;

public final class VehicleDtos {
  private VehicleDtos() {}

  public record TypeOption(VehicleType type, String label) {}

  public record BrandRequest(
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
      @Size(max = 180) @Pattern(regexp = "^$|^[a-z0-9]+(-[a-z0-9]+)*$", message = "Slug invalide") String slug,
      @Size(max = 500) String logoUrl, Boolean active, Integer displayOrder) {}

  public record BrandResponse(Long id, String name, String slug, String logoUrl, boolean active, int displayOrder) {
    public static BrandResponse of(VehicleBrand b) { return new BrandResponse(b.getId(), b.getName(), b.getSlug(), b.getLogoUrl(), b.isActive(), b.getDisplayOrder()); }
  }

  public record ModelRequest(
      @NotNull(message = "La marque est obligatoire") Long brandId,
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
      @NotNull(message = "Le type de véhicule est obligatoire") VehicleType vehicleType,
      Boolean active) {}

  public record ModelResponse(Long id, Long brandId, String brandName, String name, String slug, VehicleType vehicleType, String vehicleTypeLabel, boolean active) {
    public static ModelResponse of(VehicleModel m) {
      return new ModelResponse(m.getId(), m.getBrand().getId(), m.getBrand().getName(), m.getName(), m.getSlug(), m.getVehicleType(), m.getVehicleType().label(), m.isActive());
    }
  }

  public record EngineRequest(
      @NotNull(message = "Le modèle est obligatoire") Long modelId,
      @NotBlank(message = "Le nom est obligatoire") @Size(max = 150) String name,
      FuelType fuelType,
      @Min(value = 1, message = "Cylindrée invalide") @Max(20000) Integer displacementCc,
      @Min(value = 1, message = "Puissance invalide") @Max(2000) Integer powerHp,
      @NotNull(message = "L'année de début est obligatoire") @Min(value = 1950, message = "Année invalide") @Max(value = 2100, message = "Année invalide") Integer yearFrom,
      @Min(value = 1950, message = "Année invalide") @Max(value = 2100, message = "Année invalide") Integer yearTo,
      Boolean active) {
    @AssertTrue(message = "L'année de fin doit suivre l'année de début")
    public boolean isPeriodValid() { return yearTo == null || yearFrom == null || yearTo >= yearFrom; }
  }

  public record EngineResponse(Long id, Long modelId, String modelName, String brandName, String name, FuelType fuelType,
                               Integer displacementCc, Integer powerHp, int yearFrom, Integer yearTo, String label, boolean active) {
    public static EngineResponse of(VehicleEngine e) {
      var m = e.getModel();
      return new EngineResponse(e.getId(), m.getId(), m.getName(), m.getBrand().getName(), e.getName(), e.getFuelType(), e.getDisplacementCc(),
          e.getPowerHp(), e.getYearFrom(), e.getYearTo(), label(e), e.isActive());
    }

    public static String label(VehicleEngine e) {
      String period = e.getYearFrom() + (e.getYearTo() == null ? "+" : e.getYearTo() == e.getYearFrom() ? "" : "-" + e.getYearTo());
      return e.getName() + " (" + period + ")";
    }
  }

  public record CompatibilityRequest(
      @NotNull(message = "La motorisation est obligatoire") Long engineId,
      @Min(1950) @Max(2100) Integer yearFrom,
      @Min(1950) @Max(2100) Integer yearTo,
      @Size(max = 500) String notes) {
    @AssertTrue(message = "L'année de fin doit suivre l'année de début")
    public boolean isPeriodValid() { return yearTo == null || yearFrom == null || yearTo >= yearFrom; }
  }

  public record CompatibilityResponse(Long id, Long engineId, String engineName, Long modelId, String modelName, Long brandId, String brandName,
                                      VehicleType vehicleType, Integer yearFrom, Integer yearTo, String years, String notes, String label) {
    public static CompatibilityResponse of(VehicleCompatibility c) {
      var e = c.getEngine();
      var m = e.getModel();
      int from = c.getYearFrom() != null ? c.getYearFrom() : e.getYearFrom();
      Integer to = c.getYearTo() != null ? c.getYearTo() : e.getYearTo();
      String years = to == null ? from + "+" : from == to ? String.valueOf(from) : from + "-" + to;
      return new CompatibilityResponse(c.getId(), e.getId(), e.getName(), m.getId(), m.getName(), m.getBrand().getId(), m.getBrand().getName(),
          m.getVehicleType(), c.getYearFrom(), c.getYearTo(), years, c.getNotes(), m.getBrand().getName() + " " + m.getName() + " " + e.getName() + " (" + years + ")");
    }
  }

  public record PartCategory(Long id, String name, String slug, String icon, long productCount) {}
}
