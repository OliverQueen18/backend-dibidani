package ml.dibidani.service;

import jakarta.servlet.http.HttpServletRequest;
import ml.dibidani.api.dto.PageResponse;
import ml.dibidani.domain.AuditLog;
import ml.dibidani.repository.AppUserRepository;
import ml.dibidani.repository.AuditLogRepository;
import ml.dibidani.search.SearchText;
import ml.dibidani.security.RequestContext;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService {
  private static final Logger log = LoggerFactory.getLogger(AuditService.class);
  private final AuditLogRepository logs;
  private final AppUserRepository users;

  public AuditService(AuditLogRepository logs, AppUserRepository users) {
    this.logs = logs;
    this.users = users;
  }

  public record AuditRow(Long id, Instant createdAt, String actorEmail, String action, String entityType, String entityId,
                         boolean success, String ip, String details) {}

  @Async
  @Transactional
  public void record(String actorEmail, String action, String entityType, String entityId, boolean success, String ip, String userAgent, String details) {
    try {
      var row = new AuditLog();
      if (actorEmail != null) {
        users.findByEmailIgnoreCase(actorEmail).ifPresent(u -> {
          row.setActorId(u.getId());
          row.setActorEmail(u.getEmail());
        });
        if (row.getActorEmail() == null) row.setActorEmail(actorEmail);
      }
      row.setAction(action);
      row.setEntityType(entityType);
      row.setEntityId(entityId);
      row.setSuccess(success);
      row.setIp(ip);
      row.setUserAgent(userAgent);
      row.setDetails(details == null || details.length() <= 2000 ? details : details.substring(0, 2000));
      logs.save(row);
    } catch (RuntimeException e) {
      log.warn("Journal d'audit indisponible : {}", e.getMessage());
    }
  }

  public void record(HttpServletRequest req, String actorEmail, String action, String entityType, String entityId, boolean success, String details) {
    record(actorEmail, action, entityType, entityId, success, RequestContext.ip(req), RequestContext.userAgent(req), details);
  }

  @Transactional(readOnly = true)
  public PageResponse<AuditRow> page(String q, String action, String actor, Instant from, Instant to, int page, int size) {
    Specification<AuditLog> spec = (root, query, cb) -> {
      List<Predicate> and = new ArrayList<>();
      if (action != null && !action.isBlank()) and.add(cb.equal(root.get("action"), action));
      if (actor != null && !actor.isBlank()) and.add(cb.like(cb.lower(cb.coalesce(root.get("actorEmail"), "")), "%" + actor.trim().toLowerCase() + "%"));
      if (from != null) and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
      if (to != null) and.add(cb.lessThan(root.get("createdAt"), to));
      if (q != null && !q.isBlank()) {
        for (String term : SearchText.terms(q)) {
          String like = SearchText.contains(term);
          and.add(cb.or(
              cb.like(cb.lower(cb.coalesce(root.get("action"), "")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.get("entityType"), "")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.get("entityId"), "")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.get("details"), "")), like, SearchText.ESCAPE),
              cb.like(cb.lower(cb.coalesce(root.get("actorEmail"), "")), like, SearchText.ESCAPE)));
        }
      }
      return cb.and(and.toArray(Predicate[]::new));
    };
    var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
    return PageResponse.of(logs.findAll(spec, pageable).map(a ->
        new AuditRow(a.getId(), a.getCreatedAt(), a.getActorEmail(), a.getAction(), a.getEntityType(), a.getEntityId(), a.isSuccess(), a.getIp(), a.getDetails())));
  }
}
