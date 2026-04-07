package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminSeasonSummaryResponse(
    Long id,
    String name,
    String status,
    String regionCode,
    Instant startAt,
    Instant endAt,
    Instant createdAt
) {
}
