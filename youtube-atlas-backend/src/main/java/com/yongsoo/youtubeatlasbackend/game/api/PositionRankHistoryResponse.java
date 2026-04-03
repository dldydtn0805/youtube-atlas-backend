package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;
import java.util.List;

public record PositionRankHistoryResponse(
    Long positionId,
    String videoId,
    String title,
    String channelTitle,
    String thumbnailUrl,
    String status,
    Integer buyRank,
    Integer latestRank,
    Integer sellRank,
    boolean latestChartOut,
    Instant buyCapturedAt,
    Instant latestCapturedAt,
    Instant closedAt,
    List<PositionRankHistoryPointResponse> points
) {
}
