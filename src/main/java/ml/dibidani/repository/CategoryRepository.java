package ml.dibidani.repository;

import ml.dibidani.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long>, JpaSpecificationExecutor<Category> {
  Optional<Category> findBySlug(String slug);
  boolean existsBySlug(String slug);
  boolean existsBySlugAndIdNot(String slug, Long id);
  List<Category> findByActiveTrueOrderByDisplayOrderAsc();
  List<Category> findAllByOrderByDisplayOrderAscNameAsc();
  List<Category> findByParentIsNullAndActiveTrueOrderByDisplayOrderAscNameAsc();
  List<Category> findByParentIdAndActiveTrueOrderByDisplayOrderAscNameAsc(Long parentId);
  List<Category> findByFeaturedTrueAndActiveTrueOrderByDisplayOrderAsc();
  List<Category> findTop5ByActiveTrueAndNameContainingIgnoreCaseOrderByDisplayOrderAsc(String q);
}
