package ml.dibidani.service;

import ml.dibidani.api.dto.AccountDtos.AddressResponse;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.api.dto.StatsDtos.*;
import ml.dibidani.api.dto.StockDtos.StockRow;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.domain.*;
import ml.dibidani.repository.*;
import ml.dibidani.search.SearchText;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.*;
import java.util.*;

/** Indicateurs du back-office, calculés en SQL sur les commandes réellement vendues. */
@Service
@Transactional(readOnly = true)
public class StatsService {
  private static final ZoneId BAMAKO = ZoneId.of("Africa/Bamako");

  private final CustomerOrderRepository orders;
  private final AppUserRepository users;
  private final ProductRepository products;
  private final PaymentRepository paymentRepo;
  private final CustomerAddressRepository addresses;
  private final FavoriteRepository favorites;
  private final PaymentService payments;
  private final OrderMapper mapper;

  public StatsService(CustomerOrderRepository orders, AppUserRepository users, ProductRepository products, PaymentRepository paymentRepo,
                      CustomerAddressRepository addresses, FavoriteRepository favorites, PaymentService payments, OrderMapper mapper) {
    this.orders = orders;
    this.users = users;
    this.products = products;
    this.paymentRepo = paymentRepo;
    this.addresses = addresses;
    this.favorites = favorites;
    this.payments = payments;
    this.mapper = mapper;
  }

  public DashboardResponse dashboard(int days) {
    int period = Math.max(1, Math.min(days, 365));
    LocalDate today = LocalDate.now(BAMAKO);
    LocalDate fromDay = today.minusDays(period - 1L);
    Instant from = start(fromDay), to = start(today.plusDays(1));
    Instant prevFrom = start(fromDay.minusDays(period));

    var current = sales(from, to);
    var previous = sales(prevFrom, from);
    var todaySales = sales(start(today), to);
    var kpis = new Kpis(current.revenue, previous.revenue, current.count, previous.count,
        todaySales.revenue, todaySales.count, average(current), users.countByRoleAndCreatedAtGreaterThanEqual(Role.CUSTOMER, from),
        orders.unitsSold(from, to), products.countLowStock(), products.countByActiveTrueAndStock(0),
        paymentRepo.countByStatus(PaymentStatus.PENDING), paymentRepo.sumAmountByStatus(PaymentStatus.SUCCESS),
        orders.countByStatusIn(AdminOrderService.group("to_process")), orders.countByStatusIn(AdminOrderService.group("preparing")),
        orders.countByStatusIn(AdminOrderService.group("to_deliver")), users.countByRole(Role.CUSTOMER), products.countByActiveTrue());

    var latest = orders.findAll(PageRequest.of(0, 8, Sort.by(Sort.Order.desc("createdAt")))).map(mapper::summary).getContent();
    var lowStock = products.findLowStock(PageRequest.of(0, 8)).stream().map(StockRow::of).toList();
    return new DashboardResponse(period, fromDay, today, kpis, byDay(from, to, fromDay, today), statuses(from, to),
        methods(from, to), categories(from, to), top(from, to, 5), payments.stats(), lowStock, latest);
  }

  public ReportResponse report(LocalDate fromDay, LocalDate toDay) {
    LocalDate today = LocalDate.now(BAMAKO);
    LocalDate f = fromDay == null ? today.minusDays(29) : fromDay;
    LocalDate t = toDay == null ? today : toDay;
    if (t.isBefore(f)) throw new IllegalArgumentException("La date de fin doit suivre la date de début");
    if (f.plusDays(731).isBefore(t)) throw new IllegalArgumentException("Période limitée à deux ans");
    Instant from = start(f), to = start(t.plusDays(1));
    var s = sales(from, to);
    List<LabelValue> vendors = orders.salesByVendor(from, to, 10).stream()
        .map(r -> new LabelValue((String) r[0], (String) r[0], num(r[1]), dec(r[2]))).toList();
    List<LabelValue> zones = orders.salesByZone(from, to, 15).stream()
        .map(r -> new LabelValue((String) r[0], (String) r[0], num(r[1]), dec(r[2]))).toList();
    return new ReportResponse(f, t, s.revenue, s.count, average(s), orders.unitsSold(from, to),
        users.countByRoleAndCreatedAtGreaterThanEqual(Role.CUSTOMER, from), byDay(from, to, f, t), categories(from, to), vendors,
        methods(from, to), zones, statuses(from, to), top(from, to, 10));
  }

  // ---------- Clients ----------

