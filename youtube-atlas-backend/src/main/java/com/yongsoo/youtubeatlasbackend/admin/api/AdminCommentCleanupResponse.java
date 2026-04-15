package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminCommentCleanupResponse(
    Instant deleteBefore,
    Instant deletedAt,
    long deletedCount
) {
}
