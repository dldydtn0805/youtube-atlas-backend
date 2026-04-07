package com.yongsoo.youtubeatlasbackend.admin;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminCommentSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminDashboardResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminFavoriteSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminSeasonSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminSummaryMetricsResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminTrendRunSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminTrendSnapshotResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminUserSummaryResponse;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.comments.CommentRepository;
import com.yongsoo.youtubeatlasbackend.favorites.FavoriteStreamerRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

@Service
public class AdminDashboardService {

    private final AppUserRepository appUserRepository;
    private final CommentRepository commentRepository;
    private final FavoriteStreamerRepository favoriteStreamerRepository;
    private final GameSeasonRepository gameSeasonRepository;
    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;

    public AdminDashboardService(
        AppUserRepository appUserRepository,
        CommentRepository commentRepository,
        FavoriteStreamerRepository favoriteStreamerRepository,
        GameSeasonRepository gameSeasonRepository,
        TrendRunRepository trendRunRepository,
        TrendSnapshotRepository trendSnapshotRepository
    ) {
        this.appUserRepository = appUserRepository;
        this.commentRepository = commentRepository;
        this.favoriteStreamerRepository = favoriteStreamerRepository;
        this.gameSeasonRepository = gameSeasonRepository;
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        TrendRun latestTrendRun = trendRunRepository.findTopByOrderByCapturedAtDesc().orElse(null);

        return new AdminDashboardResponse(
            new AdminSummaryMetricsResponse(
                appUserRepository.count(),
                commentRepository.count(),
                favoriteStreamerRepository.count(),
                trendRunRepository.count()
            ),
            gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)
                .map(season -> new AdminSeasonSummaryResponse(
                    season.getId(),
                    season.getName(),
                    season.getStatus().name(),
                    season.getRegionCode(),
                    season.getStartAt(),
                    season.getEndAt(),
                    season.getCreatedAt()
                ))
                .orElse(null),
            latestTrendRun == null ? null : new AdminTrendRunSummaryResponse(
                latestTrendRun.getId(),
                latestTrendRun.getRegionCode(),
                latestTrendRun.getCategoryId(),
                latestTrendRun.getCategoryLabel(),
                latestTrendRun.getSource(),
                latestTrendRun.getCapturedAt(),
                trendSnapshotRepository.findTop8ByRun_IdOrderByRankAsc(latestTrendRun.getId()).stream()
                    .map(snapshot -> new AdminTrendSnapshotResponse(
                        snapshot.getRank(),
                        snapshot.getVideoId(),
                        snapshot.getTitle(),
                        snapshot.getChannelTitle(),
                        snapshot.getThumbnailUrl(),
                        snapshot.getViewCount()
                    ))
                    .toList()
            ),
            appUserRepository.findTop8ByOrderByCreatedAtDesc().stream()
                .map(user -> new AdminUserSummaryResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getPictureUrl(),
                    user.getCreatedAt(),
                    user.getLastLoginAt()
                ))
                .toList(),
            commentRepository.findTop8ByOrderByCreatedAtDesc().stream()
                .map(comment -> new AdminCommentSummaryResponse(
                    comment.getId(),
                    comment.getVideoId(),
                    comment.getAuthor(),
                    comment.getContent(),
                    comment.getClientId(),
                    comment.getCreatedAt()
                ))
                .toList(),
            favoriteStreamerRepository.findTop8ByOrderByCreatedAtDesc().stream()
                .map(favorite -> new AdminFavoriteSummaryResponse(
                    favorite.getId(),
                    favorite.getUser().getId(),
                    favorite.getUser().getEmail(),
                    favorite.getChannelId(),
                    favorite.getChannelTitle(),
                    favorite.getThumbnailUrl(),
                    favorite.getCreatedAt()
                ))
                .toList()
        );
    }
}
