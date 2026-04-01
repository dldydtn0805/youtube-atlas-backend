package com.yongsoo.youtubeatlasbackend.auth.api;

import java.time.Instant;

public record AuthSessionResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    AuthUserResponse user
) {
}
