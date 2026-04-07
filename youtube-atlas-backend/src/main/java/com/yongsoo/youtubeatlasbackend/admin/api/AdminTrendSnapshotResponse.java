package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminTrendSnapshotResponse(
    Integer rank,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Long viewCount
) {
}
