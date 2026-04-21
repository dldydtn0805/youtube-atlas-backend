package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminUserGameSummaryResponse(
    Long seasonId,
    String seasonName,
    String regionCode,
    boolean participating,
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints,
    Long calculatedTierScore,
    Long manualTierScoreAdjustment,
    Long tierScore,
    Long totalAssetPoints,
    AdminTierSummaryResponse currentTier,
    AdminTierSummaryResponse nextTier,
    long openPositionCount,
    long closedPositionCount
) {
}
