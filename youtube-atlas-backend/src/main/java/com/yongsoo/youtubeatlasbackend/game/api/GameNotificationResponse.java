package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.GameStrategyType;

public record GameNotificationResponse(
    String id,
    String notificationType,
    String title,
    String message,
    Long positionId,
    String videoId,
    String videoTitle,
    String channelTitle,
    String thumbnailUrl,
    List<GameStrategyType> strategyTags,
    Long highlightScore,
    Instant readAt,
    Instant createdAt,
    boolean showModal
) {
}
