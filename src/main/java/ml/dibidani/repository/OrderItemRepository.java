package ml.dibidani.repository;

import ml.dibidani.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
  @Query("select i from OrderItem i join fetch i.order o left join fetch i.product where i.vendorId = :vendorId order by o.createdAt desc")
  List<OrderItem> findByVendorId(@Param("vendorId") Long vendorId);

  long countByVendorId(Long vendorId);
}
