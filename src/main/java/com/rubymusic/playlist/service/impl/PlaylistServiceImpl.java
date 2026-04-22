package com.rubymusic.playlist.service.impl;

import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSong;
import com.rubymusic.playlist.repository.PlaylistRepository;
import com.rubymusic.playlist.repository.PlaylistSongRepository;
import com.rubymusic.playlist.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;

    @Override
    @Transactional
    public Playlist create(UUID userId, String name, String description, boolean isPublic) {
        Playlist playlist = Playlist.builder()
                .userId(userId)
                .name(name)
                .description(description)
                .isPublic(isPublic)
                .build();
        return playlistRepository.save(playlist);
    }

    @Override
    @Transactional
    public Playlist createSystemPlaylist(UUID userId) {
        // Idempotent — only one system playlist per user
        return playlistRepository.findByUserIdAndIsSystemTrueAndDeletedAtIsNull(userId)
                .orElseGet(() -> {
                    Playlist system = Playlist.builder()
                            .userId(userId)
                            .name("Tus me gusta")
                            .isPublic(false)
                            .isSystem(true)
                            .build();
                    log.info("Creating system playlist for user: {}", userId);
                    return playlistRepository.save(system);
                });
    }

    @Override
    public Playlist findById(UUID playlistId) {
        return playlistRepository.findByIdAndDeletedAtIsNull(playlistId)
                .orElseThrow(() -> new IllegalArgumentException("Playlist not found: " + playlistId));
    }

    @Override
    @Transactional
    public List<Playlist> findByUserId(UUID userId) {
        // Asegura que exista la system playlist "Tus me gusta" para este usuario
        // antes de listar. Idempotente — si ya existe, no crea duplicados.
        createSystemPlaylist(userId);
        return playlistRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public Playlist update(UUID playlistId, UUID requestingUserId,
                           String name, String description, String coverUrl, Boolean isPublic) {
        Playlist playlist = findAndVerifyOwner(playlistId, requestingUserId);

        if (name != null && !name.isBlank() && !playlist.getIsSystem()) {
            playlist.setName(name);
        }
        if (description != null) playlist.setDescription(description);
        if (coverUrl != null) playlist.setCoverUrl(coverUrl);
        if (isPublic != null) playlist.setIsPublic(isPublic);

        return playlistRepository.save(playlist);
    }

    @Override
    @Transactional
    public void softDelete(UUID playlistId, UUID requestingUserId) {
        Playlist playlist = findAndVerifyOwner(playlistId, requestingUserId);
        if (playlist.getIsSystem()) {
            throw new IllegalStateException("System playlists cannot be deleted");
        }
        playlist.setDeletedAt(LocalDateTime.now());
        playlistRepository.save(playlist);
    }

    @Override
    @Transactional
    public PlaylistSong addSong(UUID playlistId, UUID requestingUserId, UUID songId) {
        Playlist playlist = findAndVerifyOwner(playlistId, requestingUserId);

        if (playlistSongRepository.existsByPlaylistIdAndSongId(playlistId, songId)) {
            return playlistSongRepository.findByPlaylistIdAndSongId(playlistId, songId).orElseThrow();
        }

        int nextPosition = playlistSongRepository.findMaxPositionByPlaylistId(playlistId) + 1;
        PlaylistSong entry = PlaylistSong.builder()
                .playlist(playlist)
                .songId(songId)
                .position(nextPosition)
                .build();
        return playlistSongRepository.save(entry);
    }

    @Override
    @Transactional
    public void removeSong(UUID playlistId, UUID requestingUserId, UUID songId) {
        findAndVerifyOwner(playlistId, requestingUserId);
        // Use the explicit @Modifying DELETE so the row is guaranteed to leave
        // the DB even when the parent Playlist's `songs` collection is
        // already in the persistence context (orphanRemoval reconciliation
        // could otherwise silently skip the delete).
        int removed = playlistSongRepository.deleteByPlaylistIdAndSongId(playlistId, songId);
        log.debug("removeSong playlist={} song={} rowsDeleted={}", playlistId, songId, removed);
    }

    @Override
    @Transactional
    public void reorderSongs(UUID playlistId, UUID requestingUserId, List<UUID> orderedSongIds) {
        findAndVerifyOwner(playlistId, requestingUserId);

        List<PlaylistSong> songs = playlistSongRepository.findAllByPlaylistIdOrderByPosition(playlistId);

        for (int i = 0; i < orderedSongIds.size(); i++) {
            UUID songId = orderedSongIds.get(i);
            final int position = i;
            songs.stream()
                    .filter(ps -> ps.getSongId().equals(songId))
                    .findFirst()
                    .ifPresent(ps -> ps.setPosition(position));
        }
        playlistSongRepository.saveAll(songs);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Playlist findAndVerifyOwner(UUID playlistId, UUID requestingUserId) {
        Playlist playlist = findById(playlistId);
        if (!playlist.getUserId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Access denied to playlist: " + playlistId);
        }
        return playlist;
    }
}
