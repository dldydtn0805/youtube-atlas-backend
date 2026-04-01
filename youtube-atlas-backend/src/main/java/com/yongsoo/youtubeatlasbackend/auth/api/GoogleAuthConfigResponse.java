package com.yongsoo.youtubeatlasbackend.auth.api;

public record GoogleAuthConfigResponse(
    String clientId,
    boolean enabled
) {
}
