package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record CurrentSeasonResponse(
    Long seasonId,
    String seasonName,
    String status,
    String regionCode,
    Instant startAt,
    Instant endAt,
    Long startingBalancePoints,
    Integer minHoldSeconds,
    Integer maxOpenPositions,
    Integer rankPointMultiplier,
    WalletResponse wallet
) {
}
