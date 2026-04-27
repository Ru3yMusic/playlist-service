package com.rubymusic.playlist.service;

import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PlaylistService {

    /** Creates a regular user playlist */
    Playlist create(UUID userId, String name, String description, boolean isPublic);

    /**
     * Auto-createds on user registration — "Tus me gusta".
     * Called by an internal event or by the auth-service via REST.
     */
    Playlist createSystemPlaylist(UUID userId);

    Playlist findById(UUID playlistId);

    List<Playlist> findByUserId(UUID userId);

    /** Paginated public playlists across all users, newest first. */
    Page<Playlist> findPublic(Pageable pageable);

    /**
     * Saves another user's public playlist into the requesting user's library.
     * Idempotent — calling twice with the same pair returns the same record.
     * Throws if the target is not eligible (private, system, soft-deleted, or owned).
     */
    void savePlaylistForUser(UUID playlistId, UUID requestingUserId);

    /** Removes a saved-playlist row. No-op if the user had not saved it. */
    void unsavePlaylistForUser(UUID playlistId, UUID requestingUserId);

    /**
     * Lists playlists the user has saved from other users, filtered to those
     * still publicly visible (excludes private/system/soft-deleted).
     */
    List<Playlist> findSavedByUser(UUID userId);

    Playlist update(UUID playlistId, UUID requestingUserId,
                    String name, String description, String coverUrl, Boolean isPublic);

    /** Soft-delete — supports Undo within a client-side time window */
    void softDelete(UUID playlistId, UUID requestingUserId);

    PlaylistSong addSong(UUID playlistId, UUID requestingUserId, UUID songId);

    void removeSong(UUID playlistId, UUID requestingUserId, UUID songId);

    /**
     * Reorders all songs to match the provided ordered list of song IDs.
     * The list must contain exactly the songs currently in the playlist.
     */
    void reorderSongs(UUID playlistId, UUID requestingUserId, List<UUID> orderedSongIds);
}
