package com.rubymusic.playlist.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "playlist_songs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_playlist_song",
                columnNames = {"playlist_id", "song_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistSong {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    /** References catalog-service song — no cross-service FK */
    @Column(name = "song_id", nullable = false)
    private UUID songId;

    /** Integer position for drag-and-drop reordering */
    @Column(nullable = false)
    private Integer position;

    @Column(name = "added_at", nullable = false)
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();
}
