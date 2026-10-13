package ml.dibidani.service;

import ml.dibidani.api.dto.CartDtos.PromoView;
import ml.dibidani.api.dto.PromoDtos.*;
import ml.dibidani.common.ConflictException;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.PromoCode;
import ml.dibidani.domain.PromoType;
import ml.dibidani.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class PromoCodeService {
  private final PromoCodeRepository promos;

  public PromoCodeService(PromoCodeRepository promos) { this.promos = promos; }

  /** promo est null si le code n'est pas applicable ; view explique pourquoi. */
  public record Evaluation(PromoCode promo, BigDecimal discount, PromoView view) {}

  public Evaluation evaluate(String rawCode, BigDecimal subtotal, Instant now) {
    String code = normalize(rawCode);
    return promos.findByCodeIgnoreCase(code).map(p -> evaluate(p, subtotal, now))
        .orElseGet(() -> new Evaluation(null, BigDecimal.ZERO, new PromoView(code, false, null, null, "Ce code promo n'existe pas.")));
  }

  public Evaluation evaluate(PromoCode p, BigDecimal subtotal, Instant now) {
    String refusal = null;
    if (!p.isActive()) refusal = "Ce code promo n'est plus actif.";
    else if (p.getStartDate() != null && now.isBefore(p.getStartDate())) refusal = "Ce code promo n'est pas encore valable.";
    else if (p.getEndDate() != null && !now.isBefore(p.getEndDate())) refusal = "Ce code promo a expiré.";
    else if (p.getUsageLimit() != null && p.getUsedCount() >= p.getUsageLimit()) refusal = "Ce code promo a atteint sa limite d'utilisation.";
    else if (p.getMinSubtotal() != null && subtotal.compareTo(p.getMinSubtotal()) < 0)
      refusal = "Ce code est valable dès " + fcfa(p.getMinSubtotal()) + " d'achat.";
    if (refusal != null) return new Evaluation(null, BigDecimal.ZERO, new PromoView(p.getCode(), false, p.getType(), p.getDescription(), refusal));

    BigDecimal discount = switch (p.getType()) {
      case PERCENTAGE -> subtotal.multiply(p.getValue()).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
      case FIXED_AMOUNT -> p.getValue().setScale(0, RoundingMode.HALF_UP);
      case FREE_DELIVERY -> BigDecimal.ZERO;
    };
    if (p.getMaxDiscount() != null) discount = discount.min(p.getMaxDiscount());
    discount = discount.min(subtotal).max(BigDecimal.ZERO);
    String message = p.getType() == PromoType.FREE_DELIVERY ? "Livraison offerte avec ce code." : "Remise de " + fcfa(discount) + " appliquée.";
    return new Evaluation(p, discount, new PromoView(p.getCode(), true, p.getType(), p.getDescription(), message));
  }

  /** Verrouille le code et consomme une utilisation au moment de la commande. */
  @Transactional
  public Evaluation consume(String rawCode, BigDecimal subtotal) {
    var p = promos.lockByCode(normalize(rawCode)).orElseThrow(() -> new ConflictException("Ce code promo n'existe plus"));
    var eval = evaluate(p, subtotal, Instant.now());
    if (eval.promo() == null) throw new ConflictException(eval.view().message());
    p.setUsedCount(p.getUsedCount() + 1);
    return eval;
  }

  public static String normalize(String code) { return code == null ? null : code.trim().toUpperCase(Locale.ROOT); }

  static String fcfa(BigDecimal v) {
    return NumberFormat.getIntegerInstance(Locale.FRANCE).format(v.setScale(0, RoundingMode.HALF_UP)).replace('\u202f', ' ').replace('\u00a0', ' ') + " FCFA";
  }

  // ---------- Back-office ----------

  public List<PromoCodeResponse> list() { return promos.findAllByOrderByCreatedAtDesc().stream().map(PromoCodeResponse::of).toList(); }

  @Transactional
  public PromoCodeResponse create(PromoCodeRequest r) {
    var p = new PromoCode();
    apply(p, r);
    return PromoCodeResponse.of(promos.save(p));
  }

  @Transactional
  public PromoCodeResponse update(Long id, PromoCodeRequest r) {
    var p = promos.findById(id).orElseThrow(() -> new NotFoundException("Code promo introuvable"));
    apply(p, r);
    return PromoCodeResponse.of(p);
  }

  @Transactional
  public PromoCodeResponse deactivate(Long id) {
    var p = promos.findById(id).orElseThrow(() -> new NotFoundException("Code promo introuvable"));
    p.setActive(false);
    return PromoCodeResponse.of(p);
  }

  private void apply(PromoCode p, PromoCodeRequest r) {
    String code = normalize(r.code());
    boolean taken = p.getId() == null ? promos.existsByCodeIgnoreCase(code) : promos.existsByCodeIgnoreCaseAndIdNot(code, p.getId());
    if (taken) throw new ConflictException("Le code « " + code + " » existe déjà");
    p.setCode(code);
    p.setDescription(Slugs.blankToNull(r.description()));
    p.setType(r.type());
    p.setValue(r.type() == PromoType.FREE_DELIVERY ? BigDecimal.ZERO : r.value());
    p.setMinSubtotal(r.minSubtotal());
    p.setMaxDiscount(r.maxDiscount());
    p.setStartDate(r.startDate());
    p.setEndDate(r.endDate());
    p.setUsageLimit(r.usageLimit());
    p.setActive(r.active() == null || r.active());
  }
}
