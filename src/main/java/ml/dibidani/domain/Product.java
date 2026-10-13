package ml.dibidani.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product extends BaseEntity {
  @Column(nullable = false, unique = true) private String sku;
  @Column(length = 255) private String barcode;
  @Column(nullable = false) private String name;
  @Column(nullable = false, unique = true) private String slug;
  @Column(length = 500) private String shortDescription;
  @Column(columnDefinition = "text") private String description;
  @Column(nullable = false, precision = 14, scale = 2) private BigDecimal price;
  @Column(precision = 14, scale = 2) private BigDecimal promoPrice;
  @Column(precision = 14, scale = 2) private BigDecimal costPrice;
  @Column(nullable = false) private Integer stock = 0;
  @Column(nullable = false) private Integer lowStockThreshold = 5;
  @Column(nullable = false) private String unit = "unité";
  @Column(precision = 10, scale = 3) private BigDecimal weightKg;
  @Column(precision = 10, scale = 2) private BigDecimal lengthCm;
  @Column(precision = 10, scale = 2) private BigDecimal widthCm;
  @Column(precision = 10, scale = 2) private BigDecimal heightCm;
  private String mainImageUrl;
  private String oemReference;
  private String manufacturerReference;

  @ElementCollection @BatchSize(size = 50)
  @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
  @OrderColumn(name = "position") @Column(name = "image_url")
  private List<String> images = new ArrayList<>();

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true) @BatchSize(size = 50)
  @OrderBy("position asc")
  private List<ProductVariant> variants = new ArrayList<>();

  @ElementCollection @BatchSize(size = 50)
  @CollectionTable(name = "product_specifications", joinColumns = @JoinColumn(name = "product_id"))
  @OrderColumn(name = "position")
  private List<ProductSpecification> specifications = new ArrayList<>();

  @ManyToOne(optional = false, fetch = FetchType.EAGER) private Category category;
  @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "subcategory_id") private Category subcategory;
  @ManyToOne(fetch = FetchType.EAGER) private Brand brand;
  @ManyToOne(fetch = FetchType.EAGER) private Vendor vendor;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private boolean featured = false;
  @Column(nullable = false) private boolean newArrival = false;
  @Column(nullable = false) private boolean bestSeller = false;
  @Column(nullable = false) private Double rating = 0.0;
  @Column(nullable = false) private Integer reviewCount = 0;

  /** Promotion effective : un prix promotionnel strictement inférieur au prix normal. */
  public boolean isOnPromotion() { return promoPrice != null && price != null && promoPrice.compareTo(price) < 0; }
  public BigDecimal getEffectivePrice() { return isOnPromotion() ? promoPrice : price; }

  public String getSku() { return sku; }
  public void setSku(String v) { sku = v; }
  public String getBarcode() { return barcode; }
  public void setBarcode(String v) { barcode = v; }
  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { slug = v; }
  public String getShortDescription() { return shortDescription; }
  public void setShortDescription(String v) { shortDescription = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { description = v; }
  public BigDecimal getPrice() { return price; }
  public void setPrice(BigDecimal v) { price = v; }
  public BigDecimal getPromoPrice() { return promoPrice; }
  public void setPromoPrice(BigDecimal v) { promoPrice = v; }
  public BigDecimal getCostPrice() { return costPrice; }
  public void setCostPrice(BigDecimal v) { costPrice = v; }
  public Integer getStock() { return stock; }
  public void setStock(Integer v) { stock = v; }
  public Integer getLowStockThreshold() { return lowStockThreshold; }
  public void setLowStockThreshold(Integer v) { lowStockThreshold = v; }
  public String getUnit() { return unit; }
  public void setUnit(String v) { unit = v; }
  public BigDecimal getWeightKg() { return weightKg; }
  public void setWeightKg(BigDecimal v) { weightKg = v; }
  public BigDecimal getLengthCm() { return lengthCm; }
  public void setLengthCm(BigDecimal v) { lengthCm = v; }
  public BigDecimal getWidthCm() { return widthCm; }
  public void setWidthCm(BigDecimal v) { widthCm = v; }
  public BigDecimal getHeightCm() { return heightCm; }
  public void setHeightCm(BigDecimal v) { heightCm = v; }
  public String getMainImageUrl() { return mainImageUrl; }
  public void setMainImageUrl(String v) { mainImageUrl = v; }
  public String getOemReference() { return oemReference; }
  public void setOemReference(String v) { oemReference = v; }
  public String getManufacturerReference() { return manufacturerReference; }
  public void setManufacturerReference(String v) { manufacturerReference = v; }
  public List<String> getImages() { return images; }
  public void setImages(List<String> v) { images = v; }
  public List<ProductVariant> getVariants() { return variants; }
  public List<ProductSpecification> getSpecifications() { return specifications; }
  public void setSpecifications(List<ProductSpecification> v) { specifications = v; }
  public Category getCategory() { return category; }
  public void setCategory(Category v) { category = v; }
  public Category getSubcategory() { return subcategory; }
  public void setSubcategory(Category v) { subcategory = v; }
  public Brand getBrand() { return brand; }
  public void setBrand(Brand v) { brand = v; }
  public Vendor getVendor() { return vendor; }
  public void setVendor(Vendor v) { vendor = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public boolean isFeatured() { return featured; }
  public void setFeatured(boolean v) { featured = v; }
  public boolean isNewArrival() { return newArrival; }
  public void setNewArrival(boolean v) { newArrival = v; }
  public boolean isBestSeller() { return bestSeller; }
  public void setBestSeller(boolean v) { bestSeller = v; }
  public Double getRating() { return rating; }
  public void setRating(Double v) { rating = v; }
  public Integer getReviewCount() { return reviewCount; }
  public void setReviewCount(Integer v) { reviewCount = v; }
}
