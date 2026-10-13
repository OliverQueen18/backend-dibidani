package ml.dibidani.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ml.dibidani.domain.AppUser;
import ml.dibidani.domain.Permission;
import ml.dibidani.repository.AppUserRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwt;
  private final AppUserRepository users;

  public JwtAuthFilter(JwtService jwt, AppUserRepository users) {
    this.jwt = jwt;
    this.users = users;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
    String header = req.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith("Bearer ")) {
      try {
        var parsed = jwt.parse(header.substring(7));
        users.findByEmailIgnoreCase(parsed.email()).filter(AppUser::isActive).filter(u -> u.getTokenVersion() == parsed.version()).ifPresent(u -> {
          List<SimpleGrantedAuthority> authorities = new ArrayList<>();
          authorities.add(new SimpleGrantedAuthority("ROLE_" + u.getRole().name()));
          for (Permission p : u.getRole().permissions()) authorities.add(new SimpleGrantedAuthority(p.authority()));
          var auth = new UsernamePasswordAuthenticationToken(u.getEmail(), null, authorities);
          SecurityContextHolder.getContext().setAuthentication(auth);
        });
      } catch (RuntimeException ignored) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(req, res);
  }
}
