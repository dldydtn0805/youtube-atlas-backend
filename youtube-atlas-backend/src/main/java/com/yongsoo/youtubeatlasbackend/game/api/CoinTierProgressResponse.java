package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record CoinTierProgressResponse(
    Long seasonId,
    String seasonName,
    String regionCode,
    Long coinBalance,
    CoinTierResponse currentTier,
    CoinTierResponse nextTier,
    List<CoinTierResponse> tiers
) {
}
