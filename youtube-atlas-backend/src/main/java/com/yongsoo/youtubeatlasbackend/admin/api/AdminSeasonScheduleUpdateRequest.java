package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminSeasonScheduleUpdateRequest(
    Instant startAt,
    Instant endAt
) {
}
