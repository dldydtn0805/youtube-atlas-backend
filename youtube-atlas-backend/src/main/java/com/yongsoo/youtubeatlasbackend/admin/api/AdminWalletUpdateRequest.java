package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminWalletUpdateRequest(
    Long seasonId,
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints,
    Long manualTierScoreAdjustment,
    Long coinBalance
) {
}
