package com.yongsoo.youtubeatlasbackend.admin.api;

import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;

public record AdminUserHighlightSummaryResponse(
    Long userId,
    Long seasonId,
    String seasonName,
    String regionCode,
    long calculatedHighlightScore,
    long manualTierScoreAdjustment,
    long tierScore,
    int highlightCount,
    List<GameHighlightResponse> highlights
) {
}
