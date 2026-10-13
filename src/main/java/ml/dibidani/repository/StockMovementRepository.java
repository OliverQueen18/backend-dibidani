package ml.dibidani.repository;

import ml.dibidani.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {

  /** [type, nombre de mouvements, somme absolue des quantités] sur la période. */
  @Query("select m.type, count(m), coalesce(sum(abs(m.quantity)), 0) from StockMovement m where m.createdAt >= :from group by m.type")
  List<Object[]> totalsByTypeSince(@Param("from") Instant from);
}
