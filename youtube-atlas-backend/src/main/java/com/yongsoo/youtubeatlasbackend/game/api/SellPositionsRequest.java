package com.yongsoo.youtubeatlasbackend.game.api;

import org.springframework.util.StringUtils;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SellPositionsRequest(
    @NotBlank String regionCode,
    Long positionId,
    String videoId,
    @NotNull @Min(1) Integer quantity
) {
    @AssertTrue(message = "positionId 또는 videoId 중 하나는 필수입니다.")
    public boolean hasSellTarget() {
        return positionId != null || StringUtils.hasText(videoId);
    }
}
