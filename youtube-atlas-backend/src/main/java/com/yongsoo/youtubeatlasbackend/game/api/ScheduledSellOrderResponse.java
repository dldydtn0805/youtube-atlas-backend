package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record ScheduledSellOrderResponse(
    Long id,
    Long positionId,
    String videoId,
    String videoTitle,
    String channelTitle,
    String thumbnailUrl,
    String regionCode,
    Integer buyRank,
    Integer currentRank,
    String triggerType,
    Integer targetRank,
    Double targetProfitRatePercent,
    String triggerDirection,
    Long quantity,
    Long sellPricePoints,
    Long settledPoints,
    Long pnlPoints,
    String status,
    String failedReason,
    Instant createdAt,
    Instant updatedAt,
    Instant triggeredAt,
    Instant executedAt,
    Instant canceledAt,
    Instant failedAt
) {
}
