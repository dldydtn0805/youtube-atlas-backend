package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record SeasonCoinResultResponse(
    Long seasonId,
    String seasonName,
    String regionCode,
    Long finalCoinBalance,
    CoinTierResponse finalTier,
    Instant finalizedAt
) {
}
