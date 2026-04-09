package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;

public record AdminUserDetailResponse(
    Long id,
    String email,
    String displayName,
    String pictureUrl,
    boolean admin,
    Instant createdAt,
    Instant lastLoginAt,
    long favoriteCount,
    PlaybackProgressResponse lastPlaybackProgress,
    AdminUserGameSummaryResponse activeSeasonGame,
    List<AdminUserGameSummaryResponse> activeSeasonGames
) {
}
