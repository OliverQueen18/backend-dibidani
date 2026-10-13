package ml.dibidani.repository;

import ml.dibidani.domain.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {
  List<Unit> findAllByOrderByDisplayOrderAscNameAsc();
  List<Unit> findByActiveTrueOrderByDisplayOrderAscNameAsc();
  boolean existsByNameIgnoreCase(String name);
  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
