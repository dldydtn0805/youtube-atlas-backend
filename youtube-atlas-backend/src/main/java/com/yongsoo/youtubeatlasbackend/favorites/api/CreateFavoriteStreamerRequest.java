package com.yongsoo.youtubeatlasbackend.favorites.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateFavoriteStreamerRequest(
    @NotBlank(message = "channelId는 필수입니다.")
    @Size(max = 128, message = "channelId는 128자 이하여야 합니다.")
    String channelId,

    @NotBlank(message = "channelTitle은 필수입니다.")
    @Size(max = 255, message = "channelTitle은 255자 이하여야 합니다.")
    String channelTitle,

    @Size(max = 2000, message = "thumbnailUrl은 2000자 이하여야 합니다.")
    String thumbnailUrl
) {
}
