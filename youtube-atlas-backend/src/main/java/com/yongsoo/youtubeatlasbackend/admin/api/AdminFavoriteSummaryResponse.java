package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminFavoriteSummaryResponse(
    Long id,
    Long userId,
    String userEmail,
    String channelId,
    String channelTitle,
    String thumbnailUrl,
    Instant createdAt
) {
}
