package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record TierProgressResponse(
    Long seasonId,
    String seasonName,
    String regionCode,
    Long highlightScore,
    Long calculatedHighlightScore,
    Long manualTierScoreAdjustment,
    TierResponse currentTier,
    TierResponse nextTier,
    List<TierResponse> tiers
) {
}
