package com.rubymusic.playlist.repository;

import com.rubymusic.playlist.model.PlaylistSavedByUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistSavedByUserRepository extends JpaRepository<PlaylistSavedByUser, UUID> {

    /** Used by save/unsave to detect existing record (idempotent save, valid unsave). */
    Optional<PlaylistSavedByUser> findByUserIdAndPlaylistId(UUID userId, UUID playlistId);

    /**
     * Step 1 of the saved-playlists lookup: returns the playlist IDs the user
     * saved, ordered newest-first by savedAt. Step 2 (in PlaylistRepository)
     * loads the actual entities with their songs.
     *
     * <p>We split into two queries because PostgreSQL rejects
     * {@code SELECT DISTINCT ... ORDER BY <col not in SELECT>}, which is what
     * the single-query approach with JOIN FETCH on the songs collection
     * produces. Two small queries are simpler than fighting the rule.
     */
    @Query("""
            SELECT s.playlistId FROM PlaylistSavedByUser s
            WHERE s.userId = :userId
            ORDER BY s.savedAt DESC
            """)
    List<UUID> findSavedPlaylistIdsOrdered(@Param("userId") UUID userId);

    /** Cascade delete called when a playlist transitions to private or is soft-deleted. */
    @Modifying
    @Query("DELETE FROM PlaylistSavedByUser s WHERE s.playlistId = :playlistId")
    int deleteAllByPlaylistId(@Param("playlistId") UUID playlistId);
}
