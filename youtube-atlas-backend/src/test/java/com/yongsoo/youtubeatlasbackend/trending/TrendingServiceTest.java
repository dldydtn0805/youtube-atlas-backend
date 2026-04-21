package com.yongsoo.youtubeatlasbackend.trending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.game.GameService;
import com.yongsoo.youtubeatlasbackend.trending.api.SyncTrendingRequest;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeCatalogService;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategoryResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasContentDetails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoSnippet;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoStatistics;

class TrendingServiceTest {

    private YouTubeCatalogService youTubeCatalogService;
    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private TrendSignalRepository trendSignalRepository;
    private GameService gameService;
    private TrendingService trendingService;

    @BeforeEach
    void setUp() {
        youTubeCatalogService = org.mockito.Mockito.mock(YouTubeCatalogService.class);
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        gameService = org.mockito.Mockito.mock(GameService.class);

        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getTrending().setCaptureSlotMinutes(5);
        atlasProperties.getTrending().setSyncMaxPagesPerSource(3);
        when(youTubeCatalogService.getCategories(any())).thenReturn(List.of(
            new VideoCategoryResponse("10", "음악", "뮤직", List.of("10")),
            new VideoCategoryResponse("20", "게임", "게임", List.of("20")),
            new VideoCategoryResponse("25", "뉴스", "뉴스", List.of("25"))
        ));

        trendingService = new TrendingService(
            atlasProperties,
            youTubeCatalogService,
            trendRunRepository,
            trendSnapshotRepository,
            trendSignalRepository,
            gameService
        );
    }

