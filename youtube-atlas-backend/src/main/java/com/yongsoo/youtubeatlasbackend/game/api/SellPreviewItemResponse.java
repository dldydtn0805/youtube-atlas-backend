package com.yongsoo.youtubeatlasbackend.game.api;

public record SellPreviewItemResponse(
    Long positionId,
    Integer buyRank,
    Integer quantity,
    Long stakePoints,
    Long sellPricePoints,
    Long pnlPoints,
    Long settledPoints,
    Long projectedHighlightScore,
    Long bestHighlightScore,
    Long appliedHighlightScoreDelta,
    boolean willUpdateRecord
) {
}
