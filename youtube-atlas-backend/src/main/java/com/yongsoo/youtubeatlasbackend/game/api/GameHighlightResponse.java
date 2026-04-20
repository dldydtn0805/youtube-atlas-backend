package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record GameHighlightResponse(
    String id,
    String highlightType,
    String grade,
    String title,
    String description,
    Long positionId,
    String videoId,
    String videoTitle,
    String channelTitle,
    String thumbnailUrl,
    Integer buyRank,
    Integer highlightRank,
    Integer sellRank,
    Integer rankDiff,
    Integer quantity,
    Long stakePoints,
    Long currentPricePoints,
    Long profitPoints,
    Double profitRatePercent,
    Long highlightScore,
    String status,
    Instant createdAt
) {
}
