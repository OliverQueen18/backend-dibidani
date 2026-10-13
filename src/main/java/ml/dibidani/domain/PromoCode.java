package ml.dibidani.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "promo_codes")
public class PromoCode extends BaseEntity {
  @Column(nullable = false, unique = true) private String code;
  @Column(length = 500) private String description;
  @Enumerated(EnumType.STRING) @Column(nullable = false) private PromoType type;
  /** Pourcentage (PERCENTAGE) ou montant en FCFA (FIXED_AMOUNT) ; ignoré pour FREE_DELIVERY. */
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal value = BigDecimal.ZERO;
  @Column(precision = 14, scale = 2) private BigDecimal minSubtotal;
  @Column(precision = 14, scale = 2) private BigDecimal maxDiscount;
  private Instant startDate;
  private Instant endDate;
  private Integer usageLimit;
  @Column(nullable = false) private int usedCount = 0;
  @Column(nullable = false) private boolean active = true;

  public String getCode() { return code; }
  public void setCode(String v) { code = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { description = v; }
  public PromoType getType() { return type; }
  public void setType(PromoType v) { type = v; }
  public BigDecimal getValue() { return value; }
  public void setValue(BigDecimal v) { value = v; }
  public BigDecimal getMinSubtotal() { return minSubtotal; }
  public void setMinSubtotal(BigDecimal v) { minSubtotal = v; }
  public BigDecimal getMaxDiscount() { return maxDiscount; }
  public void setMaxDiscount(BigDecimal v) { maxDiscount = v; }
  public Instant getStartDate() { return startDate; }
  public void setStartDate(Instant v) { startDate = v; }
  public Instant getEndDate() { return endDate; }
  public void setEndDate(Instant v) { endDate = v; }
  public Integer getUsageLimit() { return usageLimit; }
  public void setUsageLimit(Integer v) { usageLimit = v; }
  public int getUsedCount() { return usedCount; }
  public void setUsedCount(int v) { usedCount = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
}
