package com.yongsoo.youtubeatlasbackend.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AdminPositionUpdateRequest(
    @NotNull(message = "quantity는 필수입니다.")
    @Min(value = 100, message = "quantity는 100 이상이어야 합니다.")
    Integer quantity,
    @NotNull(message = "stakePoints는 필수입니다.")
    @Min(value = 0, message = "stakePoints는 0 이상이어야 합니다.")
    Long stakePoints
) {
}
