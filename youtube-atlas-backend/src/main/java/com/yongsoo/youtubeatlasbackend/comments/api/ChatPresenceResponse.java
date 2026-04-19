package com.yongsoo.youtubeatlasbackend.comments.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatPresenceResponse(
    int activeCount
) {
}
