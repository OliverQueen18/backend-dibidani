package ml.dibidani.service;

import ml.dibidani.api.dto.OrderDtos.*;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.common.Slugs;
import ml.dibidani.domain.*;
import ml.dibidani.repository.CustomerOrderRepository;
import ml.dibidani.search.SearchText;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class AdminOrderService {
  private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako");

  private final CustomerOrderRepository orders;
  private final OrderWorkflow workflow;
  private final OrderMapper mapper;

  public AdminOrderService(CustomerOrderRepository orders, OrderWorkflow workflow, OrderMapper mapper) {
    this.orders = orders;
    this.workflow = workflow;
    this.mapper = mapper;
  }

  public record Filter(OrderStatus status, String group, PaymentStatus paymentStatus, String paymentMethod, DeliveryMethod deliveryMethod,
                       Long customerId, String q, LocalDate from, LocalDate to) {}

  /** Groupes d'onglets du back-office. */
  static Set<OrderStatus> group(String g) {
    if (g == null) return null;
    return switch (g) {
      case "to_process" -> EnumSet.of(OrderStatus.NEW, OrderStatus.PAID);
      case "awaiting_payment" -> EnumSet.of(OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED);
      case "preparing" -> EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PREPARING);
      case "to_deliver" -> EnumSet.of(OrderStatus.READY_FOR_DELIVERY, OrderStatus.OUT_FOR_DELIVERY);
      case "done" -> EnumSet.of(OrderStatus.DELIVERED);
      case "closed" -> EnumSet.of(OrderStatus.CANCELLED, OrderStatus.RETURNED, OrderStatus.REFUNDED);
      default -> null;
    };
  }

  public PageResponse<OrderSummary> page(Filter f, int page, int size) {
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.desc("createdAt")));
    return PageResponse.of(orders.findAll(spec(f), pageable).map(mapper::summary));
  }

  public List<CustomerOrder> list(Filter f, int max) {
    return orders.findAll(spec(f), PageRequest.of(0, max, Sort.by(Sort.Order.desc("createdAt")))).getContent();
  }

  private static Specification<CustomerOrder> spec(Filter f) {
    return (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();
      if (f.status() != null) and.add(cb.equal(root.get("status"), f.status()));
      Set<OrderStatus> g = group(f.group());
      if (g != null) and.add(root.get("status").in(g));
      if (f.paymentStatus() != null) and.add(cb.equal(root.get("paymentStatus"), f.paymentStatus()));
      if (f.paymentMethod() != null && !f.paymentMethod().isBlank()) and.add(cb.equal(root.get("paymentMethod"), f.paymentMethod()));
      if (f.deliveryMethod() != null) and.add(cb.equal(root.get("deliveryMethod"), f.deliveryMethod()));
      if (f.customerId() != null) and.add(cb.equal(root.get("customer").get("id"), f.customerId()));
      if (f.from() != null) and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from().atStartOfDay(BAMAKO).toInstant()));
      if (f.to() != null) and.add(cb.lessThan(root.get("createdAt"), f.to().plusDays(1).atStartOfDay(BAMAKO).toInstant()));
      if (f.q() != null && !f.q().isBlank()) {
        String raw = f.q().trim().toLowerCase(Locale.ROOT);
        for (String term : SearchText.terms(f.q())) {
          String like = SearchText.contains(term);
          and.add(cb.or(
              cb.like(cb.lower(root.get("orderNumber")), "%" + raw.replace("%", "") + "%"),
              cb.like(SearchText.normNullable(cb, root.get("customerName")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.<String>get("customerPhone"), "")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.<String>get("customerEmail"), "")), like, SearchText.ESCAPE)));
        }
      }
      return cb.and(and.toArray(Predicate[]::new));
    };
  }

  public Map<String, Long> counts() {
    Map<String, Long> out = new LinkedHashMap<>();
    out.put("all", orders.count());
    for (String g : List.of("to_process", "awaiting_payment", "preparing", "to_deliver", "done", "closed")) out.put(g, orders.countByStatusIn(group(g)));
    return out;
  }

  public OrderResponse detail(Long id) { return mapper.admin(find(id)); }

  public CustomerOrder find(Long id) { return orders.findById(id).orElseThrow(() -> new NotFoundException("Commande introuvable")); }

  @Transactional
  public OrderResponse changeStatus(Long id, StatusChangeRequest r, AppUser actor) {
    var o = find(id);
    workflow.transition(o, r.status(), actor, null, r.comment());
    return mapper.admin(o);
  }

  @Transactional
  public OrderResponse note(Long id, NoteRequest r) {
    var o = find(id);
    o.setAdminNote(Slugs.blankToNull(r.note()));
    return mapper.admin(o);
  }

  public List<StatusOption> statuses() {
    return Arrays.stream(OrderStatus.values()).map(s -> new StatusOption(s, s.label())).toList();
  }
}
