package com.yongsoo.youtubeatlasbackend.auth;

public record AuthenticatedUser(
    Long id,
    String email,
    String displayName,
    String pictureUrl
) {
}
