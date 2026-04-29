package com.yongsoo.youtubeatlasbackend.game.api;

public record TierResponse(
    String tierCode,
    String displayName,
    Long minScore,
    String badgeCode,
    String titleCode,
    String profileThemeCode,
    Integer inventorySlots
) {
}
