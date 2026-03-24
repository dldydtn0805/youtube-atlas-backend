package com.yongsoo.youtubeatlasbackend.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteCategorySnippet;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteVideoCategoryItem;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteVideoPage;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasContentDetails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoSnippet;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoStatistics;

class YouTubeCatalogServiceTest {

    private YouTubeApiClient youTubeApiClient;
    private YouTubeCatalogService youTubeCatalogService;

    @BeforeEach
    void setUp() {
        youTubeApiClient = org.mockito.Mockito.mock(YouTubeApiClient.class);

        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getYoutube().getCache().setCategoriesTtlSeconds(60);
        atlasProperties.getYoutube().getCache().setVideoSectionsTtlSeconds(60);

        youTubeCatalogService = new YouTubeCatalogService(
            new CategoryCatalog(),
            new CatalogResponseCache(atlasProperties),
            youTubeApiClient,
            new ObjectMapper()
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

        assertThat(youTubeCatalogService.getPopularVideosByCategory("kr", "music", null).items()).hasSize(1);
        assertThat(youTubeCatalogService.getPopularVideosByCategory("KR", "music", null).items()).hasSize(1);

        verify(youTubeApiClient, times(1)).fetchVideoCategories("KR");
        verify(youTubeApiClient, times(1)).fetchMostPopularVideos("KR", "10", null);
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
}
