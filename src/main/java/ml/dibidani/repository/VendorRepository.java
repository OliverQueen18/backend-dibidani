package ml.dibidani.repository;

import ml.dibidani.domain.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long>, JpaSpecificationExecutor<Vendor> {
  Optional<Vendor> findBySlug(String slug);
  Optional<Vendor> findBySlugAndActiveTrue(String slug);
  boolean existsBySlug(String slug);
  boolean existsBySlugAndIdNot(String slug, Long id);
  List<Vendor> findByActiveTrueOrderByNameAsc();
  List<Vendor> findAllByOrderByNameAsc();
  Optional<Vendor> findByOwnerId(Long ownerId);
}
