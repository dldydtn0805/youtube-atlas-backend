package com.yongsoo.youtubeatlasbackend.trending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
        when(youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of(), 3))
            .thenReturn(List.of(video("video-1")));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(eq("KR"), eq("0"), any()))
            .thenReturn(Optional.empty());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());
        when(trendSignalRepository.findById(any())).thenReturn(Optional.empty());

        var response = trendingService.sync(new SyncTrendingRequest("KR", "10", "Music", List.of("10")));

        ArgumentCaptor<TrendRun> trendRunCaptor = ArgumentCaptor.forClass(TrendRun.class);
        verify(trendRunRepository).save(trendRunCaptor.capture());
        assertThat(trendRunCaptor.getValue().getCategoryId()).isEqualTo("0");
        assertThat(trendRunCaptor.getValue().getCategoryLabel()).isEqualTo("전체");
        assertThat(trendRunCaptor.getValue().getSourceCategoryIds()).isEmpty();

        verify(youTubeCatalogService).fetchMergedCategoryVideos("KR", List.of(), 3);
        verify(trendSignalRepository).findByIdRegionCodeAndIdCategoryId("KR", "0");
        assertThat(response.categoryId()).isEqualTo("0");
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
        int rankChange,
        String title,
        Instant capturedAt
    ) {
        TrendSignal signal = new TrendSignal();
        signal.setId(new TrendSignalId(regionCode, categoryId, videoId));
        signal.setCategoryLabel("전체");
        signal.setCurrentRunId(1L);
        signal.setPreviousRunId(2L);
        signal.setCurrentRank(currentRank);
        signal.setPreviousRank(currentRank + rankChange);
        signal.setRankChange(rankChange);
        signal.setCurrentViewCount(100L);
        signal.setPreviousViewCount(90L);
        signal.setViewCountDelta(10L);
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
