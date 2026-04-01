package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;
import java.util.List;

public record RealtimeSurgingResponse(
    String regionCode,
    String categoryId,
    String categoryLabel,
    int rankChangeThreshold,
    int totalCount,
    Instant capturedAt,
    List<TrendSignalResponse> items
) {
}
