package ml.dibidani.repository;

import ml.dibidani.domain.Payment;
import ml.dibidani.domain.PaymentProvider;
import ml.dibidani.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
  Optional<Payment> findByOrderId(Long orderId);
  List<Payment> findByOrderIdIn(Collection<Long> orderIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.provider = :provider and p.providerReference = :reference")
  Optional<Payment> lockByProviderReference(@Param("provider") PaymentProvider provider, @Param("reference") String reference);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.order.id = :orderId")
  Optional<Payment> lockByOrderId(@Param("orderId") Long orderId);

  long countByStatus(PaymentStatus status);

  @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
  BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

  @Query("select p.status, count(p), coalesce(sum(p.amount), 0) from Payment p group by p.status")
  List<Object[]> statsByStatus();
}
