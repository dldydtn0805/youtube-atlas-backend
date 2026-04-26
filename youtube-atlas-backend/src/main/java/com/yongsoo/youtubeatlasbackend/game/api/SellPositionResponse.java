package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record SellPositionResponse(
    Long positionId,
    String videoId,
    Integer buyRank,
    Integer sellRank,
    Integer rankDiff,
    Long quantity,
    Long stakePoints,
    Long sellPricePoints,
    Long pnlPoints,
    Long settledPoints,
    Long highlightScore,
    Long balancePoints,
    Instant soldAt
) {
}
