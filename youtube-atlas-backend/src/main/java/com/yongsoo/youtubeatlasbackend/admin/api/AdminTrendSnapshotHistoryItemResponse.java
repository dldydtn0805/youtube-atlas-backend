package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminTrendSnapshotHistoryItemResponse(
    Long id,
    Long runId,
    String regionCode,
    String categoryId,
    String categoryLabel,
    String source,
    Instant capturedAt,
    Instant savedAt,
    Integer rank,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Long viewCount,
    String videoCategoryId,
    String videoCategoryLabel
) {
}
