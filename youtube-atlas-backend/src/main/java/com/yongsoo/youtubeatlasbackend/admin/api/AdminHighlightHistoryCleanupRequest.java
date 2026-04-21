package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record AdminHighlightHistoryCleanupRequest(
    @NotNull(message = "deleteBefore는 필수입니다.") Instant deleteBefore
) {
}
