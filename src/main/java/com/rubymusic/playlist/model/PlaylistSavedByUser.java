package com.rubymusic.playlist.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records that a user has saved another user's PUBLIC playlist into their own
 * library. The saved playlist appears alongside the user's own playlists in
 * "Mi biblioteca", but stays read-only — only the original owner can edit it.
 *
 * <p>Lifecycle rules (enforced by service layer):
 * <ul>
 *   <li>You cannot save your own playlist (would duplicate ownership).</li>
 *   <li>You cannot save a system playlist ("Tus me gusta") of another user.</li>
 *   <li>You cannot save a soft-deleted or non-public playlist.</li>
 *   <li>If the owner flips the playlist from public to private (or soft-deletes
 *       it), all rows referencing that playlist are deleted — see
 *       {@code PlaylistServiceImpl.update / softDelete}. This is the "borrón
 *       duro" rule: when privacy changes, savers lose the link and must
 *       re-save if it ever becomes public again.</li>
 * </ul>
 */
@Entity
@Table(name = "playlist_saved_by_user",
        indexes = {
                @Index(name = "idx_psbu_user_id", columnList = "user_id"),
                @Index(name = "idx_psbu_playlist_id", columnList = "playlist_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_psbu_user_playlist",
                columnNames = {"user_id", "playlist_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistSavedByUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** User who saved the playlist (auth-service users, no FK). */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Target playlist (cross-row reference within playlist-service DB). */
    @Column(name = "playlist_id", nullable = false)
    private UUID playlistId;

    @CreationTimestamp
    @Column(name = "saved_at", nullable = false, updatable = false)
    private LocalDateTime savedAt;
}
