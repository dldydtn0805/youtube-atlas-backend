package com.yongsoo.youtubeatlasbackend.playback.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpsertPlaybackProgressRequest(
    @NotBlank(message = "videoId는 필수입니다.")
    @Size(max = 128, message = "videoId는 128자 이하여야 합니다.")
    String videoId,

    @Size(max = 255, message = "videoTitle은 255자 이하여야 합니다.")
    String videoTitle,

    @Size(max = 255, message = "channelTitle은 255자 이하여야 합니다.")
    String channelTitle,

    @Size(max = 2000, message = "thumbnailUrl은 2000자 이하여야 합니다.")
    String thumbnailUrl,

    @PositiveOrZero(message = "positionSeconds는 0 이상이어야 합니다.")
    long positionSeconds
) {
}
