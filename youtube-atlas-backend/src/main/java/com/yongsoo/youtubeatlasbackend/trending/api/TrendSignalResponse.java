package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;

public record TrendSignalResponse(
    String regionCode,
    String categoryId,
    String categoryLabel,
    String videoId,
    Integer currentRank,
    Integer previousRank,
    Integer rankChange,
    Long currentViewCount,
    Long previousViewCount,
    Long viewCountDelta,
    boolean isNew,
    String title,
    String channelTitle,
    String channelId,
    String thumbnailUrl,
    Instant capturedAt
) {
}