    @Test
    void getSignalsAlwaysUsesAllCategoryId() {
        TrendRun latestRun = trendRun(88L, Instant.parse("2026-04-17T12:00:00Z"));
        TrendSnapshot latestSnapshot = snapshot(latestRun, "video-1", 1);
        TrendSignal signal = trendSignal("KR", "0", "video-1", 1, 0, "Signal", latestRun.getCapturedAt());
        signal.setCurrentRunId(latestRun.getId());
        signal.setPreviousRunId(87L);
        signal.setPreviousRank(1);
        signal.setRankChange(0);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(latestSnapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(signal));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", latestRun.getCapturedAt()
        )).thenReturn(Optional.empty());

        trendingService.getSignals("kr", "10", List.of("video-1"));

        verify(trendSnapshotRepository).findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0");
        verify(trendSignalRepository).findByIdRegionCodeAndIdCategoryId("KR", "0");
    }

    @Test
    void getSignalsFallsBackToSnapshotDerivedNewWhenSignalPointsToSameRun() {
        TrendRun latestRun = trendRun(90L, Instant.parse("2026-04-17T12:00:00Z"));
        TrendRun previousRun = trendRun(89L, Instant.parse("2026-04-17T11:00:00Z"));
        TrendSnapshot latestSnapshot = snapshot(latestRun, "video-1", 2);
        TrendSignal brokenSignal = trendSignal("KR", "0", "video-1", 2, 0, "Broken", latestRun.getCapturedAt());
        brokenSignal.setCurrentRunId(latestRun.getId());
        brokenSignal.setPreviousRunId(latestRun.getId());
        brokenSignal.setPreviousRank(2);
        brokenSignal.setRankChange(0);
        brokenSignal.setNew(false);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(latestSnapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(brokenSignal));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", latestRun.getCapturedAt()
        )).thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(previousRun.getId())).thenReturn(List.of());

        var response = trendingService.getSignals("KR", "0", List.of("video-1"));

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.currentRank()).isEqualTo(2);
            assertThat(item.previousRank()).isNull();
            assertThat(item.rankChange()).isNull();
            assertThat(item.isNew()).isTrue();
        });
    }

    @Test
    void getRealtimeSurgingUsesConfiguredThresholdAndAllCategoryId() {
        Instant capturedAt = Instant.parse("2026-04-01T05:30:00Z");
        TrendRun latestRun = trendRun(10L, capturedAt);
        TrendSnapshot snapshot = snapshot(latestRun, "video-1", 3);
        TrendSignal signal = trendSignal("KR", "0", "video-1", 3, 10, "Surging", capturedAt);
        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(signal));

        var response = trendingService.getRealtimeSurging("kr");

        assertThat(response.regionCode()).isEqualTo("KR");
        assertThat(response.categoryId()).isEqualTo("0");
        assertThat(response.rankChangeThreshold()).isEqualTo(5);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.capturedAt()).isEqualTo(capturedAt);
        assertThat(response.items()).singleElement().extracting("videoId", "rankChange")
            .containsExactly("video-1", 10);
    }

    @Test
    void getRealtimeSurgingFallsBackToLatestRunCapturedAtWhenEmpty() {
        TrendRun latestRun = trendRun(11L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSnapshot snapshot = snapshot(latestRun, "video-1", 1);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getRealtimeSurging("KR");

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.capturedAt()).isEqualTo(latestRun.getCapturedAt());
    }

    @Test
    void getTopRankRisersReturnsOnlyPositiveRankChangesFromAllCategory() {
        Instant capturedAt = Instant.parse("2026-04-01T05:30:00Z");
        TrendRun latestRun = trendRun(12L, capturedAt);
        TrendSnapshot firstSnapshot = snapshot(latestRun, "video-1", 2);
        TrendSnapshot secondSnapshot = snapshot(latestRun, "video-2", 5);
        TrendSignal first = trendSignal("KR", "0", "video-1", 2, 12, "Top rise 1", capturedAt);
        TrendSignal second = trendSignal("KR", "0", "video-2", 5, -15, "Top drop 2", capturedAt);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(firstSnapshot, secondSnapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(first, second));

        var response = trendingService.getTopRankRisers("kr");

        assertThat(response.regionCode()).isEqualTo("KR");
        assertThat(response.categoryId()).isEqualTo("0");
        assertThat(response.limit()).isEqualTo(10);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.capturedAt()).isEqualTo(capturedAt);
        assertThat(response.items()).extracting("videoId", "rankChange")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("video-1", 12));
    }

    @Test
    void getTopRankRisersDoesNotFillRemainingSlotsWithNewEntries() {
        Instant capturedAt = Instant.parse("2026-04-01T05:30:00Z");
        TrendRun latestRun = trendRun(13L, capturedAt);
        TrendSnapshot risingSnapshot = snapshot(latestRun, "video-1", 2);
        TrendSnapshot newEntryHigherSnapshot = snapshot(latestRun, "video-3", 4);
        TrendSnapshot newEntryLowerSnapshot = snapshot(latestRun, "video-4", 7);
        TrendSignal rising = trendSignal("KR", "0", "video-1", 2, 12, "Top rise 1", capturedAt);
        TrendSignal newEntryHigher = trendSignal("KR", "0", "video-3", 4, null, "New 1", capturedAt);
        newEntryHigher.setNew(true);
        TrendSignal newEntryLower = trendSignal("KR", "0", "video-4", 7, null, "New 2", capturedAt);
        newEntryLower.setNew(true);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(risingSnapshot, newEntryHigherSnapshot, newEntryLowerSnapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(rising, newEntryHigher, newEntryLower));

        var response = trendingService.getTopRankRisers("KR");

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).extracting("videoId", "currentRank", "isNew")
            .containsExactly(org.assertj.core.groups.Tuple.tuple("video-1", 2, false));
    }

    @Test
    void getTopRankRisersFallsBackToLatestRunCapturedAtWhenEmpty() {
        TrendRun latestRun = trendRun(14L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSnapshot snapshot = snapshot(latestRun, "video-1", 1);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getTopRankRisers("KR");

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.capturedAt()).isEqualTo(latestRun.getCapturedAt());
    }

    @Test
    void getNewChartEntriesUsesAllCategoryId() {
        Instant capturedAt = Instant.parse("2026-04-01T05:30:00Z");
        TrendRun latestRun = trendRun(15L, capturedAt);
        TrendSnapshot snapshot = snapshot(latestRun, "video-2", 7);
        TrendSignal signal = trendSignal("KR", "0", "video-2", 7, null, "New entry", capturedAt);
        signal.setNew(true);
        signal.setPreviousRank(null);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(signal));

        var response = trendingService.getNewChartEntries("kr");

        assertThat(response.regionCode()).isEqualTo("KR");
        assertThat(response.categoryId()).isEqualTo("0");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.capturedAt()).isEqualTo(capturedAt);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.videoId()).isEqualTo("video-2");
            assertThat(item.isNew()).isTrue();
        });
    }

    @Test
    void getNewChartEntriesFallsBackToLatestRunCapturedAtWhenEmpty() {
        TrendRun latestRun = trendRun(16L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSnapshot snapshot = snapshot(latestRun, "video-1", 1);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getNewChartEntries("KR");

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.capturedAt()).isEqualTo(latestRun.getCapturedAt());
    }

    @Test
    void getTopVideosReturnsSnapshotBackedPageWithTrendSignal() {
        TrendRun latestRun = trendRun(21L, Instant.parse("2026-04-02T06:00:00Z"));
        TrendSnapshot first = snapshot(latestRun, "video-1", 1);
        TrendSnapshot second = snapshot(latestRun, "video-2", 2);
        TrendSignal firstSignal = trendSignal("KR", "0", "video-1", 1, 4, "Title", latestRun.getCapturedAt());

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(first, second));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(firstSignal));

        var response = trendingService.getTopVideos("kr", null);

        assertThat(response.categoryId()).isEqualTo("0");
        assertThat(response.items()).hasSize(2);
        assertThat(response.availableCategories()).extracting("id", "label", "count")
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("10", "음악", 2L)
            );
        assertThat(response.nextPageToken()).isNull();
        assertThat(response.items().getFirst().id()).isEqualTo("video-1");
        assertThat(response.items().getFirst().snippet().categoryId()).isEqualTo("10");
        assertThat(response.items().getFirst().snippet().categoryLabel()).isEqualTo("음악");
        assertThat(response.items().getFirst().trend()).isNotNull();
        assertThat(response.items().getFirst().trend().currentRank()).isEqualTo(1);
        assertThat(response.items().getLast().trend()).isNotNull();
        assertThat(response.items().getLast().trend().currentRank()).isEqualTo(2);
        assertThat(response.items().getLast().trend().rankChange()).isNull();
    }

    @Test
    void getTopVideosUsesPageTokenOffset() {
        TrendRun latestRun = trendRun(22L, Instant.parse("2026-04-02T07:00:00Z"));
        List<TrendSnapshot> snapshots = java.util.stream.IntStream.rangeClosed(1, 55)
            .mapToObj(index -> snapshot(latestRun, "video-" + index, index))
            .toList();

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(snapshots);
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getTopVideos("KR", "50");

        assertThat(response.items()).hasSize(5);
        assertThat(response.items().getFirst().id()).isEqualTo("video-51");
        assertThat(response.availableCategories()).singleElement().extracting("id", "label", "count")
            .containsExactly("10", "음악", 55L);
        assertThat(response.nextPageToken()).isNull();
    }

    @Test
    void getTopVideosFallsBackToSignalsWhenLatestSnapshotIsMissing() {
        TrendRun latestRun = trendRun(23L, Instant.parse("2026-04-02T08:00:00Z"));
        TrendSnapshot first = snapshot(latestRun, "video-1", 1);
        TrendSnapshot second = snapshot(latestRun, "video-2", 2);
        TrendSignal firstSignal = trendSignal("KR", "0", "video-1", 1, 4, "Signal 1", latestRun.getCapturedAt());

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(first, second));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(firstSignal));

        var response = trendingService.getTopVideos("KR", null);

        assertThat(response.categoryId()).isEqualTo("0");
        assertThat(response.items()).extracting("id").containsExactly("video-1", "video-2");
        assertThat(response.items().getFirst().trend().currentRank()).isEqualTo(1);
        assertThat(response.items().getFirst().trend().rankChange()).isEqualTo(4);
        assertThat(response.items().getFirst().statistics().viewCount()).isEqualTo(100L);
    }

    @Test
    void getTopVideosFallsBackToSignalsWhenLatestRunHasNoSnapshots() {
        TrendRun latestRun = trendRun(23L, Instant.parse("2026-04-02T08:00:00Z"));
        TrendSnapshot snapshot = snapshot(latestRun, "video-1", 1);

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot));

        var response = trendingService.getTopVideos("KR", null);

        assertThat(response.items()).singleElement().extracting("id").isEqualTo("video-1");
    }

    @Test
    void getTopVideosDerivesRankDropFromPreviousSnapshotWhenSignalIsStale() {
        TrendRun previousRun = trendRun(24L, Instant.parse("2026-04-02T07:00:00Z"));
        TrendRun latestRun = trendRun(25L, Instant.parse("2026-04-02T08:00:00Z"));
        TrendSnapshot latestSnapshot = snapshot(latestRun, "video-1", 5);
        TrendSignal staleSignal = trendSignal("KR", "0", "video-1", 3, 0, "Stale", previousRun.getCapturedAt());

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(latestSnapshot));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(staleSignal));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", latestRun.getCapturedAt()
        ))
            .thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(previousRun.getId()))
            .thenReturn(List.of(snapshot(previousRun, "video-1", 3)));

        var response = trendingService.getTopVideos("KR", null);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.trend().currentRank()).isEqualTo(5);
            assertThat(item.trend().previousRank()).isEqualTo(3);
            assertThat(item.trend().rankChange()).isEqualTo(-2);
            assertThat(item.trend().isNew()).isFalse();
        });
    }

    @Test
    void getMusicTopVideosUsesLatestStoredSnapshots() {
        TrendRun latestRun = trendRun(24L, Instant.parse("2026-04-02T08:30:00Z"));
        TrendSnapshot music = snapshot(latestRun, "music-1", 1, "10", "음악");
        TrendSnapshot news = snapshot(latestRun, "news-1", 2, "25", "뉴스");

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(music, news));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getMusicTopVideos("KR", null);

        assertThat(response.items()).singleElement().extracting("id").isEqualTo("music-1");
        verify(youTubeCatalogService, never()).getPopularVideosByCategory("KR", "10", null);
    }

    @Test
    void getMusicTopVideosFiltersSnapshotToMusicVideosOnly() {
        TrendRun latestRun = trendRun(23L, Instant.parse("2026-04-02T08:00:00Z"));
        TrendSnapshot musicFirst = snapshot(latestRun, "music-1", 1, "10", "음악");
        TrendSnapshot nonMusic = snapshot(latestRun, "news-1", 2, "25", "뉴스");
        TrendSnapshot musicSecond = snapshot(latestRun, "music-2", 3, "10", "음악");

        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(musicFirst, nonMusic, musicSecond));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getMusicTopVideos("KR", null);

        assertThat(response.categoryId()).isEqualTo("10");
        assertThat(response.label()).isEqualTo("음악");
        assertThat(response.availableCategories()).isEmpty();
        assertThat(response.items()).extracting("id").containsExactly("music-1", "music-2");
        assertThat(response.items()).allSatisfy(item -> {
            assertThat(item.snippet().categoryId()).isEqualTo("10");
            assertThat(item.snippet().categoryLabel()).isEqualTo("음악");
        });
    }

    @Test
    void getVideoHistoryReturnsObservedHistoryAndCurrentChartOut() {
        TrendRun firstRun = trendRun(11L, Instant.parse("2026-04-01T05:00:00Z"));
        TrendRun latestRun = trendRun(12L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSnapshot snapshot = snapshot(firstRun, "video-1", 23);

        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(List.of(snapshot));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByCapturedAtDescIdDesc("KR", "0"))
            .thenReturn(Optional.of(latestRun));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc("KR", "0", 11L, 12L))
            .thenReturn(List.of(firstRun, latestRun));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.empty());

        var response = trendingService.getVideoHistory("kr", "video-1");

        assertThat(response.videoId()).isEqualTo("video-1");
        assertThat(response.latestRank()).isNull();
        assertThat(response.latestChartOut()).isTrue();
        assertThat(response.points()).hasSize(2);
        assertThat(response.points().getFirst().rank()).isEqualTo(23);
        assertThat(response.points().getLast().chartOut()).isTrue();
    }

    @Test
    void syncAlwaysUsesAllCategoryDefaults() {
        when(trendRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndSourceAndCapturedAt(eq("KR"), eq("0"), eq("youtube-mostPopular"), any()))
            .thenReturn(Optional.empty());
        when(youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of(), 3))
            .thenReturn(List.of(video("video-1")));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            eq("KR"), eq("0"), eq("youtube-mostPopular"), any()
        ))
            .thenReturn(Optional.empty());
        when(trendRunRepository.findIdsByRegionCodeAndCategoryIdAndCapturedAtBefore(eq("KR"), eq("0"), any()))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());
        when(trendSignalRepository.findById(any())).thenReturn(Optional.empty());

        var response = trendingService.sync(new SyncTrendingRequest("KR", "10", "Music", List.of("10")));

        ArgumentCaptor<TrendRun> trendRunCaptor = ArgumentCaptor.forClass(TrendRun.class);
        verify(trendRunRepository).save(trendRunCaptor.capture());
        assertThat(trendRunCaptor.getValue().getCategoryId()).isEqualTo("0");
        assertThat(trendRunCaptor.getValue().getCategoryLabel()).isEqualTo("전체");
        assertThat(trendRunCaptor.getValue().getSourceCategoryIds()).isEmpty();
        assertThat(trendRunCaptor.getValue().getCapturedAt()).isNotNull();
        assertThat(trendRunCaptor.getValue().getCapturedAt().getEpochSecond() % 300L).isZero();

        verify(youTubeCatalogService).fetchMergedCategoryVideos("KR", List.of(), 3);
        verify(youTubeCatalogService).getCategories("KR");
        verify(trendSignalRepository).findByIdRegionCodeAndIdCategoryId("KR", "0");
        assertThat(response.categoryId()).isEqualTo("0");
    }

    @Test
    void syncPurgesExpiredRunsWhenRetentionIsEnabled() {
        when(trendRunRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndSourceAndCapturedAt(eq("US"), eq("0"), eq("youtube-mostPopular"), any()))
            .thenReturn(Optional.empty());
        when(youTubeCatalogService.fetchMergedCategoryVideos("US", List.of(), 3))
            .thenReturn(List.of(video("video-1")));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            eq("US"), eq("0"), eq("youtube-mostPopular"), any()
        ))
            .thenReturn(Optional.empty());
        when(trendRunRepository.findIdsByRegionCodeAndCategoryIdAndCapturedAtBefore(eq("US"), eq("0"), any()))
            .thenReturn(List.of(4L, 5L));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("US", "0"))
            .thenReturn(List.of());
        when(trendSignalRepository.findById(any())).thenReturn(Optional.empty());

        trendingService.sync(new SyncTrendingRequest("US", "0", "전체", List.of()));

        verify(trendSnapshotRepository).deleteByRun_IdIn(List.of(4L, 5L));
        verify(trendRunRepository).deleteAllByIdInBatch(List.of(4L, 5L));
    }

    @Test
    void syncReusesExistingRunWithinSameCaptureSlot() {
        TrendRun existingRun = trendRun(31L, Instant.parse("2026-04-10T09:00:00Z"));

        when(trendRunRepository.findByRegionCodeAndCategoryIdAndSourceAndCapturedAt(eq("KR"), eq("0"), eq("youtube-mostPopular"), any()))
            .thenReturn(Optional.of(existingRun));
        when(youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of(), 3))
            .thenReturn(List.of(video("video-1")));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", existingRun.getCapturedAt()
        ))
            .thenReturn(Optional.empty());
        when(trendRunRepository.findIdsByRegionCodeAndCategoryIdAndCapturedAtBefore(eq("KR"), eq("0"), any()))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());
        when(trendSignalRepository.findById(any())).thenReturn(Optional.empty());

        trendingService.sync(new SyncTrendingRequest("KR", "0", "전체", List.of()));

        verify(trendRunRepository, never()).save(any());
        verify(trendSnapshotRepository).deleteByRun_Id(31L);
    }

    @Test
    void syncComparesAgainstPreviousCapturedSlotForRankChange() {
        TrendRun currentRun = trendRun(51L, Instant.parse("2026-04-10T09:00:00Z"));
        TrendRun previousRun = trendRun(49L, Instant.parse("2026-04-10T08:00:00Z"));

        when(trendRunRepository.findByRegionCodeAndCategoryIdAndSourceAndCapturedAt(eq("KR"), eq("0"), eq("youtube-mostPopular"), any()))
            .thenReturn(Optional.of(currentRun));
        when(youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of(), 3))
            .thenReturn(List.of(video("video-1")));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", currentRun.getCapturedAt()
        ))
            .thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(previousRun.getId()))
            .thenReturn(List.of(snapshot(previousRun, "video-1", 4)));
        when(trendRunRepository.findIdsByRegionCodeAndCategoryIdAndCapturedAtBefore(eq("KR"), eq("0"), any()))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());
        when(trendSignalRepository.findById(any())).thenReturn(Optional.empty());

        trendingService.sync(new SyncTrendingRequest("KR", "0", "전체", List.of()));

        ArgumentCaptor<TrendSignal> signalCaptor = ArgumentCaptor.forClass(TrendSignal.class);
        verify(trendSignalRepository).save(signalCaptor.capture());
        assertThat(signalCaptor.getValue().getPreviousRunId()).isEqualTo(previousRun.getId());
        assertThat(signalCaptor.getValue().getPreviousRank()).isEqualTo(4);
        assertThat(signalCaptor.getValue().getCurrentRank()).isEqualTo(1);
        assertThat(signalCaptor.getValue().getRankChange()).isEqualTo(3);
    }

    @Test
    void getVideoHistoryCollapsesDuplicateRunsInSameCaptureSlot() {
        TrendRun firstRun = trendRun(41L, Instant.parse("2026-04-10T07:00:03Z"));
        TrendRun secondRun = trendRun(42L, Instant.parse("2026-04-10T07:04:55Z"));
        TrendRun latestRun = trendRun(43L, Instant.parse("2026-04-10T08:00:20Z"));

        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(List.of(
                snapshot(firstRun, "video-1", 133),
                snapshot(secondRun, "video-1", 133),
                snapshot(latestRun, "video-1", 128)
            ));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByCapturedAtDescIdDesc("KR", "0"))
            .thenReturn(Optional.of(latestRun));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc("KR", "0", 41L, 43L))
            .thenReturn(List.of(firstRun, secondRun, latestRun));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.empty());

        var response = trendingService.getVideoHistory("KR", "video-1");

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0).capturedAt()).isEqualTo(Instant.parse("2026-04-10T07:00:00Z"));
        assertThat(response.points().get(1).capturedAt()).isEqualTo(Instant.parse("2026-04-10T08:00:00Z"));
    }

    private AtlasVideo video(String id) {
        return new AtlasVideo(
            id,
            new AtlasContentDetails("PT10M"),
            new AtlasVideoSnippet(
                "Title",
                "Channel",
                "channel-1",
                "10",
                OffsetDateTime.parse("2026-03-24T10:00:00Z"),
                null
            ),
            new AtlasVideoStatistics(100L)
        );
    }

    private TrendSignal trendSignal(
        String regionCode,
        String categoryId,
        String videoId,
        int currentRank,
        Integer rankChange,
        String title,
        Instant capturedAt
    ) {
        TrendSignal signal = new TrendSignal();
        signal.setId(new TrendSignalId(regionCode, categoryId, videoId));
        signal.setCategoryLabel("전체");
        signal.setCurrentRunId(1L);
        signal.setPreviousRunId(2L);
        signal.setCurrentRank(currentRank);
        signal.setPreviousRank(rankChange != null ? currentRank + rankChange : null);
        signal.setRankChange(rankChange);
        signal.setCurrentViewCount(100L);
        signal.setPreviousViewCount(rankChange != null ? 90L : null);
        signal.setViewCountDelta(rankChange != null ? 10L : null);
        signal.setNew(false);
        signal.setTitle(title);
        signal.setChannelTitle("Channel");
        signal.setChannelId("channel-1");
        signal.setThumbnailUrl("https://example.com/thumb.jpg");
        signal.setCapturedAt(capturedAt);
        signal.setUpdatedAt(capturedAt);
        return signal;
    }

    private TrendRun trendRun(Long id, Instant capturedAt) {
        TrendRun run = new TrendRun();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", id);
        run.setRegionCode("KR");
        run.setCategoryId("0");
        run.setCategoryLabel("전체");
        run.setSourceCategoryIds(List.of());
        run.setSource("youtube-mostPopular");
        run.setCapturedAt(capturedAt);
        return run;
    }

    private TrendSnapshot snapshot(TrendRun run, String videoId, int rank) {
        return snapshot(run, videoId, rank, "10", "음악");
    }

    private TrendSnapshot snapshot(TrendRun run, String videoId, int rank, String videoCategoryId, String videoCategoryLabel) {
        TrendSnapshot snapshot = new TrendSnapshot();
        snapshot.setRun(run);
        snapshot.setRegionCode("KR");
        snapshot.setCategoryId("0");
        snapshot.setVideoCategoryId(videoCategoryId);
        snapshot.setVideoCategoryLabel(videoCategoryLabel);
        snapshot.setVideoId(videoId);
        snapshot.setRank(rank);
        snapshot.setTitle("Title");
        snapshot.setChannelTitle("Channel");
        snapshot.setChannelId("channel-1");
        snapshot.setThumbnailUrl("https://example.com/thumb.jpg");
        snapshot.setViewCount(100L);
        snapshot.setCreatedAt(run.getCapturedAt());
        return snapshot;
    }
}
