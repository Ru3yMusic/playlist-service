package com.rubymusic.playlist.event;

import java.util.UUID;

/**
 * Emitted only when a previously-PUBLIC playlist is soft-deleted. Private
 * deletions don't broadcast — no one else could see them in the feed anyway.
 *
 * <p>Minimal payload: the consumer just needs to remove the card by id from
 * any open feed/library view.
 */
public record PlaylistDeletedEvent(
        UUID playlistId
) {}
