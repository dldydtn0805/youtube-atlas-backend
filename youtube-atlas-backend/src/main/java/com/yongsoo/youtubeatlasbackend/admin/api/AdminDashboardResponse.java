package com.yongsoo.youtubeatlasbackend.admin.api;

import java.util.List;

public record AdminDashboardResponse(
    AdminSummaryMetricsResponse metrics,
    AdminSeasonSummaryResponse activeSeason,
    List<AdminSeasonSummaryResponse> activeSeasons,
    AdminTrendRunSummaryResponse latestTrendRun,
    List<AdminUserSummaryResponse> recentUsers,
    List<AdminCommentSummaryResponse> recentComments,
    List<AdminFavoriteSummaryResponse> recentFavorites
) {
}
