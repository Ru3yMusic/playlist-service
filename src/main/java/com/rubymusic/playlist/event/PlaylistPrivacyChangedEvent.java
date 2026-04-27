package com.rubymusic.playlist.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Emitted on any privacy flip of a non-system playlist. The {@code isPublic}
 * field reflects the NEW state — consumers decide:
 *   - {@code isPublic=true}  → playlist newly visible publicly, add to feed
 *   - {@code isPublic=false} → playlist went private, remove from feed and
 *                              from any saved-from-others view
 */
public record PlaylistPrivacyChangedEvent(
        UUID playlistId,
        UUID userId,
        boolean isPublic,
        String name,
        String description,
        String coverUrl,
        int songCount,
        LocalDateTime createdAt
) {}
