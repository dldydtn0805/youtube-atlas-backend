package com.yongsoo.youtubeatlasbackend.comments.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatMessageResponse(
    Long id,
    String videoId,
    String messageType,
    String systemEventType,
    String author,
    String content,
    String clientId,
    Long userId,
    String currentTierCode,
    Instant createdAt
) {
}
