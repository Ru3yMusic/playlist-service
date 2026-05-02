package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.dto.PlaylistSongResponse;
import com.rubymusic.playlist.exception.GlobalExceptionHandler;
import com.rubymusic.playlist.mapper.PlaylistMapper;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSong;
import com.rubymusic.playlist.service.PlaylistService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
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
class PlaylistSongsControllerTest {

    @Mock
    private PlaylistService playlistService;

    @Mock
    private PlaylistMapper playlistMapper;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private PlaylistSongsController controller;

    private MockMvc mockMvc;

    private static final UUID USER = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        lenient().when(httpRequest.getHeader("X-User-Id")).thenReturn(USER.toString());
    }

    // ── getPlaylistSongs ──────────────────────────────────────────────────────

    @Test
    void getPlaylistSongs_returnsList() throws Exception {
        UUID id = UUID.randomUUID();
        Playlist p = mock(Playlist.class);
        PlaylistSong song1 = mock(PlaylistSong.class);
        when(p.getSongs()).thenReturn(List.of(song1));
        when(playlistService.findById(id)).thenReturn(p);
        when(playlistMapper.toSongDtoList(anyList()))
                .thenReturn(List.of(new PlaylistSongResponse()));

        mockMvc.perform(get("/api/v1/playlists/{id}/songs", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ── addSongToPlaylist ─────────────────────────────────────────────────────

    @Test
    void addSongToPlaylist_returns201() throws Exception {
        UUID playlistId = UUID.randomUUID();
        UUID songId = UUID.randomUUID();
        PlaylistSong saved = mock(PlaylistSong.class);
        when(playlistService.addSong(playlistId, USER, songId)).thenReturn(saved);
        when(playlistMapper.toSongDto(saved)).thenReturn(new PlaylistSongResponse());

        mockMvc.perform(post("/api/v1/playlists/{id}/songs", playlistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"songId\":\"" + songId + "\"}"))
                .andExpect(status().isCreated());

        verify(playlistService).addSong(playlistId, USER, songId);
    }

    @Test
    void addSongToPlaylist_missingSongId_returns422() throws Exception {
        // AddSongRequest.songId is @NotNull
        UUID playlistId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/playlists/{id}/songs", playlistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── removeSongFromPlaylist ────────────────────────────────────────────────

    @Test
    void removeSongFromPlaylist_returns204() throws Exception {
        UUID playlistId = UUID.randomUUID();
        UUID songId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/playlists/{id}/songs/{songId}", playlistId, songId))
                .andExpect(status().isNoContent());

        verify(playlistService).removeSong(playlistId, USER, songId);
    }

    // ── reorderSongs ──────────────────────────────────────────────────────────

    @Test
    void reorderSongs_returns204() throws Exception {
        UUID playlistId = UUID.randomUUID();
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/playlists/{id}/songs/reorder", playlistId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderedSongIds\":[\"" + s1 + "\",\"" + s2 + "\"]}"))
                .andExpect(status().isNoContent());

        verify(playlistService).reorderSongs(playlistId, USER, List.of(s1, s2));
    }

}
