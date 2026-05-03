package com.yongsoo.youtubeatlasbackend.youtube.model;

import java.time.OffsetDateTime;

public record AtlasCommentHighlight(
    String id,
    String authorName,
    String text,
    Long likeCount,
    OffsetDateTime publishedAt
) {
}
