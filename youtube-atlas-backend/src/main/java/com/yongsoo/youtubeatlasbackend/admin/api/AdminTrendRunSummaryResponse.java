package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;
import java.util.List;

public record AdminTrendRunSummaryResponse(
    Long id,
    String regionCode,
    String categoryId,
    String categoryLabel,
    String source,
    Instant capturedAt,
    List<AdminTrendSnapshotResponse> topVideos
) {
}
