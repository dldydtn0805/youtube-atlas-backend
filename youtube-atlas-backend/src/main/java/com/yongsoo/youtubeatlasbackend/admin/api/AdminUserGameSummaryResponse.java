package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminUserGameSummaryResponse(
    Long seasonId,
    String seasonName,
    boolean participating,
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints,
    Long coinBalance,
    Long totalAssetPoints,
    AdminCoinTierSummaryResponse currentCoinTier,
    AdminCoinTierSummaryResponse nextCoinTier,
    long openPositionCount,
    long closedPositionCount
) {
}
