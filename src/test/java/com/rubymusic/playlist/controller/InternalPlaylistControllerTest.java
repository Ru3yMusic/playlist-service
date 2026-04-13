package com.rubymusic.playlist.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubymusic.playlist.config.JwtAuthenticationFilter;
import com.rubymusic.playlist.config.JwtTokenProvider;
import com.rubymusic.playlist.config.SecurityConfig;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.service.PlaylistService;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link InternalPlaylistController} with full security chain.
 *
 * <p>Verifies the zero-trust security contract:
 * <ul>
 *   <li>Service JWT (ROLE_SERVICE) → 200 on all internal endpoints</li>
 *   <li>User JWT (ROLE_USER)       → 403 Forbidden on internal endpoints</li>
 *   <li>No JWT                     → 401 Unauthorized on internal endpoints</li>
 * </ul>
 *
 * <p>Also verifies dual-path mapping: both old {@code /api/v1/playlists/internal/**}
 * and new {@code /api/internal/v1/playlists/**} paths are secured.
 *
 * TDD cycle:
 *   RED  — SecurityConfig does not exist yet → compile fails
 *   GREEN — SecurityConfig + updated controller wired → all pass
 */
@WebMvcTest(InternalPlaylistController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class, JwtAuthenticationFilter.class})
@ActiveProfiles("test")
@DisplayName("InternalPlaylistController security + behavior")
class InternalPlaylistControllerTest {

    // ── Static RSA key pair shared across all test instances ──────────────────

    static final KeyPair KEY_PAIR;

    static {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KEY_PAIR = gen.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate test RSA key pair", e);
        }
    }

    /** Provides the test PublicKey — overrides JwtConfig via @ConditionalOnMissingBean */
    @TestConfiguration
    static class SecurityTestConfig {
        @Bean
        public PublicKey testPublicKey() {
            return KEY_PAIR.getPublic();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PlaylistService playlistService;

    private String serviceToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        Date now = new Date();
        Date exp = new Date(now.getTime() + 60_000);

        serviceToken = Jwts.builder()
                .subject("interaction-service")
                .claim("role", "SERVICE")
                .issuedAt(now)
                .expiration(exp)
                .signWith(KEY_PAIR.getPrivate())
                .compact();

        userToken = Jwts.builder()
                .subject("user-abc-123")
                .claim("role", "USER")
                .issuedAt(now)
                .expiration(exp)
                .signWith(KEY_PAIR.getPrivate())
                .compact();

        // Default stub: createSystemPlaylist returns a system playlist
        Playlist systemPlaylist = Playlist.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .name("Tus me gusta")
                .isSystem(true)
                .isPublic(false)
                .build();
        when(playlistService.createSystemPlaylist(any())).thenReturn(systemPlaylist);
        when(playlistService.addSong(any(), any(), any())).thenReturn(null);
    }

    // ── POST /api/internal/v1/playlists/system/songs (new path) ───────────────

    @Test
    @DisplayName("POST /api/internal/v1/playlists/system/songs — service JWT → 200")
    void addSongToSystemPlaylist_newPath_withServiceJwt_returns200() throws Exception {
        Map<String, String> body = Map.of(
                "userId", UUID.randomUUID().toString(),
                "songId", UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/internal/v1/playlists/system/songs")
                        .header("Authorization", "Bearer " + serviceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/internal/v1/playlists/system/songs — user JWT → 403")
    void addSongToSystemPlaylist_newPath_withUserJwt_returns403() throws Exception {
        Map<String, String> body = Map.of(
                "userId", UUID.randomUUID().toString(),
                "songId", UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/internal/v1/playlists/system/songs")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/internal/v1/playlists/system/songs — no JWT → 401")
    void addSongToSystemPlaylist_newPath_noJwt_returns401() throws Exception {
        Map<String, String> body = Map.of(
                "userId", UUID.randomUUID().toString(),
                "songId", UUID.randomUUID().toString()
        );

        mockMvc.perform(post("/api/internal/v1/playlists/system/songs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/v1/playlists/internal/system/{userId} (old path, backward compat) ──

    @Test
    @DisplayName("POST /api/v1/playlists/internal/system/{userId} — service JWT → 200 (backward compat)")
    void createSystemPlaylist_oldPath_withServiceJwt_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/internal/system/{userId}", UUID.randomUUID())
                        .header("Authorization", "Bearer " + serviceToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/playlists/internal/system/{userId} — no JWT → 401 (backward compat)")
    void createSystemPlaylist_oldPath_noJwt_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/playlists/internal/system/{userId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
