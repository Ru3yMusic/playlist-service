package com.rubymusic.playlist.repository;

import com.rubymusic.playlist.model.Playlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /**
     * Public playlists from any user (excludes system "Tus me gusta" and soft-deleted),
     * newest first. Powers the "Playlist Recomendadas" section in /user/music.
     * Songs eagerly loaded for songCount computation in the mapper.
     */
    @EntityGraph(attributePaths = {"songs"})
    Page<Playlist> findAllByIsPublicTrueAndIsSystemFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Loads playlists by ID with songs eager-fetched, filtered to those still
     * publicly visible. The SERVICE re-orders the result by the input ID order
     * (since SQL IN does not preserve order). Used by step 2 of saved-playlists
     * resolution — see PlaylistSavedByUserRepository.findSavedPlaylistIdsOrdered.
     */
    @EntityGraph(attributePaths = {"songs"})
    List<Playlist> findAllByIdInAndIsPublicTrueAndIsSystemFalseAndDeletedAtIsNull(List<UUID> ids);

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
