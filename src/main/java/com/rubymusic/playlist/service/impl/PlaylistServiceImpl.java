package com.rubymusic.playlist.service.impl;

import com.rubymusic.playlist.event.MusicFeedEventPublisher;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSong;
import com.rubymusic.playlist.model.PlaylistSavedByUser;
import com.rubymusic.playlist.repository.PlaylistRepository;
import com.rubymusic.playlist.repository.PlaylistSavedByUserRepository;
import com.rubymusic.playlist.repository.PlaylistSongRepository;
import com.rubymusic.playlist.service.PlaylistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistSongRepository playlistSongRepository;
    private final PlaylistSavedByUserRepository playlistSavedByUserRepository;
    private final MusicFeedEventPublisher musicFeedEventPublisher;

    @Override
    @Transactional
    public Playlist create(UUID userId, String name, String description, boolean isPublic) {
        Playlist playlist = Playlist.builder()
                .userId(userId)
                .name(name)
                .description(description)
                .isPublic(isPublic)
                .build();
        Playlist saved = playlistRepository.save(playlist);

        // Public, non-system playlists are the only thing that goes into the
        // global feed. System playlists ("Tus me gusta") are NEVER shareable.
        if (isPublic && !Boolean.TRUE.equals(saved.getIsSystem())) {
            musicFeedEventPublisher.publishPublicCreated(saved);
        }
        return saved;
    }

    @Override
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
    public Page<Playlist> findPublic(Pageable pageable) {
        return playlistRepository
                .findAllByIsPublicTrueAndIsSystemFalseAndDeletedAtIsNullOrderByCreatedAtDesc(pageable);
    }

    @Override
    @Transactional
    public Playlist update(UUID playlistId, UUID requestingUserId,
                           String name, String description, String coverUrl, Boolean isPublic) {
        Playlist playlist = findAndVerifyOwner(playlistId, requestingUserId);
        boolean wasPublic = Boolean.TRUE.equals(playlist.getIsPublic());

        if (name != null && !name.isBlank() && !playlist.getIsSystem()) {
            playlist.setName(name);
        }
        if (description != null) playlist.setDescription(description);
        if (coverUrl != null) playlist.setCoverUrl(coverUrl);

        // Cascade rule (borrón duro): if the owner flips public → private,
        // every user who saved this playlist loses the link. Detect transition
        // BEFORE setting the new value.
        boolean transitioningToPrivate =
                isPublic != null
                        && !isPublic
                        && wasPublic;

        if (isPublic != null) playlist.setIsPublic(isPublic);

        Playlist saved = playlistRepository.save(playlist);

        if (transitioningToPrivate) {
            int dropped = playlistSavedByUserRepository.deleteAllByPlaylistId(playlistId);
            if (dropped > 0) {
                log.info("Playlist {} went private — dropped {} saved-by-user link(s)",
                        playlistId, dropped);
            }
        }

        // Emit privacy-changed only on actual flip and only for non-system
        // playlists. System playlists are private by design and never appear
        // in the music feed regardless of any (impossible) flag flip.
        boolean nowPublic = Boolean.TRUE.equals(saved.getIsPublic());
        if (wasPublic != nowPublic && !Boolean.TRUE.equals(saved.getIsSystem())) {
            musicFeedEventPublisher.publishPrivacyChanged(saved);
        }
        return saved;
    }

    @Override
    @Transactional
    public void softDelete(UUID playlistId, UUID requestingUserId) {
        Playlist playlist = findAndVerifyOwner(playlistId, requestingUserId);
        if (playlist.getIsSystem()) {
            throw new IllegalStateException("System playlists cannot be deleted");
        }
        boolean wasPublic = Boolean.TRUE.equals(playlist.getIsPublic());
        playlist.setDeletedAt(LocalDateTime.now());
        playlistRepository.save(playlist);

        // Same cascade as privacy flip — if the owner deletes, savers lose it.
        int dropped = playlistSavedByUserRepository.deleteAllByPlaylistId(playlistId);
        if (dropped > 0) {
            log.info("Playlist {} soft-deleted — dropped {} saved-by-user link(s)",
                    playlistId, dropped);
        }

        // Only emit if the playlist was visible publicly. Private playlists were
        // never in the feed, no one needs to know they vanished.
        if (wasPublic) {
            musicFeedEventPublisher.publishDeleted(playlistId);
        }
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

    @Override
    @Transactional
    public void savePlaylistForUser(UUID playlistId, UUID requestingUserId) {
        Playlist target = findById(playlistId); // throws if not found / soft-deleted

        if (target.getUserId().equals(requestingUserId)) {
            throw new IllegalArgumentException("Cannot save your own playlist");
        }
        if (Boolean.TRUE.equals(target.getIsSystem())) {
            throw new IllegalArgumentException("System playlists cannot be saved");
        }
        if (!Boolean.TRUE.equals(target.getIsPublic())) {
            throw new IllegalArgumentException("Cannot save a private playlist");
        }

        // Idempotent — return silently if already saved.
        if (playlistSavedByUserRepository
                .findByUserIdAndPlaylistId(requestingUserId, playlistId).isPresent()) {
            return;
        }

        playlistSavedByUserRepository.save(PlaylistSavedByUser.builder()
                .userId(requestingUserId)
                .playlistId(playlistId)
                .build());
    }

    @Override
    @Transactional
    public void unsavePlaylistForUser(UUID playlistId, UUID requestingUserId) {
        playlistSavedByUserRepository
                .findByUserIdAndPlaylistId(requestingUserId, playlistId)
                .ifPresent(playlistSavedByUserRepository::delete);
    }

    @Override
    public List<Playlist> findSavedByUser(UUID userId) {
        // Two-step: get the IDs in saved-order, then load the entities with songs.
        // PostgreSQL won't accept DISTINCT + ORDER BY <non-selected col> + JOIN FETCH
        // collection in a single query, so we split.
        List<UUID> orderedIds = playlistSavedByUserRepository.findSavedPlaylistIdsOrdered(userId);
        if (orderedIds.isEmpty()) {
            return List.of();
        }

        List<Playlist> visible = playlistRepository
                .findAllByIdInAndIsPublicTrueAndIsSystemFalseAndDeletedAtIsNull(orderedIds);

        // Re-order to match savedAt DESC (SQL IN does not preserve order).
        Map<UUID, Playlist> byId = visible.stream()
                .collect(Collectors.toMap(Playlist::getId, Function.identity()));
        return orderedIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .toList();
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
