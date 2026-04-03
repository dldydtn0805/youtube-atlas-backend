package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;

public record VideoRankHistoryPointResponse(
    Long runId,
    Instant capturedAt,
    Integer rank,
    Long viewCount,
    boolean chartOut
) {
}
