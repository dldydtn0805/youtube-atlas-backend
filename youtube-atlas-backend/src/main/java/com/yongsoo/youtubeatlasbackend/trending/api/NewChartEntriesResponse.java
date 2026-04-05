package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;
import java.util.List;

public record NewChartEntriesResponse(
    String regionCode,
    String categoryId,
    String categoryLabel,
    int totalCount,
    Instant capturedAt,
    List<TrendSignalResponse> items
) {
}
