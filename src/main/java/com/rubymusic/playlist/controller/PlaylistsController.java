package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.dto.CreatePlaylistRequest;
import com.rubymusic.playlist.dto.PlaylistResponse;
import com.rubymusic.playlist.dto.UpdatePlaylistRequest;
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
public class PlaylistsController implements PlaylistsApi {

    private final PlaylistService playlistService;
    private final PlaylistMapper playlistMapper;
    private final HttpServletRequest httpRequest;

    private UUID currentUserId() {
        return UUID.fromString(httpRequest.getHeader("X-User-Id"));
    }

    @Override
    @Transactional
    public ResponseEntity<PlaylistResponse> createPlaylist(CreatePlaylistRequest body) {
        var playlist = playlistService.create(
                currentUserId(),
                body.getName(),
                body.getDescription(),
                Boolean.TRUE.equals(body.getIsPublic())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(playlistMapper.toDto(playlist));
    }

    @Override
    public ResponseEntity<List<PlaylistResponse>> getMyPlaylists() {
        return ResponseEntity.ok(playlistMapper.toDtoList(playlistService.findByUserId(currentUserId())));
    }

    @Override
    public ResponseEntity<PlaylistResponse> getPlaylistById(UUID id) {
        return ResponseEntity.ok(playlistMapper.toDto(playlistService.findById(id)));
    }

    @Override
    @Transactional
    public ResponseEntity<PlaylistResponse> updatePlaylist(UUID id, UpdatePlaylistRequest body) {
        var playlist = playlistService.update(id, currentUserId(),
                body.getName(), body.getDescription(), body.getCoverUrl(), body.getIsPublic());
        return ResponseEntity.ok(playlistMapper.toDto(playlist));
    }

    @Override
    @Transactional
    public ResponseEntity<Void> deletePlaylist(UUID id) {
        playlistService.softDelete(id, currentUserId());
        return ResponseEntity.noContent().build();
    }
}
