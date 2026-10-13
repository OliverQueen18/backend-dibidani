package ml.dibidani.repository;

import ml.dibidani.domain.Favorite;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
  List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);
  List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
  Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);
  boolean existsByUserIdAndProductId(Long userId, Long productId);
  long countByUserId(Long userId);
}
