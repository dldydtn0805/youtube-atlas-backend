package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminSummaryMetricsResponse(
    long totalUsers,
    long totalComments,
    long totalFavorites,
    long totalTrendRuns
) {
}
