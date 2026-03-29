package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal endpoints for service-to-service calls only.
 * Not exposed through the public OpenAPI contract.
 * Called by: auth-service (on user registration) and interaction-service (on song like/unlike).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/playlists/internal")
public class InternalPlaylistController {

    private final PlaylistService playlistService;

    @PostMapping("/system/{userId}")
    @Transactional
    public ResponseEntity<Void> createSystemPlaylist(@PathVariable UUID userId) {
        playlistService.createSystemPlaylist(userId);
        log.debug("System playlist ensured for user: {}", userId);
        return ResponseEntity.ok().build();
    }

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
        var systemPlaylist = playlistService.createSystemPlaylist(userId);
        playlistService.removeSong(systemPlaylist.getId(), userId, songId);
        return ResponseEntity.noContent().build();
    }
}
