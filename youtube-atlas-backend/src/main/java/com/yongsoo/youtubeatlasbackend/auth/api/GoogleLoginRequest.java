package com.yongsoo.youtubeatlasbackend.auth.api;

public record GoogleLoginRequest(
    String idToken,
    String code,
    String redirectUri
) {
}
