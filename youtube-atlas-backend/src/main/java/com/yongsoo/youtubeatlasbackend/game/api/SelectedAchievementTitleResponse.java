package com.yongsoo.youtubeatlasbackend.game.api;

import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;

public record SelectedAchievementTitleResponse(
    String code,
    String displayName,
    String shortName,
    AchievementTitleGrade grade,
    String description
) {
}
