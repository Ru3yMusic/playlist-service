package com.rubymusic.playlist.controller;

import com.rubymusic.playlist.dto.CreatePlaylistRequest;
import com.rubymusic.playlist.dto.PlaylistPage;
import com.rubymusic.playlist.dto.PlaylistResponse;
import com.rubymusic.playlist.dto.UpdatePlaylistRequest;
import com.rubymusic.playlist.mapper.PlaylistMapper;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.service.PlaylistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PlaylistsController implements PlaylistsApi {

    private final PlaylistService playlistService;
    private final PlaylistMapper playlistMapper;
    private final HttpServletRequest httpRequest;

    private UUID currentUserId() {
        return UUID.fromString(httpRequest.getHeader("X-User-Id"));
    }

    @Override
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
    public ResponseEntity<PlaylistPage> getPublicPlaylists(Integer page, Integer size) {
        Page<Playlist> result = playlistService.findPublic(PageRequest.of(page, size));
        PlaylistPage dto = new PlaylistPage()
                .content(playlistMapper.toDtoList(result.getContent()))
                .totalElements((int) result.getTotalElements())
                .totalPages(result.getTotalPages())
                .page(result.getNumber())
                .size(result.getSize());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<PlaylistResponse> getPlaylistById(UUID id) {
        return ResponseEntity.ok(playlistMapper.toDto(playlistService.findById(id)));
    }

    @Override
    public ResponseEntity<PlaylistResponse> updatePlaylist(UUID id, UpdatePlaylistRequest body) {
        var playlist = playlistService.update(id, currentUserId(),
                body.getName(), body.getDescription(), body.getCoverUrl(), body.getIsPublic());
        return ResponseEntity.ok(playlistMapper.toDto(playlist));
    }

    @Override
    public ResponseEntity<Void> deletePlaylist(UUID id) {
        playlistService.softDelete(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> savePublicPlaylist(UUID id) {
        playlistService.savePlaylistForUser(id, currentUserId());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @Override
    public ResponseEntity<Void> unsavePublicPlaylist(UUID id) {
        playlistService.unsavePlaylistForUser(id, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<PlaylistResponse>> getMySavedPlaylists() {
        return ResponseEntity.ok(playlistMapper.toDtoList(
                playlistService.findSavedByUser(currentUserId())));
    }
}
