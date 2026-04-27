package com.rubymusic.playlist.event;

/**
 * Kafka topic names for the {@code music-feed.*} family — events that drive the
 * realtime UI feed at {@code /user/music}. Mirrors the convention used in
 * catalog-service (album.released, artist.top-changed). Payload is full JSON
 * so the consumer can render the card without refetching from the playlist API.
 */
public final class MusicFeedTopics {

    public static final String PLAYLIST_PUBLIC_CREATED = "music-feed.playlist.public-created";
    public static final String PLAYLIST_PRIVACY_CHANGED = "music-feed.playlist.privacy-changed";
    public static final String PLAYLIST_DELETED = "music-feed.playlist.deleted";

    private MusicFeedTopics() {}
}
