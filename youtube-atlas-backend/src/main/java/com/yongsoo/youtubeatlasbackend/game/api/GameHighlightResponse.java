package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.GameStrategyType;

public record GameHighlightResponse(
    String id,
    String highlightType,
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
    Long quantity,
    Long stakePoints,
    Long currentPricePoints,
    Long profitPoints,
    Double profitRatePercent,
    List<GameStrategyType> strategyTags,
    Long highlightScore,
    String status,
    Instant createdAt
) {
}
