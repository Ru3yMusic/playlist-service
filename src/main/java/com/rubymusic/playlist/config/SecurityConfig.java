package com.rubymusic.playlist.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless REST security configuration for playlist-service.
 *
 * <p>Authorization strategy:
 * <ul>
 *   <li>{@code /api/internal/v1/**} — requires {@code ROLE_SERVICE} JWT (new canonical path)</li>
 *   <li>{@code /api/v1/playlists/internal/**} — requires {@code ROLE_SERVICE} JWT (legacy path, kept for migration)</li>
 *   <li>{@code /actuator/**, /swagger-ui/**, /v3/api-docs/**} — public infrastructure</li>
 *   <li>Any other request — requires valid JWT (USER or SERVICE)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> res.sendError(401, "Unauthorized"))
                        .accessDeniedHandler((req, res, e) -> res.sendError(403, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                        // New canonical internal path — requires SERVICE role
                        .requestMatchers("/api/internal/v1/**").hasAuthority("ROLE_SERVICE")

                        // Legacy internal path — kept for backward compatibility during migration
                        .requestMatchers("/api/v1/playlists/internal/**").hasAuthority("ROLE_SERVICE")

                        // Public infrastructure
                        .requestMatchers("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // All other API endpoints require authentication (USER or SERVICE)
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
