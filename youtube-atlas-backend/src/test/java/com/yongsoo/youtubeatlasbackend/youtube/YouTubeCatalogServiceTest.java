package com.yongsoo.youtubeatlasbackend.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.common.ExternalServiceException;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteCategorySnippet;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteVideoCategoryItem;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteVideoPage;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasContentDetails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoSnippet;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoStatistics;

class YouTubeCatalogServiceTest {

    private YouTubeApiClient youTubeApiClient;
    private TrendSignalRepository trendSignalRepository;
    private YouTubeCatalogService youTubeCatalogService;

    @BeforeEach
    void setUp() {
        youTubeApiClient = org.mockito.Mockito.mock(YouTubeApiClient.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        when(youTubeApiClient.isIgnorableCategoryFetchError(any())).thenAnswer(invocation -> {
            RuntimeException exception = invocation.getArgument(0);
            String message = exception.getMessage();
            return message != null && (
                message.contains("The requested video chart is not supported or is not available.")
                    || message.contains("Requested entity was not found.")
            );
        });

        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getYoutube().getCache().setCategoriesTtlSeconds(60);
        atlasProperties.getYoutube().getCache().setVideoSectionsTtlSeconds(60);

        youTubeCatalogService = new YouTubeCatalogService(
            new CategoryCatalog(),
            new CatalogResponseCache(atlasProperties),
            youTubeApiClient,
            trendSignalRepository
        );
    }

    @Test
    void getCategoriesCachesPerRegion() {
        when(youTubeApiClient.fetchVideoCategories("KR")).thenReturn(List.of(
            new RemoteVideoCategoryItem("10", new RemoteCategorySnippet(true, "Music"))
        ));

        assertThat(youTubeCatalogService.getCategories("kr")).hasSize(2);
        assertThat(youTubeCatalogService.getCategories("KR")).hasSize(2);

        verify(youTubeApiClient, times(1)).fetchVideoCategories("KR");
    }

    @Test
    void getPopularVideosByCategoryCachesRepeatedSelections() {
        when(youTubeApiClient.fetchVideoCategories("KR")).thenReturn(List.of(
            new RemoteVideoCategoryItem("10", new RemoteCategorySnippet(true, "Music"))
        ));
        when(youTubeApiClient.fetchMostPopularVideos("KR", "10", null)).thenReturn(
            new RemoteVideoPage(List.of(video("video-1", "10")), null)
        );
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn("KR", "10", List.of("video-1")))
            .thenReturn(List.of());

        assertThat(youTubeCatalogService.getPopularVideosByCategory("kr", "10", null).items()).hasSize(1);
        assertThat(youTubeCatalogService.getPopularVideosByCategory("KR", "10", null).items()).hasSize(1);

        verify(youTubeApiClient, times(1)).fetchVideoCategories("KR");
        verify(youTubeApiClient, times(1)).fetchMostPopularVideos("KR", "10", null);
        verify(trendSignalRepository, times(2))
            .findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn("KR", "10", List.of("video-1"));
    }

    @Test
    void getPopularVideosByCategoryAddsTrendSignalToItems() {
        when(youTubeApiClient.fetchVideoCategories("KR")).thenReturn(List.of(
            new RemoteVideoCategoryItem("10", new RemoteCategorySnippet(true, "Music"))
        ));
        when(youTubeApiClient.fetchMostPopularVideos("KR", "10", null)).thenReturn(
            new RemoteVideoPage(List.of(video("video-1", "10")), null)
        );
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn(eq("KR"), eq("10"), eq(List.of("video-1"))))
            .thenReturn(List.of(trendSignal("KR", "10", "video-1")));

