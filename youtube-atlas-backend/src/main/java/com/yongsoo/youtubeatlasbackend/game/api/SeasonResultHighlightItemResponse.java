package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.GameStrategyType;

public record SeasonResultHighlightItemResponse(
    Long positionId,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Integer buyRank,
    Integer sellRank,
    Integer rankDiff,
    Long profitPoints,
    Double profitRatePercent,
    Long holdDurationSeconds,
    Integer tagCount,
    Long highlightScore,
    List<GameStrategyType> strategyTags
) {
}
