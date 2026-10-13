package ml.dibidani.domain;
import jakarta.persistence.*;
@Entity @Table(name="brands")
public class Brand extends BaseEntity {
  @Column(nullable=false, unique=true) private String name;
  private String logoUrl;
  @Column(nullable=false) private boolean active=true;
  public String getName(){return name;} public void setName(String v){name=v;}
  public String getLogoUrl(){return logoUrl;} public void setLogoUrl(String v){logoUrl=v;}
  public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
