package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record MarketVideoResponse(
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Integer currentRank,
    Integer previousRank,
    Integer rankChange,
    Long basePricePoints,
    Long currentPricePoints,
    Long momentumPriceDeltaPoints,
    Double momentumPriceDeltaPercent,
    String momentumPriceType,
    Long currentViewCount,
    Long viewCountDelta,
    Boolean isNew,
    Boolean canBuy,
    String buyBlockedReason,
    Instant capturedAt
) {
}