        var response = youTubeCatalogService.getPopularVideosByCategory("KR", "10", null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().trend()).isNotNull();
        assertThat(response.items().getFirst().trend().currentRank()).isEqualTo(3);
        assertThat(response.items().getFirst().trend().rankChange()).isEqualTo(2);
        assertThat(response.items().getFirst().trend().capturedAt()).isEqualTo(Instant.parse("2026-03-24T12:00:00Z"));
    }

    @Test
    void getPopularVideosByCategoryThrowsWhenSourceCategoryIsUnsupported() {
        when(youTubeApiClient.fetchVideoCategories("KR")).thenReturn(List.of(
            new RemoteVideoCategoryItem("24", new RemoteCategorySnippet(true, "Entertainment"))
        ));
        when(youTubeApiClient.fetchMostPopularVideos("KR", "24", null))
            .thenThrow(new ExternalServiceException("Requested entity was not found."));

        assertThatThrownBy(() -> youTubeCatalogService.getPopularVideosByCategory("KR", "24", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("현재 KR에서는 요청한 카테고리 인기 차트를 지원하지 않습니다.");
    }

    @Test
    void fetchMergedCategoryVideosCollectsMultiplePagesForTrendingSync() {
        when(youTubeApiClient.fetchMostPopularVideos("KR", "10", null)).thenReturn(
            new RemoteVideoPage(List.of(video("video-1", "10")), "page-2")
        );
        when(youTubeApiClient.fetchMostPopularVideos("KR", "10", "page-2")).thenReturn(
            new RemoteVideoPage(List.of(video("video-2", "10")), "page-3")
        );
        when(youTubeApiClient.fetchMostPopularVideos("KR", "10", "page-3")).thenReturn(
            new RemoteVideoPage(List.of(video("video-3", "10")), null)
        );

        var videos = youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of("10"), 3);

        assertThat(videos).extracting(AtlasVideo::id).containsExactly("video-1", "video-2", "video-3");
    }

    @Test
    void fetchMergedCategoryVideosCachesUnsupportedSourcesPerRegion() {
        when(youTubeApiClient.fetchMostPopularVideos("KR", "24", null))
            .thenThrow(new ExternalServiceException("Requested entity was not found."));
        when(youTubeApiClient.fetchMostPopularVideos("KR", "23", null)).thenReturn(
            new RemoteVideoPage(List.of(video("video-1", "23")), null)
        );

        assertThat(youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of("24", "23"), 1))
            .extracting(AtlasVideo::id)
            .containsExactly("video-1");
        assertThat(youTubeCatalogService.fetchMergedCategoryVideos("KR", List.of("24", "23"), 1))
            .extracting(AtlasVideo::id)
            .containsExactly("video-1");

        verify(youTubeApiClient, times(1)).fetchMostPopularVideos("KR", "24", null);
        verify(youTubeApiClient, times(2)).fetchMostPopularVideos("KR", "23", null);
    }

    private AtlasVideo video(String id, String categoryId) {
        return new AtlasVideo(
            id,
            new AtlasContentDetails("PT10M"),
            new AtlasVideoSnippet(
                "Title",
                "Channel",
                categoryId,
                OffsetDateTime.parse("2026-03-24T10:00:00Z"),
                null
            ),
            new AtlasVideoStatistics(100L)
        );
    }

    private TrendSignal trendSignal(String regionCode, String categoryId, String videoId) {
        TrendSignal signal = new TrendSignal();
        signal.setId(new TrendSignalId(regionCode, categoryId, videoId));
        signal.setCategoryLabel("Music");
        signal.setCurrentRunId(10L);
        signal.setPreviousRunId(9L);
        signal.setCurrentRank(3);
        signal.setPreviousRank(5);
        signal.setRankChange(2);
        signal.setCurrentViewCount(1_000L);
        signal.setPreviousViewCount(700L);
        signal.setViewCountDelta(300L);
        signal.setNew(false);
        signal.setTitle("Title");
        signal.setChannelTitle("Channel");
        signal.setThumbnailUrl("https://example.com/thumb.jpg");
        signal.setCapturedAt(Instant.parse("2026-03-24T12:00:00Z"));
        signal.setUpdatedAt(Instant.parse("2026-03-24T12:30:00Z"));
        return signal;
    }
}
