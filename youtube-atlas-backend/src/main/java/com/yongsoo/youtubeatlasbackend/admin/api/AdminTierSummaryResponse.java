package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminTierSummaryResponse(
    String tierCode,
    String displayName,
    Long minScore,
    String badgeCode,
    String titleCode,
    String profileThemeCode
) {
}
