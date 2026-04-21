package com.rubymusic.playlist.service;

import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSong;

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
