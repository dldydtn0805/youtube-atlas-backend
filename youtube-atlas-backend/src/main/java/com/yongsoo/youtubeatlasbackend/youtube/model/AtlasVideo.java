package com.yongsoo.youtubeatlasbackend.youtube.model;

public record AtlasVideo(
    String id,
    AtlasContentDetails contentDetails,
    AtlasVideoSnippet snippet,
    AtlasVideoStatistics statistics
) {
}
