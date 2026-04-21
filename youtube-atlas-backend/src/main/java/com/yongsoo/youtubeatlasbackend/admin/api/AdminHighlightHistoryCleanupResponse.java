package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminHighlightHistoryCleanupResponse(
    Instant deleteBefore,
    Instant deletedAt,
    long deletedCount
) {
}
