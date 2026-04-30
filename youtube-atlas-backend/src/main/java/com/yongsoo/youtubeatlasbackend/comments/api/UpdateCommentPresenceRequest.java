package com.yongsoo.youtubeatlasbackend.comments.api;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentPresenceRequest(
    @NotBlank(message = "clientId는 필수입니다.") String clientId
) {
}
