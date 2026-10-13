package ml.dibidani.security;

import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 4 demandes OTP / 10 min par identifiant, 12 / 10 min par IP ; 8 vérifications / 10 min. */
@Component
public class OtpRateLimiter {
  private static final Duration WINDOW = Duration.ofMinutes(10);
  private static final int FORGOT_PER_ID = 4;
  private static final int FORGOT_PER_IP = 12;
  private static final int VERIFY_PER_ID = 8;

  private final Map<String, Deque<Instant>> forgotId = new ConcurrentHashMap<>();
  private final Map<String, Deque<Instant>> forgotIp = new ConcurrentHashMap<>();
  private final Map<String, Deque<Instant>> verifyId = new ConcurrentHashMap<>();

  public boolean allowForgot(String ip, String identifier) {
    Instant now = Instant.now();
    return allow(forgotIp, ip == null ? "unknown" : ip, FORGOT_PER_IP, now)
        && allow(forgotId, key(identifier), FORGOT_PER_ID, now);
  }

  public boolean allowVerify(String identifier) {
    return allow(verifyId, key(identifier), VERIFY_PER_ID, Instant.now());
  }

  public void clear(String identifier) {
    if (identifier != null) {
      forgotId.remove(key(identifier));
      verifyId.remove(key(identifier));
    }
  }

  private static String key(String identifier) {
    return identifier == null ? "" : identifier.trim().toLowerCase();
  }

  private boolean allow(Map<String, Deque<Instant>> map, String key, int max, Instant now) {
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
