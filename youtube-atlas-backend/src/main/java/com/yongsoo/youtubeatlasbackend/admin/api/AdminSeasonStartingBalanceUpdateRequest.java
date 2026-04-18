package com.yongsoo.youtubeatlasbackend.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminSeasonStartingBalanceUpdateRequest(
    @NotNull(message = "startingBalancePoints는 필수입니다.")
    @Min(value = 0, message = "startingBalancePoints는 0 이상이어야 합니다.")
    Long startingBalancePoints
) {
}
