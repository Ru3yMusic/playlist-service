package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.dto.AddSongRequest;
import com.rubymusic.playlist.dto.PlaylistSongResponse;
import com.rubymusic.playlist.dto.ReorderSongsRequest;
import com.rubymusic.playlist.mapper.PlaylistMapper;
import com.rubymusic.playlist.service.PlaylistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistSongsController implements PlaylistSongsApi {

    private final PlaylistService playlistService;
    private final PlaylistMapper playlistMapper;
    private final HttpServletRequest httpRequest;

    private UUID currentUserId() {
        return UUID.fromString(httpRequest.getHeader("X-User-Id"));
    }

    @Override
    public ResponseEntity<List<PlaylistSongResponse>> getPlaylistSongs(UUID id) {
        var songs = playlistService.findById(id).getSongs();
        return ResponseEntity.ok(playlistMapper.toSongDtoList(songs));
    }

    @Override
    @Transactional
    public ResponseEntity<PlaylistSongResponse> addSongToPlaylist(UUID id, AddSongRequest body) {
        var playlistSong = playlistService.addSong(id, currentUserId(), body.getSongId());
        return ResponseEntity.status(HttpStatus.CREATED).body(playlistMapper.toSongDto(playlistSong));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> removeSongFromPlaylist(UUID id, UUID songId) {
        playlistService.removeSong(id, currentUserId(), songId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @Transactional
    public ResponseEntity<Void> reorderSongs(UUID id, ReorderSongsRequest body) {
        playlistService.reorderSongs(id, currentUserId(), body.getOrderedSongIds());
        return ResponseEntity.noContent().build();
    }
}
