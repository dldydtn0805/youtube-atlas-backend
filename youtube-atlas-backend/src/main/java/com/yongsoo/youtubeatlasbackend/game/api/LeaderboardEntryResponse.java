package com.yongsoo.youtubeatlasbackend.game.api;

public record LeaderboardEntryResponse(
    Integer rank,
    Long userId,
    String displayName,
    String pictureUrl,
    TierResponse currentTier,
    SelectedAchievementTitleResponse selectedAchievementTitle,
    Long highlightScore,
    Integer highlightCount,
    String topHighlightType,
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
