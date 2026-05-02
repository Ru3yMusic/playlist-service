package com.rubymusic.playlist.service.impl;

import com.rubymusic.playlist.event.MusicFeedEventPublisher;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSavedByUser;
import com.rubymusic.playlist.model.PlaylistSong;
import com.rubymusic.playlist.repository.PlaylistRepository;
import com.rubymusic.playlist.repository.PlaylistSavedByUserRepository;
import com.rubymusic.playlist.repository.PlaylistSongRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceImplTest {

    @Mock private PlaylistRepository playlistRepository;
    @Mock private PlaylistSongRepository playlistSongRepository;
    @Mock private PlaylistSavedByUserRepository playlistSavedByUserRepository;
    @Mock private MusicFeedEventPublisher musicFeedEventPublisher;

    @InjectMocks private PlaylistServiceImpl service;

    private static final UUID USER = UUID.randomUUID();
    private static final UUID OTHER_USER = UUID.randomUUID();
    private static final UUID PLAYLIST = UUID.randomUUID();
    private static final UUID SONG = UUID.randomUUID();

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_publicPlaylist_persistsAndPublishesEvent() {
        Playlist saved = Playlist.builder().id(PLAYLIST).userId(USER).name("List").isPublic(true).isSystem(false).build();
        when(playlistRepository.save(any(Playlist.class))).thenReturn(saved);

        Playlist result = service.create(USER, "List", "desc", true);

        assertThat(result.getName()).isEqualTo("List");
        verify(musicFeedEventPublisher).publishPublicCreated(saved);
    }

    @Test
    void create_privatePlaylist_doesNotPublishEvent() {
        Playlist saved = Playlist.builder().id(PLAYLIST).userId(USER).isPublic(false).isSystem(false).build();
        when(playlistRepository.save(any(Playlist.class))).thenReturn(saved);

        service.create(USER, "List", "desc", false);

        verify(musicFeedEventPublisher, never()).publishPublicCreated(any());
    }

    @Test
    void create_publicSystemPlaylist_doesNotPublishEvent() {
        // Edge case — system playlists are NEVER in the feed even if isPublic=true
        Playlist saved = Playlist.builder().id(PLAYLIST).userId(USER).isPublic(true).isSystem(true).build();
        when(playlistRepository.save(any(Playlist.class))).thenReturn(saved);

        service.create(USER, "Sys", null, true);

        verify(musicFeedEventPublisher, never()).publishPublicCreated(any());
    }

    // ── createSystemPlaylist ──────────────────────────────────────────────────

    @Test
    void createSystemPlaylist_existing_returnsIt_noSave() {
        Playlist existing = Playlist.builder().id(PLAYLIST).userId(USER).isSystem(true).build();
        when(playlistRepository.findByUserIdAndIsSystemTrueAndDeletedAtIsNull(USER))
                .thenReturn(Optional.of(existing));

        Playlist result = service.createSystemPlaylist(USER);

        assertThat(result).isSameAs(existing);
        verify(playlistRepository, never()).save(any());
    }

    @Test
    void createSystemPlaylist_absent_createsNew() {
        when(playlistRepository.findByUserIdAndIsSystemTrueAndDeletedAtIsNull(USER))
                .thenReturn(Optional.empty());
        when(playlistRepository.save(any(Playlist.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.createSystemPlaylist(USER);

        ArgumentCaptor<Playlist> captor = ArgumentCaptor.forClass(Playlist.class);
        verify(playlistRepository).save(captor.capture());
        assertThat(captor.getValue().getIsSystem()).isTrue();
        assertThat(captor.getValue().getName()).isEqualTo("Tus me gusta");
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    void findById_existing_returnsIt() {
        Playlist p = Playlist.builder().id(PLAYLIST).userId(USER).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(p));

        assertThat(service.findById(PLAYLIST)).isSameAs(p);
    }

    @Test
    void findById_missing_throwsIllegalArgument() {
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(PLAYLIST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Playlist not found");
    }

    // ── findByUserId ──────────────────────────────────────────────────────────

    @Test
    void findByUserId_ensuresSystemPlaylistAndReturnsList() {
        Playlist sys = Playlist.builder().id(UUID.randomUUID()).userId(USER).isSystem(true).build();
        when(playlistRepository.findByUserIdAndIsSystemTrueAndDeletedAtIsNull(USER))
                .thenReturn(Optional.of(sys));
        when(playlistRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(USER))
                .thenReturn(List.of(sys));

        List<Playlist> result = service.findByUserId(USER);

        assertThat(result).containsExactly(sys);
    }

    // ── findPublic ────────────────────────────────────────────────────────────

    @Test
    void findPublic_delegatesToRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Playlist> page = new PageImpl<>(List.of());
        when(playlistRepository
                .findAllByIsPublicTrueAndIsSystemFalseAndDeletedAtIsNullOrderByCreatedAtDesc(pageable))
                .thenReturn(page);

        assertThat(service.findPublic(pageable)).isSameAs(page);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_publicToPrivate_dropsSavedLinks_andPublishesEvent() {
        Playlist existing = Playlist.builder()
                .id(PLAYLIST).userId(USER)
                .name("Old").isPublic(true).isSystem(false)
                .build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));
        when(playlistRepository.save(existing)).thenReturn(existing);
        when(playlistSavedByUserRepository.deleteAllByPlaylistId(PLAYLIST)).thenReturn(3);

        service.update(PLAYLIST, USER, "New", "desc", "url", false);

        assertThat(existing.getName()).isEqualTo("New");
        assertThat(existing.getIsPublic()).isFalse();
        verify(playlistSavedByUserRepository).deleteAllByPlaylistId(PLAYLIST);
        verify(musicFeedEventPublisher).publishPrivacyChanged(existing);
    }

    @Test
    void update_privateToPublic_publishesPrivacyChanged_noDelete() {
        Playlist existing = Playlist.builder()
                .id(PLAYLIST).userId(USER)
                .isPublic(false).isSystem(false)
                .build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));
        when(playlistRepository.save(existing)).thenReturn(existing);

        service.update(PLAYLIST, USER, null, null, null, true);

        verify(musicFeedEventPublisher).publishPrivacyChanged(existing);
        verify(playlistSavedByUserRepository, never()).deleteAllByPlaylistId(any());
    }

    @Test
    void update_systemPlaylist_doesNotChangeName() {
        Playlist existing = Playlist.builder()
                .id(PLAYLIST).userId(USER)
                .name("Tus me gusta").isPublic(false).isSystem(true)
                .build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));
        when(playlistRepository.save(existing)).thenReturn(existing);

        service.update(PLAYLIST, USER, "Trying to rename", null, null, null);

        assertThat(existing.getName()).isEqualTo("Tus me gusta");
    }

    @Test
    void update_notOwner_throws() {
        Playlist existing = Playlist.builder().id(PLAYLIST).userId(OTHER_USER).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(PLAYLIST, USER, "x", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Access denied");
    }

    // ── softDelete ────────────────────────────────────────────────────────────

    @Test
    void softDelete_publicPlaylist_setsDeletedAt_dropsSaved_publishesEvent() {
        Playlist existing = Playlist.builder()
                .id(PLAYLIST).userId(USER)
                .isPublic(true).isSystem(false)
                .build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));
        when(playlistSavedByUserRepository.deleteAllByPlaylistId(PLAYLIST)).thenReturn(2);

        service.softDelete(PLAYLIST, USER);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(playlistSavedByUserRepository).deleteAllByPlaylistId(PLAYLIST);
        verify(musicFeedEventPublisher).publishDeleted(PLAYLIST);
    }

    @Test
    void softDelete_privatePlaylist_doesNotPublishEvent() {
        Playlist existing = Playlist.builder()
                .id(PLAYLIST).userId(USER)
                .isPublic(false).isSystem(false)
                .build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));

        service.softDelete(PLAYLIST, USER);

        verify(musicFeedEventPublisher, never()).publishDeleted(any());
    }

    @Test
    void softDelete_systemPlaylist_throws() {
        Playlist existing = Playlist.builder()
                .id(PLAYLIST).userId(USER).isSystem(true).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.softDelete(PLAYLIST, USER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("System playlists cannot be deleted");
    }

    // ── addSong ───────────────────────────────────────────────────────────────

    @Test
    void addSong_newSong_persistsWithNextPosition() {
        Playlist owner = Playlist.builder().id(PLAYLIST).userId(USER).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(owner));
        when(playlistSongRepository.existsByPlaylistIdAndSongId(PLAYLIST, SONG)).thenReturn(false);
        when(playlistSongRepository.findMaxPositionByPlaylistId(PLAYLIST)).thenReturn(4);
        when(playlistSongRepository.save(any(PlaylistSong.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        PlaylistSong result = service.addSong(PLAYLIST, USER, SONG);

        assertThat(result.getPosition()).isEqualTo(5);
        assertThat(result.getSongId()).isEqualTo(SONG);
    }

    @Test
    void addSong_alreadyPresent_returnsExisting() {
        Playlist owner = Playlist.builder().id(PLAYLIST).userId(USER).build();
        PlaylistSong existing = PlaylistSong.builder().songId(SONG).position(2).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(owner));
        when(playlistSongRepository.existsByPlaylistIdAndSongId(PLAYLIST, SONG)).thenReturn(true);
        when(playlistSongRepository.findByPlaylistIdAndSongId(PLAYLIST, SONG))
                .thenReturn(Optional.of(existing));

        PlaylistSong result = service.addSong(PLAYLIST, USER, SONG);

        assertThat(result).isSameAs(existing);
        verify(playlistSongRepository, never()).save(any());
    }

    @Test
    void addSong_notOwner_throws() {
        Playlist existing = Playlist.builder().id(PLAYLIST).userId(OTHER_USER).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.addSong(PLAYLIST, USER, SONG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Access denied");
    }

    // ── removeSong ────────────────────────────────────────────────────────────

    @Test
    void removeSong_delegatesToRepository() {
        Playlist owner = Playlist.builder().id(PLAYLIST).userId(USER).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(owner));

        service.removeSong(PLAYLIST, USER, SONG);

        verify(playlistSongRepository).deleteByPlaylistIdAndSongId(PLAYLIST, SONG);
    }

    // ── reorderSongs ──────────────────────────────────────────────────────────

    @Test
    void reorderSongs_updatesPositionsByOrder() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();
        UUID s3 = UUID.randomUUID();
        PlaylistSong ps1 = PlaylistSong.builder().songId(s1).position(0).build();
        PlaylistSong ps2 = PlaylistSong.builder().songId(s2).position(1).build();
        PlaylistSong ps3 = PlaylistSong.builder().songId(s3).position(2).build();

        Playlist owner = Playlist.builder().id(PLAYLIST).userId(USER).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(owner));
        when(playlistSongRepository.findAllByPlaylistIdOrderByPosition(PLAYLIST))
                .thenReturn(List.of(ps1, ps2, ps3));

        service.reorderSongs(PLAYLIST, USER, List.of(s3, s1, s2));

        assertThat(ps3.getPosition()).isZero();
        assertThat(ps1.getPosition()).isEqualTo(1);
        assertThat(ps2.getPosition()).isEqualTo(2);
        verify(playlistSongRepository).saveAll(List.of(ps1, ps2, ps3));
    }

    // ── savePlaylistForUser ───────────────────────────────────────────────────

    @Test
    void savePlaylistForUser_publicAndAlien_persistsLink() {
        Playlist target = Playlist.builder()
                .id(PLAYLIST).userId(OTHER_USER).isPublic(true).isSystem(false).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(target));
        when(playlistSavedByUserRepository.findByUserIdAndPlaylistId(USER, PLAYLIST))
                .thenReturn(Optional.empty());

        service.savePlaylistForUser(PLAYLIST, USER);

        verify(playlistSavedByUserRepository).save(any(PlaylistSavedByUser.class));
    }

    @Test
    void savePlaylistForUser_alreadySaved_isIdempotent() {
        Playlist target = Playlist.builder()
                .id(PLAYLIST).userId(OTHER_USER).isPublic(true).isSystem(false).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(target));
        when(playlistSavedByUserRepository.findByUserIdAndPlaylistId(USER, PLAYLIST))
                .thenReturn(Optional.of(mock(PlaylistSavedByUser.class)));

        service.savePlaylistForUser(PLAYLIST, USER);

        verify(playlistSavedByUserRepository, never()).save(any());
    }

    @Test
    void savePlaylistForUser_ownPlaylist_throws() {
        Playlist target = Playlist.builder().id(PLAYLIST).userId(USER).isPublic(true).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.savePlaylistForUser(PLAYLIST, USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("own playlist");
    }

    @Test
    void savePlaylistForUser_systemPlaylist_throws() {
        Playlist target = Playlist.builder()
                .id(PLAYLIST).userId(OTHER_USER).isPublic(true).isSystem(true).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.savePlaylistForUser(PLAYLIST, USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("System playlists");
    }

    @Test
    void savePlaylistForUser_privatePlaylist_throws() {
        Playlist target = Playlist.builder()
                .id(PLAYLIST).userId(OTHER_USER).isPublic(false).isSystem(false).build();
        when(playlistRepository.findByIdAndDeletedAtIsNull(PLAYLIST)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.savePlaylistForUser(PLAYLIST, USER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("private");
    }

    // ── unsavePlaylistForUser ─────────────────────────────────────────────────

    @Test
    void unsavePlaylistForUser_existing_deletes() {
        PlaylistSavedByUser link = mock(PlaylistSavedByUser.class);
        when(playlistSavedByUserRepository.findByUserIdAndPlaylistId(USER, PLAYLIST))
                .thenReturn(Optional.of(link));

        service.unsavePlaylistForUser(PLAYLIST, USER);

        verify(playlistSavedByUserRepository).delete(link);
    }

    @Test
    void unsavePlaylistForUser_notSaved_doesNothing() {
        when(playlistSavedByUserRepository.findByUserIdAndPlaylistId(USER, PLAYLIST))
                .thenReturn(Optional.empty());

        service.unsavePlaylistForUser(PLAYLIST, USER);

        verify(playlistSavedByUserRepository, never()).delete(any());
    }

    // ── findSavedByUser ───────────────────────────────────────────────────────

    @Test
    void findSavedByUser_emptyList_returnsEmpty() {
        when(playlistSavedByUserRepository.findSavedPlaylistIdsOrdered(USER))
                .thenReturn(List.of());

        assertThat(service.findSavedByUser(USER)).isEmpty();
    }

    @Test
    void findSavedByUser_preservesSavedAtOrder() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Playlist plA = Playlist.builder().id(idA).build();
        Playlist plB = Playlist.builder().id(idB).build();
        when(playlistSavedByUserRepository.findSavedPlaylistIdsOrdered(USER))
                .thenReturn(List.of(idB, idA));  // savedAt DESC
        when(playlistRepository
                .findAllByIdInAndIsPublicTrueAndIsSystemFalseAndDeletedAtIsNull(List.of(idB, idA)))
                .thenReturn(List.of(plA, plB));  // SQL IN may scramble order

        List<Playlist> result = service.findSavedByUser(USER);

        assertThat(result).containsExactly(plB, plA);  // re-ordered
    }

    @Test
    void findSavedByUser_skipsPlaylistsThatVanishedFromVisible() {
        // A playlist was saved but later went private/system/deleted —
        // findAllByIdIn... no longer returns it; service must skip the null map
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        Playlist plA = Playlist.builder().id(idA).build();
        when(playlistSavedByUserRepository.findSavedPlaylistIdsOrdered(USER))
                .thenReturn(List.of(idA, idB));
        when(playlistRepository
                .findAllByIdInAndIsPublicTrueAndIsSystemFalseAndDeletedAtIsNull(List.of(idA, idB)))
                .thenReturn(List.of(plA));  // idB is not visible anymore

        List<Playlist> result = service.findSavedByUser(USER);

        assertThat(result).containsExactly(plA);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private static <T> T mock(Class<T> klass) {
        return org.mockito.Mockito.mock(klass);
    }
}
