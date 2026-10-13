package ml.dibidani.repository;

import ml.dibidani.domain.PasswordResetOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PasswordResetOtpRepository extends JpaRepository<PasswordResetOtp, Long> {
  Optional<PasswordResetOtp> findFirstByUserIdAndConsumedAtIsNullOrderByCreatedAtDesc(Long userId);
  List<PasswordResetOtp> findByUserIdAndConsumedAtIsNull(Long userId);

  @Modifying
  @Query("delete from PasswordResetOtp o where o.expiresAt < :before")
  int deleteExpiredBefore(@Param("before") Instant before);
}
