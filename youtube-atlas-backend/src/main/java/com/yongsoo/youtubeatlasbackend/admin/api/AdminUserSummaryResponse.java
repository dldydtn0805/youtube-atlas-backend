package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminUserSummaryResponse(
    Long id,
    String email,
    String displayName,
    String pictureUrl,
    boolean admin,
    Instant createdAt,
    Instant lastLoginAt
) {
}
