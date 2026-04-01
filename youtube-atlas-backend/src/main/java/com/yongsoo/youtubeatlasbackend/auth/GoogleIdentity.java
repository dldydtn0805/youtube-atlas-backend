package com.yongsoo.youtubeatlasbackend.auth;

public record GoogleIdentity(
    String subject,
    String email,
    String name,
    String pictureUrl
) {
}
