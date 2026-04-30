package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record GameHighlightScoreBreakdownResponse(
    Long totalScore,
    List<GameHighlightStrategyScoreResponse> strategyScores
) {
}
