package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminUserGameSummaryResponse(
    Long seasonId,
    String seasonName,
    String regionCode,
    boolean participating,
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints,
    Long tierScore,
    Long coinBalance,
    Long totalAssetPoints,
    AdminCoinTierSummaryResponse currentCoinTier,
    AdminCoinTierSummaryResponse nextCoinTier,
    long openPositionCount,
    long closedPositionCount
) {
}
