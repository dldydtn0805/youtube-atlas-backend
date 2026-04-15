package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminUserPositionResponse(
    Long id,
    Long seasonId,
    String seasonName,
    String regionCode,
    String categoryId,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Integer buyRank,
    Integer quantity,
    Long stakePoints,
    String status,
    Instant buyCapturedAt,
    Instant createdAt,
    Instant closedAt
) {
}
