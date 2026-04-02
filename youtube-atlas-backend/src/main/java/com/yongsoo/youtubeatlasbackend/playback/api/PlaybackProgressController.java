package com.yongsoo.youtubeatlasbackend.playback.api;

import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.yongsoo.youtubeatlasbackend.auth.AuthService;
import com.yongsoo.youtubeatlasbackend.playback.PlaybackProgressService;

@RestController
@RequestMapping("/api/me/playback-progress")
public class PlaybackProgressController {

    private final PlaybackProgressService playbackProgressService;
    private final AuthService authService;

    public PlaybackProgressController(PlaybackProgressService playbackProgressService, AuthService authService) {
        this.playbackProgressService = playbackProgressService;
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<PlaybackProgressResponse> getCurrentPlaybackProgress(
        @RequestHeader("Authorization") String authorizationHeader
    ) {
        Optional<PlaybackProgressResponse> response = playbackProgressService.getCurrentProgress(
            authService.requireCurrentUser(authorizationHeader)
        );

        return response.map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public PlaybackProgressResponse upsertPlaybackProgress(
        @RequestHeader("Authorization") String authorizationHeader,
        @Valid @RequestBody UpsertPlaybackProgressRequest request
    ) {
        return playbackProgressService.upsertProgress(authService.requireCurrentUser(authorizationHeader), request);
    }
}
