package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record GameRealtimeEventResponse(
    String eventType,
    String regionCode,
    Long seasonId,
    Instant capturedAt,
    Instant occurredAt
) {
}
