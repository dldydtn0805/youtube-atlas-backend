package com.yongsoo.youtubeatlasbackend.youtube.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VideoItemResponse(
    String id,
    ContentDetailsResponse contentDetails,
    SnippetResponse snippet,
    StatisticsResponse statistics
) {
    public record ContentDetailsResponse(
        String duration
    ) {
    }

    public record SnippetResponse(
        String title,
        String channelTitle,
        String categoryId,
        String publishedAt,
        ThumbnailsResponse thumbnails
    ) {
    }

    public record ThumbnailsResponse(
        @JsonProperty("default") ThumbnailResponse defaultThumbnail,
        ThumbnailResponse medium,
        ThumbnailResponse high,
        ThumbnailResponse standard,
        ThumbnailResponse maxres
    ) {
    }

    public record ThumbnailResponse(
        String url,
        Integer width,
        Integer height
    ) {
    }

    public record StatisticsResponse(
        Long viewCount
    ) {
    }
}
