package com.yongsoo.youtubeatlasbackend.common;

public record ApiErrorResponse(
    String code,
    String message,
    Integer retryAfterSeconds
) {
}
