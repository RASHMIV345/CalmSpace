package com.healing;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class Jwt {
  private final SecretKey key;
  Jwt(@Value("${app.secret}") String s) { key = Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8)); }
  String make(AppUser u) {
    return Jwts.builder().subject(String.valueOf(u.id))
      .expiration(new Date(System.currentTimeMillis() + 86_400_000L)).signWith(key).compact();
  }
  Long read(String t) { return Long.valueOf(Jwts.parser().verifyWith(key).build().parseSignedClaims(t).getPayload().getSubject()); }
}

@Component
class JwtFilter extends OncePerRequestFilter {
  private final Jwt jwt; private final UserRepo users;
  JwtFilter(Jwt j, UserRepo u) { jwt = j; users = u; }
  @Override protected void doFilterInternal(HttpServletRequest rq, HttpServletResponse rs, FilterChain ch)
      throws ServletException, IOException {
    String h = rq.getHeader("Authorization");
    if (h != null && h.startsWith("Bearer ")) {
      try { // role is read from the DB on every request, so role changes/deletions apply immediately
        Long id = jwt.read(h.substring(7));
        users.findById(id).ifPresent(u -> SecurityContextHolder.getContext().setAuthentication(
          new UsernamePasswordAuthenticationToken(u.id, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role)))));
      } catch (Exception ignored) {}
    }
    ch.doFilter(rq, rs);
  }
}

@Configuration
class SecurityConfig {
  @Bean PasswordEncoder enc() { return new BCryptPasswordEncoder(); }
  @Bean SecurityFilterChain chain(HttpSecurity http, JwtFilter f) throws Exception {
    http.csrf(c -> c.disable())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/**").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/therapist/**").hasRole("THERAPIST")
        .anyRequest().authenticated())
      .addFilterBefore(f, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
  @Bean CommandLineRunner seed(UserRepo users, PasswordEncoder enc, @Value("${app.admin-password}") String pw) {
    return a -> { if (users.findByEmail("admin@calm.local") == null) {
      AppUser u = new AppUser(); u.name = "Admin"; u.email = "admin@calm.local";
      u.passwordHash = enc.encode(pw); u.role = "ADMIN"; users.save(u); } };
  }
}
