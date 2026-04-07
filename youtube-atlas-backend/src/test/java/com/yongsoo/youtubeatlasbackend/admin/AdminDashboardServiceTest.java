package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminDashboardResponse;
import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.comments.Comment;
import com.yongsoo.youtubeatlasbackend.comments.CommentRepository;
import com.yongsoo.youtubeatlasbackend.favorites.FavoriteStreamer;
import com.yongsoo.youtubeatlasbackend.favorites.FavoriteStreamerRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

class AdminDashboardServiceTest {

    private AppUserRepository appUserRepository;
    private AdminAccessService adminAccessService;
    private CommentRepository commentRepository;
    private FavoriteStreamerRepository favoriteStreamerRepository;
    private GameSeasonRepository gameSeasonRepository;
    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        adminAccessService = org.mockito.Mockito.mock(AdminAccessService.class);
        commentRepository = org.mockito.Mockito.mock(CommentRepository.class);
        favoriteStreamerRepository = org.mockito.Mockito.mock(FavoriteStreamerRepository.class);
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        adminDashboardService = new AdminDashboardService(
            appUserRepository,
            adminAccessService,
            commentRepository,
            favoriteStreamerRepository,
            gameSeasonRepository,
            trendRunRepository,
            trendSnapshotRepository
        );
    }

    @Test
    void getDashboardAggregatesMetricsAndRecentEntities() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmail("user@example.com");
        user.setDisplayName("Atlas");
        user.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        user.setLastLoginAt(Instant.parse("2026-04-02T00:00:00Z"));

        Comment comment = new Comment();
        ReflectionTestUtils.setField(comment, "id", 2L);
        comment.setAuthor("Atlas");
        comment.setClientId("client-1");
        comment.setContent("hello admin");
        comment.setVideoId("video-1");
        comment.setCreatedAt(Instant.parse("2026-04-03T00:00:00Z"));

        FavoriteStreamer favorite = new FavoriteStreamer();
        ReflectionTestUtils.setField(favorite, "id", 3L);
        favorite.setUser(user);
        favorite.setChannelId("channel-1");
        favorite.setChannelTitle("Streamer");
        favorite.setThumbnailUrl("https://example.com/thumb.jpg");
        favorite.setCreatedAt(Instant.parse("2026-04-04T00:00:00Z"));

        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 4L);
        season.setName("Season 1");
        season.setStatus(SeasonStatus.ACTIVE);
        season.setRegionCode("KR");
        season.setStartAt(Instant.parse("2026-04-01T00:00:00Z"));
        season.setEndAt(Instant.parse("2026-04-30T00:00:00Z"));
        season.setCreatedAt(Instant.parse("2026-03-30T00:00:00Z"));

        TrendRun trendRun = new TrendRun();
        ReflectionTestUtils.setField(trendRun, "id", 5L);
        trendRun.setRegionCode("KR");
        trendRun.setCategoryId("music");
        trendRun.setCategoryLabel("Music");
        trendRun.setSource("youtube");
        trendRun.setCapturedAt(Instant.parse("2026-04-05T00:00:00Z"));

        TrendSnapshot trendSnapshot = new TrendSnapshot();
        trendSnapshot.setVideoId("video-1");
        trendSnapshot.setRank(1);
        trendSnapshot.setTitle("Top video");
        trendSnapshot.setChannelTitle("Atlas TV");
        trendSnapshot.setThumbnailUrl("https://example.com/video.jpg");
        trendSnapshot.setViewCount(123456L);

        when(appUserRepository.count()).thenReturn(12L);
        when(commentRepository.count()).thenReturn(34L);
        when(favoriteStreamerRepository.count()).thenReturn(5L);
        when(trendRunRepository.count()).thenReturn(7L);
        when(adminAccessService.isAdminEmail("user@example.com")).thenReturn(false);
        when(appUserRepository.findTop8ByOrderByCreatedAtDesc()).thenReturn(List.of(user));
        when(commentRepository.findTop8ByOrderByCreatedAtDesc()).thenReturn(List.of(comment));
        when(favoriteStreamerRepository.findTop8ByOrderByCreatedAtDesc()).thenReturn(List.of(favorite));
        when(gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)).thenReturn(Optional.of(season));
        when(trendRunRepository.findTopByOrderByCapturedAtDesc()).thenReturn(Optional.of(trendRun));
        when(trendSnapshotRepository.findTop8ByRun_IdOrderByRankAsc(5L)).thenReturn(List.of(trendSnapshot));

        AdminDashboardResponse response = adminDashboardService.getDashboard();

        assertThat(response.metrics().totalUsers()).isEqualTo(12L);
        assertThat(response.metrics().totalComments()).isEqualTo(34L);
        assertThat(response.activeSeason()).isNotNull();
        assertThat(response.activeSeason().name()).isEqualTo("Season 1");
        assertThat(response.latestTrendRun()).isNotNull();
        assertThat(response.latestTrendRun().categoryLabel()).isEqualTo("Music");
        assertThat(response.latestTrendRun().topVideos()).hasSize(1);
        assertThat(response.recentUsers()).extracting("email").containsExactly("user@example.com");
        assertThat(response.recentComments()).extracting("content").containsExactly("hello admin");
        assertThat(response.recentFavorites()).extracting("channelTitle").containsExactly("Streamer");
    }
}
