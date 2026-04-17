package com.yongsoo.youtubeatlasbackend.admin;

import java.util.Comparator;
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
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotMaintenanceService;

@Service
public class AdminDashboardService {

    private final AppUserRepository appUserRepository;
    private final AdminAccessService adminAccessService;
    private final CommentRepository commentRepository;
    private final FavoriteStreamerRepository favoriteStreamerRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameSeasonRepository gameSeasonRepository;
    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotMaintenanceService trendSnapshotMaintenanceService;

    public AdminDashboardService(
        AppUserRepository appUserRepository,
        AdminAccessService adminAccessService,
        CommentRepository commentRepository,
        FavoriteStreamerRepository favoriteStreamerRepository,
        GamePositionRepository gamePositionRepository,
        GameSeasonRepository gameSeasonRepository,
        TrendRunRepository trendRunRepository,
        TrendSnapshotMaintenanceService trendSnapshotMaintenanceService
    ) {
        this.appUserRepository = appUserRepository;
        this.adminAccessService = adminAccessService;
        this.commentRepository = commentRepository;
        this.favoriteStreamerRepository = favoriteStreamerRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameSeasonRepository = gameSeasonRepository;
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotMaintenanceService = trendSnapshotMaintenanceService;
    }

    @Transactional
    public AdminDashboardResponse getDashboard() {
        TrendRun latestTrendRun = trendRunRepository.findTopByOrderByCapturedAtDesc().orElse(null);
        List<AdminSeasonSummaryResponse> activeSeasons = gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE).stream()
            .sorted(
                Comparator.comparing(GameSeason::getRegionCode, Comparator.nullsLast(String::compareToIgnoreCase))
                    .thenComparing(GameSeason::getStartAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(GameSeason::getId)
            )
            .map(season -> new AdminSeasonSummaryResponse(
                season.getId(),
                season.getName(),
                season.getStatus().name(),
                season.getRegionCode(),
                season.getStartAt(),
                season.getEndAt(),
                season.getCreatedAt()
            ))
            .toList();

        return new AdminDashboardResponse(
            new AdminSummaryMetricsResponse(
                appUserRepository.count(),
                commentRepository.count(),
                favoriteStreamerRepository.count(),
                trendRunRepository.count(),
                gamePositionRepository.count()
            ),
            activeSeasons.stream().findFirst().orElse(null),
            activeSeasons,
            latestTrendRun == null ? null : new AdminTrendRunSummaryResponse(
                latestTrendRun.getId(),
                latestTrendRun.getRegionCode(),
                latestTrendRun.getCategoryId(),
                latestTrendRun.getCategoryLabel(),
                latestTrendRun.getSource(),
                latestTrendRun.getCapturedAt(),
                trendSnapshotMaintenanceService.sanitizeRunSnapshots(latestTrendRun.getId()).stream()
                    .limit(8)
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
                    adminAccessService.isAdminEmail(user.getEmail()),
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
