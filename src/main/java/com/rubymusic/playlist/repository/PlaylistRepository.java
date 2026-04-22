package com.rubymusic.playlist.repository;

import com.rubymusic.playlist.model.Playlist;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

    /** Active playlists owned by the user (excludes soft-deleted).
     *  @EntityGraph eagerly fetches songs to avoid an N+1 when PlaylistMapper
     *  accesses playlist.getSongs().size() after the transaction closes
     *  (spring.jpa.open-in-view = false). */
    @EntityGraph(attributePaths = {"songs"})
    List<Playlist> findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);

    /** Same rationale: songs must be loaded within this query so the mapper
     *  can read the collection without an open session. */
    @EntityGraph(attributePaths = {"songs"})
    Optional<Playlist> findByIdAndDeletedAtIsNull(UUID id);

    /** Returns the "Tus me gusta" system playlist for the user */
    Optional<Playlist> findByUserIdAndIsSystemTrueAndDeletedAtIsNull(UUID userId);

    /** Returns all active system playlists for the user.
     *  Usado por el handler interno de remove-song para cubrir el caso de
     *  duplicados legacy: la canción puede estar en una system playlist distinta
     *  de la que devuelve `findByUserIdAndIsSystemTrueAndDeletedAtIsNull`, y hay
     *  que eliminarla de todas las activas del usuario. */
    List<Playlist> findAllByUserIdAndIsSystemTrueAndDeletedAtIsNull(UUID userId);
}
