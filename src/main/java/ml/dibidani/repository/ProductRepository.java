package ml.dibidani.repository;

import jakarta.persistence.LockModeType;
import ml.dibidani.domain.Product;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
  Optional<Product> findBySlug(String slug);
  Optional<Product> findBySlugAndActiveTrue(String slug);
  Optional<Product> findByIdAndActiveTrue(Long id);
  Optional<Product> findBySku(String sku);
  Optional<Product> findBySkuIgnoreCase(String sku);
  boolean existsBySlug(String slug);
  boolean existsBySlugAndIdNot(String slug, Long id);
  boolean existsBySku(String sku);
  boolean existsBySkuAndIdNot(String sku, Long id);
  Optional<Product> findFirstByBarcodeIgnoreCase(String barcode);
  boolean existsByBarcodeIgnoreCase(String barcode);
  boolean existsByBarcodeIgnoreCaseAndIdNot(String barcode, Long id);
  long countByActiveTrue();
  long countByActiveTrueAndStockLessThanEqual(int stock);

  List<Product> findByFeaturedTrueAndActiveTrueOrderByCreatedAtDesc(Pageable pageable);
  List<Product> findByBestSellerTrueAndActiveTrueOrderByCreatedAtDesc(Pageable pageable);
  List<Product> findByActiveTrueOrderByNewArrivalDescCreatedAtDesc(Pageable pageable);

  @Query("select p from Product p where p.active = true and p.promoPrice is not null and p.promoPrice < p.price order by p.updatedAt desc")
  List<Product> findPromotions(Pageable pageable);

  @Query("select p from Product p join p.category c left join c.parent cp left join p.subcategory s where p.active = true and (c.id = :categoryId or s.id = :categoryId or cp.id = :categoryId) order by p.featured desc, p.createdAt desc")
  List<Product> findForCategory(@Param("categoryId") Long categoryId, Pageable pageable);

  @Query("select p from Product p left join p.subcategory s where p.active = true and p.id <> :productId and (p.category.id = :categoryId or s.id = :subcategoryId) order by case when s.id = :subcategoryId then 0 else 1 end, p.bestSeller desc, p.createdAt desc")
  List<Product> findSimilar(@Param("productId") Long productId, @Param("categoryId") Long categoryId, @Param("subcategoryId") Long subcategoryId, Pageable pageable);

  @Query("select count(p) from Product p left join p.subcategory s where p.category.id = :categoryId or s.id = :categoryId")
  long countByAnyCategory(@Param("categoryId") Long categoryId);

  long countByVendorId(Long vendorId);
  long countByVendorIdAndActiveTrue(Long vendorId);
  long countByBrandId(Long brandId);
  long countByActiveTrueAndStock(int stock);

  @Query("select count(p) from Product p where p.active = true and p.stock > 0 and p.stock <= p.lowStockThreshold")
  long countLowStock();

  @Query("select p from Product p where p.active = true and p.stock <= p.lowStockThreshold order by p.stock asc, p.name asc")
  List<Product> findLowStock(Pageable pageable);

  boolean existsByUnitIgnoreCase(String unit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> lockById(@Param("id") Long id);
}
