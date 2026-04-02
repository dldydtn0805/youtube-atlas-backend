package com.yongsoo.youtubeatlasbackend.auth.api;

import java.time.Instant;

import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;

public record AuthUserResponse(
    Long id,
    String email,
    String displayName,
    String pictureUrl,
    Instant lastLoginAt,
    PlaybackProgressResponse lastPlaybackProgress
) {
}
