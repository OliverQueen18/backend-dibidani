package ml.dibidani.repository;

import ml.dibidani.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;

public interface BrandRepository extends JpaRepository<Brand, Long>, JpaSpecificationExecutor<Brand> {
  List<Brand> findByActiveTrueOrderByNameAsc();
  List<Brand> findAllByOrderByNameAsc();
  boolean existsByNameIgnoreCase(String name);
  boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
