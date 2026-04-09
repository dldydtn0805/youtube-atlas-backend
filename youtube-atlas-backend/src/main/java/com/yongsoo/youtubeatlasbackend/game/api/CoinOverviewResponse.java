package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record CoinOverviewResponse(
    Integer eligibleRankCutoff,
    Integer minimumHoldSeconds,
    Long myCoinBalance,
    Long myEstimatedCoinYield,
    Integer myActiveProducerCount,
    Integer myWarmingUpPositionCount,
    List<CoinRankResponse> ranks,
    List<CoinPositionResponse> positions
) {
}
