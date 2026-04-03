package com.yongsoo.youtubeatlasbackend.trending.api;

import java.time.Instant;
import java.util.List;

public record VideoRankHistoryResponse(
    String regionCode,
    String categoryId,
    String categoryLabel,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    Integer latestRank,
    boolean latestChartOut,
    Instant latestCapturedAt,
    List<VideoRankHistoryPointResponse> points
) {
}
