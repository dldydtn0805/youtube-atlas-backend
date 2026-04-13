package com.yongsoo.youtubeatlasbackend.youtube.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record VideoItemResponse(
    String id,
    ContentDetailsResponse contentDetails,
    SnippetResponse snippet,
    StatisticsResponse statistics,
    TrendResponse trend
) {
    public record ContentDetailsResponse(
        String duration
    ) {
    }

    public record SnippetResponse(
        String title,
        String channelTitle,
        String channelId,
        String categoryId,
        String categoryLabel,
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

    public record TrendResponse(
        String categoryLabel,
        Integer currentRank,
        Integer previousRank,
        Integer rankChange,
        Long currentViewCount,
        Long previousViewCount,
        Long viewCountDelta,
        boolean isNew,
        Instant capturedAt
    ) {
    }
}
