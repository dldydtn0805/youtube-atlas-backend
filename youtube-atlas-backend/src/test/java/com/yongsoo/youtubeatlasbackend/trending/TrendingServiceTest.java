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
import com.yongsoo.youtubeatlasbackend.trending.api.SyncTrendingRequest;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeCatalogService;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasContentDetails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoSnippet;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoStatistics;

class TrendingServiceTest {

    private YouTubeCatalogService youTubeCatalogService;
    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private TrendSignalRepository trendSignalRepository;
    private TrendingService trendingService;

    @BeforeEach
    void setUp() {
        youTubeCatalogService = org.mockito.Mockito.mock(YouTubeCatalogService.class);
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);

        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getTrending().setCaptureSlotMinutes(5);
        atlasProperties.getTrending().setSyncMaxPagesPerSource(3);

        trendingService = new TrendingService(
            atlasProperties,
            youTubeCatalogService,
            trendRunRepository,
            trendSnapshotRepository,
            trendSignalRepository
        );
    }

    @Test
    void getSignalsAlwaysUsesAllCategoryId() {
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn("KR", "0", List.of("video-1")))
            .thenReturn(List.of());

        trendingService.getSignals("kr", "10", List.of("video-1"));

        verify(trendSignalRepository).findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn("KR", "0", List.of("video-1"));
    }

    @Test
    void getRealtimeSurgingUsesConfiguredThresholdAndAllCategoryId() {
        Instant capturedAt = Instant.parse("2026-04-01T05:30:00Z");
        TrendSignal signal = trendSignal("KR", "0", "video-1", 3, 10, "Surging", capturedAt);
        when(
            trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndRankChangeGreaterThanEqualOrderByRankChangeDescCurrentRankAsc(
                "KR",
                "0",
                5
            )
        ).thenReturn(List.of(signal));

        var response = trendingService.getRealtimeSurging("kr");

        verify(trendSignalRepository)
            .findByIdRegionCodeAndIdCategoryIdAndRankChangeGreaterThanEqualOrderByRankChangeDescCurrentRankAsc(
                "KR",
                "0",
                5
            );
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
        TrendRun latestRun = new TrendRun();
        latestRun.setRegionCode("KR");
        latestRun.setCategoryId("0");
        latestRun.setCategoryLabel("전체");
        latestRun.setSourceCategoryIds(List.of());
        latestRun.setSource("youtube-mostPopular");
        latestRun.setCapturedAt(Instant.parse("2026-04-01T06:00:00Z"));

        when(
            trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndRankChangeGreaterThanEqualOrderByRankChangeDescCurrentRankAsc(
                "KR",
                "0",
                5
            )
        ).thenReturn(List.of());
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0"))
            .thenReturn(Optional.of(latestRun));

        var response = trendingService.getRealtimeSurging("KR");

        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isZero();
        assertThat(response.capturedAt()).isEqualTo(latestRun.getCapturedAt());
    }

    @Test
    void getNewChartEntriesUsesAllCategoryId() {
        Instant capturedAt = Instant.parse("2026-04-01T05:30:00Z");
        TrendSignal signal = trendSignal("KR", "0", "video-2", 7, null, "New entry", capturedAt);
        signal.setNew(true);
        signal.setPreviousRank(null);

        when(trendSignalRepository.findNewEntriesByRegionCodeAndCategoryId("KR", "0"))
            .thenReturn(List.of(signal));

        var response = trendingService.getNewChartEntries("kr");

        verify(trendSignalRepository).findNewEntriesByRegionCodeAndCategoryId("KR", "0");
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
        TrendRun latestRun = new TrendRun();
        latestRun.setRegionCode("KR");
        latestRun.setCategoryId("0");
        latestRun.setCategoryLabel("전체");
        latestRun.setSourceCategoryIds(List.of());
        latestRun.setSource("youtube-mostPopular");
        latestRun.setCapturedAt(Instant.parse("2026-04-01T06:00:00Z"));

        when(trendSignalRepository.findNewEntriesByRegionCodeAndCategoryId("KR", "0"))
            .thenReturn(List.of());
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0"))
            .thenReturn(Optional.of(latestRun));

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

        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0"))
            .thenReturn(Optional.of(latestRun));
        when(trendSnapshotRepository.findByRunIdOrderByRankAsc(21L))
            .thenReturn(List.of(first, second));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(firstSignal));

        var response = trendingService.getTopVideos("kr", null);

        assertThat(response.categoryId()).isEqualTo("0");
        assertThat(response.items()).hasSize(2);
        assertThat(response.nextPageToken()).isNull();
        assertThat(response.items().getFirst().id()).isEqualTo("video-1");
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

        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0"))
            .thenReturn(Optional.of(latestRun));
        when(trendSnapshotRepository.findByRunIdOrderByRankAsc(22L))
            .thenReturn(snapshots);
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());

        var response = trendingService.getTopVideos("KR", "50");

        assertThat(response.items()).hasSize(5);
        assertThat(response.items().getFirst().id()).isEqualTo("video-51");
        assertThat(response.nextPageToken()).isNull();
    }

    @Test
    void getVideoHistoryReturnsObservedHistoryAndCurrentChartOut() {
        TrendRun firstRun = trendRun(11L, Instant.parse("2026-04-01T05:00:00Z"));
        TrendRun latestRun = trendRun(12L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSnapshot snapshot = snapshot(firstRun, "video-1", 23);

        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(List.of(snapshot));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0"))
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
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(eq("KR"), eq("0"), any()))
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
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(eq("US"), eq("0"), any()))
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
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc("KR", "0", 31L))
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
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0"))
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
        TrendSnapshot snapshot = new TrendSnapshot();
        snapshot.setRun(run);
        snapshot.setRegionCode("KR");
        snapshot.setCategoryId("0");
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
