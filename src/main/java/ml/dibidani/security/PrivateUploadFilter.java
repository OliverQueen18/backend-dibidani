package ml.dibidani.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PrivateUploadFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String path = req.getRequestURI() == null ? "" : req.getRequestURI();
    if (path.contains("/uploads/private/")) {
      res.setStatus(HttpServletResponse.SC_FORBIDDEN);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write("{\"status\":403,\"message\":\"Image originale privée\",\"errors\":null}");
      return;
    }
    chain.doFilter(req, res);
  }
}
