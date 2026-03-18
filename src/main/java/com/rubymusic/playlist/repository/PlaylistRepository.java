package com.rubymusic.playlist.repository;

import com.rubymusic.playlist.model.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    /** Active playlists owned by the user (excludes soft-deleted) */
    List<Playlist> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    Optional<Playlist> findByIdAndDeletedAtIsNull(UUID id);

    /** Returns the "Tus me gusta" system playlist for the user */
    Optional<Playlist> findByUserIdAndIsSystemTrueAndDeletedAtIsNull(UUID userId);
}
