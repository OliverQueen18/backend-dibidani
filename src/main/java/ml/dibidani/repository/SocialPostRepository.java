package ml.dibidani.repository;

import ml.dibidani.domain.SocialChannel;
import ml.dibidani.domain.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {
  List<SocialPost> findByProductIdOrderByCreatedAtDesc(Long productId);
  List<SocialPost> findByChannelOrderByCreatedAtDesc(SocialChannel channel);
}
