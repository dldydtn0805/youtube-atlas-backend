package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record PositionResponse(
    Long id,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Integer buyRank,
    Integer currentRank,
    Integer rankDiff,
    Long stakePoints,
    Long profitPoints,
    String status,
    Instant buyCapturedAt,
    Instant createdAt,
    Instant closedAt
) {
}
