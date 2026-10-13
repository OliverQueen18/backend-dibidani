package ml.dibidani.security;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {
  private RequestContext() {}

  public static String ip(HttpServletRequest req) {
    if (req == null) return null;
    String forwarded = req.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
    }
    return req.getRemoteAddr();
  }

  public static String userAgent(HttpServletRequest req) {
    if (req == null) return null;
    String ua = req.getHeader("User-Agent");
    if (ua == null) return null;
    return ua.length() > 300 ? ua.substring(0, 300) : ua;
  }
}
