package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminCoinTierSummaryResponse(
    String tierCode,
    String displayName,
    Long minCoinBalance,
    String badgeCode,
    String titleCode,
    String profileThemeCode
) {
}
