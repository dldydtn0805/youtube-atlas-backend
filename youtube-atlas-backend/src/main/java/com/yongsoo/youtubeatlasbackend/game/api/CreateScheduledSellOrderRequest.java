package com.yongsoo.youtubeatlasbackend.game.api;

import com.yongsoo.youtubeatlasbackend.game.ScheduledSellTriggerDirection;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateScheduledSellOrderRequest(
    @NotNull Long positionId,
    @NotNull @Min(1) @Max(200) Integer targetRank,
    @NotNull @Min(1) Integer quantity,
    ScheduledSellTriggerDirection triggerDirection
) {
}
