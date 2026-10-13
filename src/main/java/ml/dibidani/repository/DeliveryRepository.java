package ml.dibidani.repository;

import ml.dibidani.domain.Delivery;
import ml.dibidani.domain.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {
  Optional<Delivery> findByOrderId(Long orderId);
  List<Delivery> findByOrderIdIn(Collection<Long> orderIds);
  List<Delivery> findByCourierIdAndStatusIn(Long courierId, Collection<DeliveryStatus> statuses);
  long countByStatus(DeliveryStatus status);
}
