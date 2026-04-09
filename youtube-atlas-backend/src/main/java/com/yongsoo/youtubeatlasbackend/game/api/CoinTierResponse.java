package com.yongsoo.youtubeatlasbackend.game.api;

public record CoinTierResponse(
    String tierCode,
    String displayName,
    Long minCoinBalance,
    String badgeCode,
    String titleCode,
    String profileThemeCode
) {
}
