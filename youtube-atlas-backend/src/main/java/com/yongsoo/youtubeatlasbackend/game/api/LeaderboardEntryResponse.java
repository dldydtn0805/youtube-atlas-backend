package com.yongsoo.youtubeatlasbackend.game.api;

public record LeaderboardEntryResponse(
    Integer rank,
    Long userId,
    String displayName,
    String pictureUrl,
    CoinTierResponse currentTier,
    Long coinBalance,
    Long totalAssetPoints,
    Long balancePoints,
    Long reservedPoints,
    Long totalStakePoints,
    Long totalEvaluationPoints,
    Double profitRatePercent,
    Long realizedPnlPoints,
    Long unrealizedPnlPoints,
    Integer openPositionCount,
    Boolean me
) {
}
