package com.rubymusic.playlist.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;

/**
 * Legacy filter — superseded by {@link JwtAuthenticationFilter} + {@link SecurityConfig}.
 *
 * <p>Kept for reference but NOT registered as a Spring bean (@Component removed).
 * The new security chain (Spring Security + JwtAuthenticationFilter) handles all
 * authentication and authorization, including internal endpoints.
 *
 * @deprecated Use {@link SecurityConfig} authorization rules instead.
 */
@Slf4j
@RequiredArgsConstructor
public class ServiceJwtFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/playlists/internal/";

    private final PublicKey jwtPublicKey;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header on internal endpoint: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(jwtPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!"SERVICE".equals(claims.get("role"))) {
                log.warn("Token on internal endpoint has wrong role: {}", claims.get("role"));
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        } catch (JwtException ex) {
            log.warn("Invalid service JWT on internal endpoint: {}", ex.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
