package com.yongsoo.youtubeatlasbackend.game.api;

import com.yongsoo.youtubeatlasbackend.game.GameStrategyType;

public record GameHighlightStrategyScoreResponse(
    GameStrategyType strategyType,
    Long baseScore,
    Integer rankDiff,
    Long rankDiffMultiplier,
    Long rankDiffBonus,
    Double profitRatePercent,
    Long profitRateMultiplier,
    Long maxProfitRateBonus,
    Long profitRateBonus,
    Long profitPoints,
    Long minProfitPointsForBonus,
    Long maxProfitPointsBonus,
    Long profitPointsBonus,
    Long totalScore
) {
}
