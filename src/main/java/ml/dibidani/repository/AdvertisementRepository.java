package ml.dibidani.repository;

import ml.dibidani.domain.AdPosition;
import ml.dibidani.domain.Advertisement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {
  @Query("select a from Advertisement a where a.position = :position and a.active = true and a.startDate <= :now and a.endDate >= :now order by a.displayOrder asc, a.id asc")
  List<Advertisement> findVisible(@Param("position") AdPosition position, @Param("now") Instant now);

  List<Advertisement> findAllByOrderByPositionAscDisplayOrderAsc();
  List<Advertisement> findByPositionOrderByDisplayOrderAsc(AdPosition position);
  boolean existsByPosition(AdPosition position);
}
