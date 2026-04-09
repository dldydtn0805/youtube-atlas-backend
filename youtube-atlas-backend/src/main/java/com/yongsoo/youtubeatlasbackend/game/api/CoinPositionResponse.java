package com.yongsoo.youtubeatlasbackend.game.api;

public record CoinPositionResponse(
    Long positionId,
    String videoId,
    String title,
    String thumbnailUrl,
    Integer currentRank,
    Integer quantity,
    Long currentValuePoints,
    Boolean rankEligible,
    Boolean productionActive,
    Double coinRatePercent,
    Long estimatedCoinYield,
    Long nextProductionInSeconds,
    Long nextPayoutInSeconds
) {
}
