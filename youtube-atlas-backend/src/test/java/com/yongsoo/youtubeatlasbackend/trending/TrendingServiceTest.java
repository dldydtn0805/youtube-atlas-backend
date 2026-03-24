package com.yongsoo.youtubeatlasbackend.trending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
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
                "10",
                OffsetDateTime.parse("2026-03-24T10:00:00Z"),
                null
            ),
            new AtlasVideoStatistics(100L)
        );
    }
}
