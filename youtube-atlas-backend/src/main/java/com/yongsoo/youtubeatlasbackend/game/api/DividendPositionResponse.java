package com.yongsoo.youtubeatlasbackend.game.api;

public record DividendPositionResponse(
    Long positionId,
    String videoId,
    String title,
    String thumbnailUrl,
    Integer currentRank,
    Integer quantity,
    Long currentValuePoints,
    Boolean rankEligible,
    Boolean holdEligible,
    Integer dividendWeight,
    Long weightedValuePoints,
    Double estimatedPoolSharePercent,
    Long nextEligibleInSeconds
) {
}
