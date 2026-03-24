package com.yongsoo.youtubeatlasbackend.youtube.model;

import java.time.OffsetDateTime;

public record AtlasVideoSnippet(
    String title,
    String channelTitle,
    String categoryId,
    OffsetDateTime publishedAt,
    AtlasThumbnails thumbnails
) {
}
