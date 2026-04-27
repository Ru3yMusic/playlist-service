package com.rubymusic.playlist.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Emitted when a user creates a NEW playlist with {@code isPublic=true}.
 * Never emitted for system playlists (Tus me gusta) — those are private by
 * design and not shareable.
 *
 * <p>{@code songCount} is included for consistency with other feed events,
 * even though it's always 0 at creation.
 */
public record PlaylistPublicCreatedEvent(
        UUID playlistId,
        UUID userId,
        String name,
        String description,
        String coverUrl,
        int songCount,
        LocalDateTime createdAt
) {}
