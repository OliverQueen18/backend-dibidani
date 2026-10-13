package ml.dibidani.api.dto;

import jakarta.validation.constraints.*;
import ml.dibidani.domain.PromoCode;
import ml.dibidani.domain.PromoType;
import java.math.BigDecimal;
import java.time.Instant;

public final class PromoDtos {
  private PromoDtos() {}

  public record PromoCodeRequest(
      @NotBlank(message = "Le code est obligatoire") @Size(max = 50)
      @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Lettres, chiffres, tirets et soulignés uniquement") String code,
      @Size(max = 500) String description,
      @NotNull(message = "Le type est obligatoire") PromoType type,
      @NotNull(message = "La valeur est obligatoire") @DecimalMin(value = "0.0", message = "Valeur invalide") BigDecimal value,
      @DecimalMin(value = "0.0") BigDecimal minSubtotal,
      @DecimalMin(value = "0.0") BigDecimal maxDiscount,
      Instant startDate,
      Instant endDate,
      @Min(value = 1, message = "Limite minimale : 1") Integer usageLimit,
      Boolean active) {

    @AssertTrue(message = "Un pourcentage doit être compris entre 1 et 100")
    public boolean isPercentValid() { return type != PromoType.PERCENTAGE || value == null || (value.signum() > 0 && value.compareTo(BigDecimal.valueOf(100)) <= 0); }

    @AssertTrue(message = "Le montant de la remise doit être positif")
    public boolean isAmountValid() { return type != PromoType.FIXED_AMOUNT || value == null || value.signum() > 0; }

    @AssertTrue(message = "La date de fin doit être postérieure à la date de début")
    public boolean isPeriodValid() { return startDate == null || endDate == null || endDate.isAfter(startDate); }
  }

  public record PromoCodeResponse(Long id, String code, String description, PromoType type, BigDecimal value, BigDecimal minSubtotal,
                                  BigDecimal maxDiscount, Instant startDate, Instant endDate, Integer usageLimit, int usedCount,
                                  boolean active, boolean currentlyValid, Instant createdAt) {
    public static PromoCodeResponse of(PromoCode p) {
      Instant now = Instant.now();
      boolean valid = p.isActive() && (p.getStartDate() == null || !now.isBefore(p.getStartDate())) && (p.getEndDate() == null || now.isBefore(p.getEndDate()))
          && (p.getUsageLimit() == null || p.getUsedCount() < p.getUsageLimit());
      return new PromoCodeResponse(p.getId(), p.getCode(), p.getDescription(), p.getType(), p.getValue(), p.getMinSubtotal(), p.getMaxDiscount(),
          p.getStartDate(), p.getEndDate(), p.getUsageLimit(), p.getUsedCount(), p.isActive(), valid, p.getCreatedAt());
    }
  }
}
