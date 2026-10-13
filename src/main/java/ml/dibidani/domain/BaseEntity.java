package ml.dibidani.domain;
import jakarta.persistence.*;
import java.time.Instant;
@MappedSuperclass
public abstract class BaseEntity {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
  @Column(nullable=false, updatable=false) private Instant createdAt = Instant.now();
  @Column(nullable=false) private Instant updatedAt = Instant.now();
  @PreUpdate void touch(){ updatedAt=Instant.now(); }
  public Long getId(){return id;} public void setId(Long id){this.id=id;}
  public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
  public Instant getUpdatedAt(){return updatedAt;} public void setUpdatedAt(Instant v){updatedAt=v;}
}
