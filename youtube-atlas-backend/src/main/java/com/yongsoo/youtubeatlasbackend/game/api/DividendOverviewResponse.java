package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record DividendOverviewResponse(
    Integer eligibleRankCutoff,
    Integer minimumHoldSeconds,
    Long totalWeightedValuePoints,
    Long myWeightedValuePoints,
    Double myEstimatedPoolSharePercent,
    Integer myEligiblePositionCount,
    Integer myWarmingUpPositionCount,
    List<DividendRankResponse> ranks,
    List<DividendPositionResponse> positions
) {
}
