package com.yongsoo.youtubeatlasbackend.auth.api;

import java.time.Instant;

public record AuthUserResponse(
    Long id,
    String email,
    String displayName,
    String pictureUrl,
    Instant lastLoginAt
) {
}
