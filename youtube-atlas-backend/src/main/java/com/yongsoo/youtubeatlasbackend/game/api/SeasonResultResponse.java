package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record SeasonResultResponse(
    Long id,
    Long seasonId,
    String seasonName,
    String regionCode,
    Instant seasonStartAt,
    Instant seasonEndAt,
    Integer finalRank,
    Long finalAssetPoints,
    Long finalBalancePoints,
    Long realizedPnlPoints,
    Long startingBalancePoints,
    Double profitRatePercent,
    Long finalHighlightScore,
    String finalTierCode,
    String finalTierName,
    String finalTierBadgeCode,
    String finalTierTitleCode,
    Long positionCount,
    Long bestPositionId,
    String bestPositionVideoId,
    String bestPositionTitle,
    String bestPositionChannelTitle,
    String bestPositionThumbnailUrl,
    Long bestPositionProfitPoints,
    Double bestPositionProfitRatePercent,
    Integer bestPositionRankDiff,
    Integer bestPositionBuyRank,
    Integer bestPositionSellRank,
    String titleCode,
    Instant createdAt,
    SeasonResultHighlightsResponse highlights
) {
}
