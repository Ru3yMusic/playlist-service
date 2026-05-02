package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.dto.PlaylistResponse;
import com.rubymusic.playlist.exception.GlobalExceptionHandler;
import com.rubymusic.playlist.mapper.PlaylistMapper;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.service.PlaylistService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PlaylistsControllerTest {

    @Mock
    private PlaylistService playlistService;

    @Mock
    private PlaylistMapper playlistMapper;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private PlaylistsController controller;

    private MockMvc mockMvc;

    private static final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(httpRequest.getHeader("X-User-Id")).thenReturn(USER.toString());
    }

    // ── createPlaylist ────────────────────────────────────────────────────────

    @Test
    void createPlaylist_validBody_returns201() throws Exception {
        Playlist saved = mock(Playlist.class);
        when(playlistService.create(USER, "My List", "Some desc", true))
                .thenReturn(saved);
        when(playlistMapper.toDto(saved)).thenReturn(new PlaylistResponse());

        mockMvc.perform(post("/api/v1/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My List\",\"description\":\"Some desc\",\"isPublic\":true}"))
                .andExpect(status().isCreated());

        verify(playlistService).create(USER, "My List", "Some desc", true);
    }

    @Test
    void createPlaylist_omittedIsPublic_defaultsToTrue_perOpenApiContract() throws Exception {
        // CreatePlaylistRequest.isPublic has default=true in the OpenAPI spec,
        // so omitting the field deserializes as true (not null).
        Playlist saved = mock(Playlist.class);
        when(playlistService.create(any(UUID.class), anyString(), any(), anyBoolean()))
                .thenReturn(saved);
        when(playlistMapper.toDto(any())).thenReturn(new PlaylistResponse());

        mockMvc.perform(post("/api/v1/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\"}"))
                .andExpect(status().isCreated());

        verify(playlistService).create(USER, "Mine", null, true);
    }

    @Test
    void createPlaylist_explicitFalseIsPublic_passesFalse() throws Exception {
        Playlist saved = mock(Playlist.class);
        when(playlistService.create(any(UUID.class), anyString(), any(), anyBoolean()))
                .thenReturn(saved);
        when(playlistMapper.toDto(any())).thenReturn(new PlaylistResponse());

        mockMvc.perform(post("/api/v1/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\",\"isPublic\":false}"))
                .andExpect(status().isCreated());

        verify(playlistService).create(USER, "Mine", null, false);
    }

    @Test
    void createPlaylist_missingName_returns422() throws Exception {
        // CreatePlaylistRequest.name is @NotNull
        mockMvc.perform(post("/api/v1/playlists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"no name\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── getMyPlaylists ────────────────────────────────────────────────────────

    @Test
    void getMyPlaylists_returnsList() throws Exception {
        Playlist p = mock(Playlist.class);
        when(playlistService.findByUserId(USER)).thenReturn(List.of(p));
        when(playlistMapper.toDtoList(anyList())).thenReturn(List.of(new PlaylistResponse()));

        mockMvc.perform(get("/api/v1/playlists/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── getPublicPlaylists ────────────────────────────────────────────────────

    @Test
    void getPublicPlaylists_returnsPage() throws Exception {
        Page<Playlist> page = new PageImpl<>(List.of(mock(Playlist.class), mock(Playlist.class)));
        when(playlistService.findPublic(any(Pageable.class))).thenReturn(page);
        when(playlistMapper.toDtoList(anyList()))
                .thenReturn(List.of(new PlaylistResponse(), new PlaylistResponse()));

        mockMvc.perform(get("/api/v1/playlists/public").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    // ── getPlaylistById ───────────────────────────────────────────────────────

    @Test
    void getPlaylistById_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Playlist p = mock(Playlist.class);
        when(playlistService.findById(id)).thenReturn(p);
        when(playlistMapper.toDto(p)).thenReturn(new PlaylistResponse());

        mockMvc.perform(get("/api/v1/playlists/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void getPlaylistById_unknown_returns400() throws Exception {
        // PlaylistServiceImpl.findById throws IllegalArgumentException → handler returns 400
        UUID id = UUID.randomUUID();
        when(playlistService.findById(id))
                .thenThrow(new IllegalArgumentException("Playlist not found: " + id));

        mockMvc.perform(get("/api/v1/playlists/{id}", id))
                .andExpect(status().isBadRequest());
    }

    // ── updatePlaylist ────────────────────────────────────────────────────────

    @Test
    void updatePlaylist_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        Playlist updated = mock(Playlist.class);
        when(playlistService.update(eq(id), eq(USER), any(), any(), any(), any())).thenReturn(updated);
        when(playlistMapper.toDto(updated)).thenReturn(new PlaylistResponse());

        mockMvc.perform(put("/api/v1/playlists/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Name\",\"description\":\"d\",\"isPublic\":false}"))
                .andExpect(status().isOk());

        verify(playlistService).update(id, USER, "New Name", "d", null, false);
    }

    // ── deletePlaylist ────────────────────────────────────────────────────────

    @Test
    void deletePlaylist_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/playlists/{id}", id))
                .andExpect(status().isNoContent());

        verify(playlistService).softDelete(id, USER);
    }

    @Test
    void deletePlaylist_systemPlaylist_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalStateException("System playlists cannot be deleted"))
                .when(playlistService).softDelete(id, USER);

        mockMvc.perform(delete("/api/v1/playlists/{id}", id))
                .andExpect(status().isBadRequest());
    }

    // ── savePublicPlaylist ────────────────────────────────────────────────────

    @Test
    void savePublicPlaylist_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/playlists/{id}/save", id))
                .andExpect(status().isNoContent());

        verify(playlistService).savePlaylistForUser(id, USER);
    }

    @Test
    void savePublicPlaylist_ownPlaylist_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Cannot save your own playlist"))
                .when(playlistService).savePlaylistForUser(id, USER);

        mockMvc.perform(post("/api/v1/playlists/{id}/save", id))
                .andExpect(status().isBadRequest());
    }

    // ── unsavePublicPlaylist ──────────────────────────────────────────────────

    @Test
    void unsavePublicPlaylist_returns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/playlists/{id}/save", id))
                .andExpect(status().isNoContent());

        verify(playlistService).unsavePlaylistForUser(id, USER);
    }

    // ── getMySavedPlaylists ───────────────────────────────────────────────────

    @Test
    void getMySavedPlaylists_returnsList() throws Exception {
        when(playlistService.findSavedByUser(USER)).thenReturn(List.of(mock(Playlist.class)));
        when(playlistMapper.toDtoList(anyList())).thenReturn(List.of(new PlaylistResponse()));

        mockMvc.perform(get("/api/v1/playlists/my/saved"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
