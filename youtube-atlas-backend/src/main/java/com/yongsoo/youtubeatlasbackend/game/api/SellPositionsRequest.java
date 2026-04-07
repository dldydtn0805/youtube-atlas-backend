package com.yongsoo.youtubeatlasbackend.game.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SellPositionsRequest(
    @NotBlank String videoId,
    @NotNull @Min(1) Integer quantity
) {
}
