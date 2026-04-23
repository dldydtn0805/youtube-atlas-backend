package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;
import com.yongsoo.youtubeatlasbackend.game.GameNotificationEventType;
import com.yongsoo.youtubeatlasbackend.game.GameStrategyType;

public record GameNotificationResponse(
    String id,
    GameNotificationEventType notificationEventType,
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
    String titleCode,
    String titleDisplayName,
    AchievementTitleGrade titleGrade,
    Instant readAt,
    Instant createdAt,
    boolean showModal
) {
}
