package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;

public record AchievementTitleResponse(
    String code,
    String displayName,
    String shortName,
    AchievementTitleGrade grade,
    String description,
    Boolean earned,
    Boolean selected,
    Instant earnedAt
) {
}
