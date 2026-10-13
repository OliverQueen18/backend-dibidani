package ml.dibidani.repository;

import ml.dibidani.domain.CustomerOrder;
import ml.dibidani.domain.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long>, JpaSpecificationExecutor<CustomerOrder> {
  /** Statuts comptés comme ventes (voir {@link OrderStatus#countsAsSale()}). */
  String SALE_STATUSES = "('PAID','CONFIRMED','PREPARING','READY_FOR_DELIVERY','OUT_FOR_DELIVERY','DELIVERED')";

  Optional<CustomerOrder> findByOrderNumber(String orderNumber);
  Optional<CustomerOrder> findByTrackingToken(String trackingToken);
  boolean existsByOrderNumber(String orderNumber);
  boolean existsByTrackingToken(String trackingToken);
  List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
  List<CustomerOrder> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);
  List<CustomerOrder> findByCustomerIdAndStatusInOrderByCreatedAtDesc(Long customerId, Collection<OrderStatus> statuses, Pageable pageable);
  long countByCustomerId(Long customerId);
  long countByStatus(OrderStatus status);
  long countByStatusIn(Collection<OrderStatus> statuses);
  long countByCreatedAtGreaterThanEqual(Instant from);

  @Query("select coalesce(sum(o.total), 0) from CustomerOrder o where o.status in :statuses")
  BigDecimal sumTotalByStatusIn(@Param("statuses") Collection<OrderStatus> statuses);

  @Query(value = "select count(*), coalesce(sum(total), 0) from customer_orders where status in " + SALE_STATUSES
      + " and created_at >= :from and created_at < :to", nativeQuery = true)
  List<Object[]> salesBetween(@Param("from") Instant from, @Param("to") Instant to);

  @Query(value = "select cast(date_trunc('day', created_at) as date) as d, count(*), coalesce(sum(total), 0) from customer_orders where status in "
      + SALE_STATUSES + " and created_at >= :from and created_at < :to group by 1 order by 1", nativeQuery = true)
  List<Object[]> salesByDay(@Param("from") Instant from, @Param("to") Instant to);

  @Query(value = "select status, count(*) from customer_orders where created_at >= :from and created_at < :to group by status", nativeQuery = true)
  List<Object[]> countByStatusBetween(@Param("from") Instant from, @Param("to") Instant to);

  @Query(value = "select coalesce(i.vendor_name, 'Sans vendeur'), sum(i.quantity), sum(i.line_total) from order_items i join customer_orders o on o.id = i.order_id"
      + " where o.status in " + SALE_STATUSES + " and o.created_at >= :from and o.created_at < :to group by 1 order by 3 desc limit :limit", nativeQuery = true)
  List<Object[]> salesByVendor(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

  @Query(value = "select i.product_id, max(i.product_name), max(i.product_sku), sum(i.quantity), sum(i.line_total) from order_items i join customer_orders o on o.id = i.order_id"
      + " where o.status in " + SALE_STATUSES + " and o.created_at >= :from and o.created_at < :to group by i.product_id order by 4 desc limit :limit", nativeQuery = true)
  List<Object[]> topProducts(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

  @Query(value = "select coalesce(pc.name, c.name), sum(i.quantity), sum(i.line_total) from order_items i join customer_orders o on o.id = i.order_id"
      + " join products p on p.id = i.product_id join categories c on c.id = p.category_id left join categories pc on pc.id = c.parent_id"
      + " where o.status in " + SALE_STATUSES + " and o.created_at >= :from and o.created_at < :to group by 1 order by 3 desc", nativeQuery = true)
  List<Object[]> salesByCategory(@Param("from") Instant from, @Param("to") Instant to);

  @Query(value = "select coalesce(payment_method, 'INCONNU'), count(*), coalesce(sum(total), 0) from customer_orders where status in " + SALE_STATUSES
      + " and created_at >= :from and created_at < :to group by 1 order by 3 desc", nativeQuery = true)
  List<Object[]> salesByPaymentMethod(@Param("from") Instant from, @Param("to") Instant to);

  @Query(value = "select coalesce(delivery_zone_name, delivery_district, 'Non renseigné'), count(*), coalesce(sum(total), 0) from customer_orders where status in "
      + SALE_STATUSES + " and created_at >= :from and created_at < :to group by 1 order by 2 desc limit :limit", nativeQuery = true)
  List<Object[]> salesByZone(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

  @Query(value = "select coalesce(sum(i.quantity), 0) from order_items i join customer_orders o on o.id = i.order_id where o.status in " + SALE_STATUSES
      + " and o.created_at >= :from and o.created_at < :to", nativeQuery = true)
  long unitsSold(@Param("from") Instant from, @Param("to") Instant to);

  @Query("select o.id from CustomerOrder o where o.status in :statuses and o.createdAt < :before order by o.createdAt")
  List<Long> findIdsByStatusInAndCreatedAtBefore(@Param("statuses") Collection<OrderStatus> statuses, @Param("before") Instant before, Pageable pageable);

  @Query(value = "select distinct p.category_id from order_items i join customer_orders o on o.id = i.order_id join products p on p.id = i.product_id"
      + " where o.customer_id = :customerId", nativeQuery = true)
  List<Long> purchasedCategoryIds(@Param("customerId") Long customerId);

  @Query(value = "select distinct i.product_id from order_items i join customer_orders o on o.id = i.order_id where o.customer_id = :customerId", nativeQuery = true)
  List<Long> purchasedProductIds(@Param("customerId") Long customerId);

  @Query(value = "select o.customer_id, count(*), coalesce(sum(o.total), 0), max(o.created_at) from customer_orders o where o.customer_id in :ids"
      + " and o.status in " + SALE_STATUSES + " group by o.customer_id", nativeQuery = true)
  List<Object[]> customerStats(@Param("ids") Collection<Long> customerIds);
}
