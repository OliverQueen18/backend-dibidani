package ml.dibidani.repository;

import jakarta.persistence.LockModeType;
import ml.dibidani.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :hash")
  Optional<RefreshToken> lockByHash(@Param("hash") String hash);

  @Modifying
  @Query("update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason where t.familyId = :family and t.revokedAt is null")
  int revokeFamily(@Param("family") String family, @Param("reason") String reason, @Param("now") Instant now);

  @Modifying
  @Query("update RefreshToken t set t.revokedAt = :now, t.revokedReason = :reason where t.user.id = :userId and t.revokedAt is null")
  int revokeAllForUser(@Param("userId") Long userId, @Param("reason") String reason, @Param("now") Instant now);

  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :before")
  int deleteExpiredBefore(@Param("before") Instant before);
}
