package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record AchievementTitleCollectionResponse(
    SelectedAchievementTitleResponse selectedTitle,
    List<AchievementTitleResponse> titles
) {
}
