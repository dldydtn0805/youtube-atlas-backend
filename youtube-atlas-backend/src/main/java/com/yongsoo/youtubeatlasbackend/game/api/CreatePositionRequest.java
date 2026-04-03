package com.yongsoo.youtubeatlasbackend.game.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePositionRequest(
    @NotBlank String regionCode,
    @NotBlank String categoryId,
    @NotBlank String videoId,
    @NotNull @Min(0) Long stakePoints
) {
}
