package com.yongsoo.youtubeatlasbackend.auth.api;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequest(
    @NotBlank(message = "idToken은 필수입니다.") String idToken
) {
}
