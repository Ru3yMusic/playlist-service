package com.rubymusic.playlist.event;

import com.rubymusic.playlist.model.Playlist;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Domain-side publisher: services call these methods inside their
 * {@code @Transactional} flows. The event is captured into Spring's
 * application context immediately, but the Kafka send is deferred to
 * {@link MusicFeedKafkaBridge} which only fires AFTER the transaction
 * commits — so a rollback never produces a phantom realtime broadcast.
 *
 * <p>All entity-to-DTO mapping happens here while the persistence context
 * is still open, so the bridge can safely serialize the immutable record
 * without lazy-loading risk.
 */
@Component
@RequiredArgsConstructor
public class MusicFeedEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishPublicCreated(Playlist playlist) {
        applicationEventPublisher.publishEvent(new PlaylistPublicCreatedEvent(
                playlist.getId(),
                playlist.getUserId(),
                playlist.getName(),
                playlist.getDescription(),
                playlist.getCoverUrl(),
                songCount(playlist),
                playlist.getCreatedAt()
        ));
    }

    public void publishPrivacyChanged(Playlist playlist) {
        applicationEventPublisher.publishEvent(new PlaylistPrivacyChangedEvent(
                playlist.getId(),
                playlist.getUserId(),
                Boolean.TRUE.equals(playlist.getIsPublic()),
                playlist.getName(),
                playlist.getDescription(),
                playlist.getCoverUrl(),
                songCount(playlist),
                playlist.getCreatedAt()
        ));
    }

    public void publishDeleted(UUID playlistId) {
        applicationEventPublisher.publishEvent(new PlaylistDeletedEvent(playlistId));
    }

    private static int songCount(Playlist playlist) {
        return playlist.getSongs() == null ? 0 : playlist.getSongs().size();
    }
}
