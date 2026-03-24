package com.yongsoo.youtubeatlasbackend.comments.api;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(
    String author,
    @NotBlank(message = "메시지 내용을 입력해 주세요.") String content,
    @NotBlank(message = "clientId는 필수입니다.") String clientId
) {
}
