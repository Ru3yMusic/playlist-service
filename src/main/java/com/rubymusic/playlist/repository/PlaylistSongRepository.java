package com.rubymusic.playlist.repository;

import com.rubymusic.playlist.model.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, UUID> {

    List<PlaylistSong> findAllByPlaylistIdOrderByPosition(UUID playlistId);

    Optional<PlaylistSong> findByPlaylistIdAndSongId(UUID playlistId, UUID songId);

    boolean existsByPlaylistIdAndSongId(UUID playlistId, UUID songId);

    /** Returns the highest position value in the playlist, used when appending */
    @Query("SELECT COALESCE(MAX(ps.position), -1) FROM PlaylistSong ps WHERE ps.playlist.id = :playlistId")
    int findMaxPositionByPlaylistId(UUID playlistId);
}
