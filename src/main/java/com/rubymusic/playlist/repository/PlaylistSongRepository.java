package com.rubymusic.playlist.repository;

import com.rubymusic.playlist.model.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, UUID> {

    List<PlaylistSong> findAllByPlaylistIdOrderByPosition(UUID playlistId);

    Optional<PlaylistSong> findByPlaylistIdAndSongId(UUID playlistId, UUID songId);

    boolean existsByPlaylistIdAndSongId(UUID playlistId, UUID songId);

    /**
     * Reliable DELETE that bypasses the parent's {@code @OneToMany(orphanRemoval=true)}
     * collection. Calling {@code playlistSongRepository.delete(child)} can leave the
     * parent's in-memory collection referencing the deleted child and, on flush,
     * Hibernate has been observed to re-persist or silently skip the removal.
     * This JPQL statement hits the DB directly and returns the affected row count
     * so the caller can tell whether the song was actually in the playlist.
     */
    @Modifying
    @Query("DELETE FROM PlaylistSong ps " +
           "WHERE ps.playlist.id = :playlistId AND ps.songId = :songId")
    int deleteByPlaylistIdAndSongId(@Param("playlistId") UUID playlistId,
                                    @Param("songId") UUID songId);

    /** Returns the highest position value in the playlist, used when appending */
    @Query("SELECT COALESCE(MAX(ps.position), -1) FROM PlaylistSong ps WHERE ps.playlist.id = :playlistId")
    int findMaxPositionByPlaylistId(UUID playlistId);

    /**
     * Borra la canción de TODAS las system playlists ACTIVAS del usuario en una sola query,
     * sin pasar por ownership ni por iterar entidades. Cubre el caso de duplicados legacy
     * para que el unlike quede real. No toca system playlists soft-deleted (deleted_at IS NOT NULL).
     * Devuelve el número de filas borradas — útil para log/diagnóstico.
     */
    @Modifying
    @Query("DELETE FROM PlaylistSong ps " +
           "WHERE ps.songId = :songId " +
           "AND ps.playlist.id IN (" +
           "  SELECT p.id FROM Playlist p " +
           "  WHERE p.userId = :userId " +
           "    AND p.isSystem = TRUE " +
           "    AND p.deletedAt IS NULL" +
           ")")
    int deleteFromUserSystemPlaylistsBySong(@Param("userId") UUID userId,
                                            @Param("songId") UUID songId);
}
