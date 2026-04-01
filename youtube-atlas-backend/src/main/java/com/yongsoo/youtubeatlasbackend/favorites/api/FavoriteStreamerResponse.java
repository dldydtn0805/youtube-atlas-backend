package com.yongsoo.youtubeatlasbackend.favorites.api;

import java.time.Instant;

public record FavoriteStreamerResponse(
    Long id,
    String channelId,
    String channelTitle,
    String thumbnailUrl,
    Instant createdAt
) {
}
