package com.yongsoo.youtubeatlasbackend.playback.api;

import java.time.Instant;

public record PlaybackProgressResponse(
    String videoId,
    String videoTitle,
    String channelTitle,
    String thumbnailUrl,
    long positionSeconds,
    Instant updatedAt
) {
}
