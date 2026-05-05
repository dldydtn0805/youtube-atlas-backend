package com.yongsoo.youtubeatlasbackend.game.api;

import com.yongsoo.youtubeatlasbackend.game.ScheduledSellTriggerDirection;
import com.yongsoo.youtubeatlasbackend.game.ScheduledSellTriggerType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateScheduledSellOrderRequest(
    @NotNull Long positionId,
    ScheduledSellTriggerType triggerType,
    @Min(1) @Max(200) Integer targetRank,
    Double targetProfitRatePercent,
    @NotNull @Min(1) Long quantity,
    ScheduledSellTriggerDirection triggerDirection
) {
}
