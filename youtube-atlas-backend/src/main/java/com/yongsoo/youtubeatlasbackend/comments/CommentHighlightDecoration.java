package com.yongsoo.youtubeatlasbackend.comments;

import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

public record CommentHighlightDecoration(
    SelectedAchievementTitleResponse selectedAchievementTitle,
    String currentTierCode
) {
}
