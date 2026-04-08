package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record DividendOverviewResponse(
    Integer eligibleRankCutoff,
    Integer minimumHoldSeconds,
    Long myEstimatedDividendPoints,
    Integer myEligiblePositionCount,
    Integer myWarmingUpPositionCount,
    List<DividendRankResponse> ranks,
    List<DividendPositionResponse> positions
) {
}
