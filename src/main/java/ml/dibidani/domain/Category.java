package ml.dibidani.domain;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
public class Category extends BaseEntity {
  @Column(nullable = false) private String name;
  @Column(nullable = false, unique = true) private String slug;
  @Column(columnDefinition = "text") private String description;
  private String icon;
  private String imageUrl;
  @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "parent_id") private Category parent;
  @OneToMany(mappedBy = "parent") @OrderBy("displayOrder asc, name asc") private List<Category> children = new ArrayList<>();
  @Column(nullable = false) private Integer displayOrder = 0;
  @Column(nullable = false) private boolean active = true;
  @Column(nullable = false) private boolean featured = false;

  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { slug = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { description = v; }
  public String getIcon() { return icon; }
  public void setIcon(String v) { icon = v; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String v) { imageUrl = v; }
  public Category getParent() { return parent; }
  public void setParent(Category v) { parent = v; }
  public List<Category> getChildren() { return children; }
  public Integer getDisplayOrder() { return displayOrder; }
  public void setDisplayOrder(Integer v) { displayOrder = v == null ? 0 : v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public boolean isFeatured() { return featured; }
  public void setFeatured(boolean v) { featured = v; }
}
