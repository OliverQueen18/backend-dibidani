package ml.dibidani.security;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fenêtre glissante en mémoire : 20 tentatives / 10 min par IP, 8 / 10 min par e-mail. */
@Component
public class LoginRateLimiter {
  private static final int PER_IP = 20;
  private static final int PER_EMAIL = 8;
  private static final Duration WINDOW = Duration.ofMinutes(10);

  private final Map<String, Deque<Instant>> byIp = new ConcurrentHashMap<>();
  private final Map<String, Deque<Instant>> byEmail = new ConcurrentHashMap<>();

  public boolean allow(String ip, String email) {
    Instant now = Instant.now();
    return allowKey(byIp, ip == null ? "unknown" : ip, PER_IP, now)
        && allowKey(byEmail, email == null ? "" : email.trim().toLowerCase(), PER_EMAIL, now);
  }

  public void success(String ip, String email) {
    if (email != null) byEmail.remove(email.trim().toLowerCase());
  }

  private boolean allowKey(Map<String, Deque<Instant>> map, String key, int max, Instant now) {
    Deque<Instant> q = map.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (q) {
      Instant cutoff = now.minus(WINDOW);
      for (Iterator<Instant> it = q.iterator(); it.hasNext(); ) {
        if (it.next().isBefore(cutoff)) it.remove();
      }
      if (q.size() >= max) return false;
      q.addLast(now);
      return true;
    }
  }
}
