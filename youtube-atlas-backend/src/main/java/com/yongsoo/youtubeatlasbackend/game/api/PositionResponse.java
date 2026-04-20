package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.GameStrategyType;

public record PositionResponse(
    Long id,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Integer buyRank,
    Integer currentRank,
    Integer rankDiff,
    Integer quantity,
    Long stakePoints,
    Long currentPricePoints,
    Long profitPoints,
    List<GameStrategyType> strategyTags,
    List<GameStrategyType> achievedStrategyTags,
    List<GameStrategyType> targetStrategyTags,
    Long projectedHighlightScore,
    boolean chartOut,
    String status,
    Instant buyCapturedAt,
    Instant createdAt,
    Instant closedAt
) {
}
