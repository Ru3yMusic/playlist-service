package com.rubymusic.playlist.mapper;

import com.rubymusic.playlist.dto.PlaylistResponse;
import com.rubymusic.playlist.dto.PlaylistSongResponse;
import com.rubymusic.playlist.model.Playlist;
import com.rubymusic.playlist.model.PlaylistSong;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlaylistMapper {

    @Mapping(target = "songCount", expression = "java(playlist.getSongs() != null ? playlist.getSongs().size() : 0)")
    PlaylistResponse toDto(Playlist playlist);

    List<PlaylistResponse> toDtoList(List<Playlist> playlists);

    PlaylistSongResponse toSongDto(PlaylistSong playlistSong);

    List<PlaylistSongResponse> toSongDtoList(List<PlaylistSong> songs);
}
