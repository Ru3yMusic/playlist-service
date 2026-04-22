package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.dto.SystemSongRequest;
import com.rubymusic.playlist.repository.PlaylistRepository;
import com.rubymusic.playlist.repository.PlaylistSongRepository;
import com.rubymusic.playlist.service.PlaylistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal endpoints for service-to-service calls only.
 * Not exposed through the public OpenAPI contract.
 *
 * <p>Called by:
 * <ul>
 *   <li>auth-service — on user registration (create system playlist)</li>
 *   <li>interaction-service — on song like/unlike (add/remove from "Tus me gusta")</li>
 * </ul>
 *
 * <p>Dual path mapping supports both the canonical new path ({@code /api/internal/v1/playlists})
 * and the legacy path ({@code /api/v1/playlists/internal}) during migration.
 * Both paths require {@code ROLE_SERVICE} — enforced by {@link com.rubymusic.playlist.config.SecurityConfig}.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/playlists/internal", "/api/internal/v1/playlists"})
public class InternalPlaylistController {

    private final PlaylistService playlistService;
    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;

    // ── System playlist lifecycle ─────────────────────────────────────────────

    @PostMapping("/system/{userId}")
    @Transactional
    public ResponseEntity<Void> createSystemPlaylist(@PathVariable UUID userId) {
        playlistService.createSystemPlaylist(userId);
        log.debug("System playlist ensured for user: {}", userId);
        return ResponseEntity.ok().build();
    }

    // ── Song management (path-variable style — legacy) ────────────────────────

    @PostMapping("/system/{userId}/songs/{songId}")
    @Transactional
    public ResponseEntity<Void> addSongToSystemPlaylist(@PathVariable UUID userId,
                                                        @PathVariable UUID songId) {
        var systemPlaylist = playlistService.createSystemPlaylist(userId);
        playlistService.addSong(systemPlaylist.getId(), userId, songId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/system/{userId}/songs/{songId}")
    @Transactional
    public ResponseEntity<Void> removeSongFromSystemPlaylist(@PathVariable UUID userId,
                                                             @PathVariable UUID songId) {
        int deleted = playlistSongRepository.deleteFromUserSystemPlaylistsBySong(userId, songId);
        log.info("removeSongFromSystemPlaylist user={} song={} deletedRows={}", userId, songId, deleted);
        return ResponseEntity.noContent().build();
    }

    // ── Song management (request body style — new canonical) ──────────────────

    /**
     * Adds a song to the user's system playlist ("Tus me gusta").
     *
     * <p>Idempotent: creates the system playlist if it doesn't exist yet.
     * This is the new canonical internal endpoint called by interaction-service.
     *
     * @param request contains {@code userId} and {@code songId}
     */
    @PostMapping("/system/songs")
    @Transactional
    public ResponseEntity<Void> addSongToSystemPlaylistV2(@Valid @RequestBody SystemSongRequest request) {
        var systemPlaylist = playlistService.createSystemPlaylist(request.getUserId());
        playlistService.addSong(systemPlaylist.getId(), request.getUserId(), request.getSongId());
        log.debug("Song {} added to system playlist for user {}", request.getSongId(), request.getUserId());
        return ResponseEntity.ok().build();
    }
}
