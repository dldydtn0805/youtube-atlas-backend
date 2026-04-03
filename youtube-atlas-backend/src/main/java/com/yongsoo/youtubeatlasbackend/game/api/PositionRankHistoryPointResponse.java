package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record PositionRankHistoryPointResponse(
    Long runId,
    Instant capturedAt,
    Integer rank,
    Long viewCount,
    boolean chartOut,
    boolean buyPoint,
    boolean sellPoint
) {
}
