package ml.dibidani.domain;

import jakarta.persistence.*;

/** Commerçant du marché : fiche catalogue, éventuellement liée à un compte SELLER. */
@Entity
@Table(name = "vendors")
public class Vendor extends BaseEntity {
  @Column(nullable = false) private String name;
  @Column(nullable = false, unique = true) private String slug;
  @Column(columnDefinition = "text") private String description;
  private String logoUrl;
  private String phone;
  private String whatsapp;
  private String address;
  private String district;
  private String city = "Bamako";
  @Column(nullable = false) private boolean active = true;
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", unique = true)
  private AppUser owner;

  public String getName() { return name; }
  public void setName(String v) { name = v; }
  public String getSlug() { return slug; }
  public void setSlug(String v) { slug = v; }
  public String getDescription() { return description; }
  public void setDescription(String v) { description = v; }
  public String getLogoUrl() { return logoUrl; }
  public void setLogoUrl(String v) { logoUrl = v; }
  public String getPhone() { return phone; }
  public void setPhone(String v) { phone = v; }
  public String getWhatsapp() { return whatsapp; }
  public void setWhatsapp(String v) { whatsapp = v; }
  public String getAddress() { return address; }
  public void setAddress(String v) { address = v; }
  public String getDistrict() { return district; }
  public void setDistrict(String v) { district = v; }
  public String getCity() { return city; }
  public void setCity(String v) { city = v; }
  public boolean isActive() { return active; }
  public void setActive(boolean v) { active = v; }
  public AppUser getOwner() { return owner; }
  public void setOwner(AppUser v) { owner = v; }
}
