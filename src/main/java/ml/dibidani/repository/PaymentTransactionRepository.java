package ml.dibidani.repository;

import ml.dibidani.domain.PaymentProvider;
import ml.dibidani.domain.PaymentTransaction;
import ml.dibidani.domain.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
  List<PaymentTransaction> findByPaymentIdOrderByCreatedAtAscIdAsc(Long paymentId);
  boolean existsByProviderAndProviderReferenceAndType(PaymentProvider provider, String providerReference, TransactionType type);
}
