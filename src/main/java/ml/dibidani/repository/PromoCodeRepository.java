package ml.dibidani.repository;

import jakarta.persistence.LockModeType;
import ml.dibidani.domain.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {
  Optional<PromoCode> findByCodeIgnoreCase(String code);
  boolean existsByCodeIgnoreCase(String code);
  boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);
  List<PromoCode> findAllByOrderByCreatedAtDesc();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from PromoCode p where upper(p.code) = upper(:code)")
  Optional<PromoCode> lockByCode(@Param("code") String code);
}
