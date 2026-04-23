package com.yongsoo.youtubeatlasbackend.auth.api;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;
import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;

public record AuthUserResponse(
    Long id,
    String email,
    String displayName,
    String pictureUrl,
    SelectedAchievementTitleResponse selectedTitle,
    Instant createdAt,
    Instant lastLoginAt,
    long favoriteCount,
    long commentCount,
    long tradeCount,
    PlaybackProgressResponse lastPlaybackProgress,
    List<PlaybackProgressResponse> recentPlaybackProgresses
) {
}
