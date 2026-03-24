package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;

public record SyncTrendingResponse(
    String regionCode,
    String categoryId,
    int syncedVideos,
    int signalCount,
    Instant capturedAt
) {
}
