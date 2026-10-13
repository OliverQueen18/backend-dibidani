package ml.dibidani.repository;

import ml.dibidani.domain.CheckoutSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CheckoutSessionRepository extends JpaRepository<CheckoutSession, Long> {
  Optional<CheckoutSession> findByUserId(Long userId);
}
