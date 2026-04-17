package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;
import java.util.List;

public record TopRankRisersResponse(
    String regionCode,
    String categoryId,
    String categoryLabel,
    int limit,
    int totalCount,
    Instant capturedAt,
    List<TrendSignalResponse> items
) {
}
