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
    Long quantity,
    Long stakePoints,
    Long currentPricePoints,
    Long profitPoints,
    List<GameStrategyType> strategyTags,
    List<GameStrategyType> achievedStrategyTags,
    List<GameStrategyType> targetStrategyTags,
    Long projectedHighlightScore,
    boolean chartOut,
    Boolean reservedForSell,
    Long scheduledSellOrderId,
    Integer scheduledSellTargetRank,
    String scheduledSellTriggerDirection,
    Long scheduledSellQuantity,
    String status,
    Instant buyCapturedAt,
    Instant createdAt,
    Instant closedAt
) {
}
