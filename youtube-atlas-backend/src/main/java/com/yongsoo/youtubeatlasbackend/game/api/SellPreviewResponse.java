package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record SellPreviewResponse(
    Integer quantity,
    Integer sellRank,
    Long stakePoints,
    Long sellPricePoints,
    Long pnlPoints,
    Long settledPoints,
    Long projectedHighlightScore,
    Long appliedHighlightScoreDelta,
    Integer recordEligibleCount,
    List<SellPreviewItemResponse> items
) {
}