  public PageResponse<CustomerRow> customers(String q, int page, int size) {
    Specification<AppUser> spec = (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();
      and.add(cb.equal(root.get("role"), Role.CUSTOMER));
      if (q != null && !q.isBlank()) {
        for (String term : SearchText.terms(q)) {
          String like = SearchText.contains(term);
          and.add(cb.or(cb.like(SearchText.norm(cb, root.get("fullName")), like, SearchText.ESCAPE),
              cb.like(cb.lower(root.get("email")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.<String>get("phone"), "")), like, SearchText.ESCAPE)));
        }
      }
      return cb.and(and.toArray(Predicate[]::new));
    };
    var result = users.findAll(spec, PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Order.desc("createdAt"))));
    Map<Long, Object[]> stats = new HashMap<>();
    if (!result.isEmpty()) orders.customerStats(result.map(AppUser::getId).getContent()).forEach(r -> stats.put(((Number) r[0]).longValue(), r));
    return PageResponse.of(result.map(u -> row(u, stats.get(u.getId()))));
  }

  public CustomerDetail customer(Long id) {
    var u = users.findById(id).filter(x -> x.getRole() == Role.CUSTOMER).orElseThrow(() -> new NotFoundException("Client introuvable"));
    var stat = orders.customerStats(List.of(id)).stream().findFirst().orElse(null);
    var list = orders.findByCustomerIdOrderByCreatedAtDesc(id, PageRequest.of(0, 50)).stream().map(mapper::summary).toList();
    return new CustomerDetail(row(u, stat), addresses.findByUserIdOrderByDefaultAddressDescCreatedAtAsc(id).stream().map(AddressResponse::of).toList(),
        list, favorites.countByUserId(id));
  }

  @Transactional
  public CustomerRow setActive(Long id, boolean active) {
    var u = users.findById(id).filter(x -> x.getRole() == Role.CUSTOMER).orElseThrow(() -> new NotFoundException("Client introuvable"));
    u.setActive(active);
    var stat = orders.customerStats(List.of(id)).stream().findFirst().orElse(null);
    return row(u, stat);
  }

  private static CustomerRow row(AppUser u, Object[] stat) {
    long count = stat == null ? 0 : num(stat[1]);
    BigDecimal spent = stat == null ? BigDecimal.ZERO : dec(stat[2]);
    Instant last = stat == null ? null : instant(stat[3]);
    return new CustomerRow(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isActive(), count, spent, last, u.getLastLoginAt(), u.getCreatedAt());
  }

  // ---------- Agrégats ----------

  private record Sales(long count, BigDecimal revenue) {}

  private Sales sales(Instant from, Instant to) {
    var rows = orders.salesBetween(from, to);
    if (rows.isEmpty()) return new Sales(0, BigDecimal.ZERO);
    Object[] r = rows.getFirst();
    return new Sales(num(r[0]), dec(r[1]));
  }

  private static BigDecimal average(Sales s) {
    return s.count == 0 ? BigDecimal.ZERO : s.revenue.divide(BigDecimal.valueOf(s.count), 0, RoundingMode.HALF_UP);
  }

  /** Série complète, jours sans vente inclus (graphiques continus). */
  private List<DayPoint> byDay(Instant from, Instant to, LocalDate fromDay, LocalDate toDay) {
    Map<LocalDate, DayPoint> found = new HashMap<>();
    for (Object[] r : orders.salesByDay(from, to)) {
      LocalDate d = r[0] instanceof Date sql ? sql.toLocalDate() : LocalDate.parse(r[0].toString());
      found.put(d, new DayPoint(d, num(r[1]), dec(r[2])));
    }
    List<DayPoint> out = new ArrayList<>();
    for (LocalDate d = fromDay; !d.isAfter(toDay); d = d.plusDays(1)) out.add(found.getOrDefault(d, new DayPoint(d, 0, BigDecimal.ZERO)));
    return out;
  }

  private List<LabelValue> statuses(Instant from, Instant to) {
    Map<String, Long> counts = new HashMap<>();
    orders.countByStatusBetween(from, to).forEach(r -> counts.put((String) r[0], num(r[1])));
    return Arrays.stream(OrderStatus.values()).filter(s -> counts.containsKey(s.name()))
        .map(s -> new LabelValue(s.name(), s.label(), counts.get(s.name()), null)).toList();
  }

  private List<LabelValue> methods(Instant from, Instant to) {
    return orders.salesByPaymentMethod(from, to).stream().map(r -> {
      String key = (String) r[0];
      String label;
      try { label = PaymentMethod.valueOf(key).label(); } catch (IllegalArgumentException e) { label = key; }
      return new LabelValue(key, label, num(r[1]), dec(r[2]));
    }).toList();
  }

  private List<LabelValue> categories(Instant from, Instant to) {
    return orders.salesByCategory(from, to).stream().map(r -> new LabelValue((String) r[0], (String) r[0], num(r[1]), dec(r[2]))).toList();
  }

  private List<TopProduct> top(Instant from, Instant to, int limit) {
    return orders.topProducts(from, to, limit).stream()
        .map(r -> new TopProduct(((Number) r[0]).longValue(), (String) r[1], (String) r[2], num(r[3]), dec(r[4]))).toList();
  }

  private static Instant start(LocalDate d) { return d.atStartOfDay(BAMAKO).toInstant(); }
  private static long num(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
  private static BigDecimal dec(Object o) { return o == null ? BigDecimal.ZERO : o instanceof BigDecimal b ? b : new BigDecimal(o.toString()); }
  private static Instant instant(Object o) {
    if (o == null) return null;
    if (o instanceof Instant i) return i;
    if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
    if (o instanceof OffsetDateTime odt) return odt.toInstant();
    return Instant.parse(o.toString());
  }
}
