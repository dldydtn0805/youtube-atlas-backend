package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record SellPositionResponse(
    Long positionId,
    String videoId,
    Integer buyRank,
    Integer sellRank,
    Integer rankDiff,
    Integer quantity,
    Long stakePoints,
    Long sellPricePoints,
    Long pnlPoints,
    Long settledPoints,
    Long balancePoints,
    Instant soldAt
) {
}
