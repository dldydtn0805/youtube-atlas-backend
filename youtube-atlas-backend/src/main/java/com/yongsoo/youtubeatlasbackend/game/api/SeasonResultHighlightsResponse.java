package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record SeasonResultHighlightsResponse(
    SeasonResultHighlightItemResponse topRankRiser,
    List<SeasonResultHighlightItemResponse> mostTaggedPositions,
    SeasonResultHighlightItemResponse longestHeld
) {
}
