package com.yongsoo.youtubeatlasbackend.youtube;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategoryResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;

@Component
public class CatalogResponseCache {

    private final Cache<String, List<VideoCategoryResponse>> categories;
    private final Cache<VideoSectionCacheKey, VideoCategorySectionResponse> videoSections;

    public CatalogResponseCache(AtlasProperties atlasProperties) {
        AtlasProperties.Cache cacheProperties = atlasProperties.getYoutube().getCache();
        this.categories = Caffeine.newBuilder()
            .maximumSize(Math.max(1, cacheProperties.getCategoriesMaxSize()))
            .expireAfterWrite(Duration.ofSeconds(Math.max(1, cacheProperties.getCategoriesTtlSeconds())))
            .build();
        this.videoSections = Caffeine.newBuilder()
            .maximumSize(Math.max(1, cacheProperties.getVideoSectionsMaxSize()))
            .expireAfterWrite(Duration.ofSeconds(Math.max(1, cacheProperties.getVideoSectionsTtlSeconds())))
            .build();
    }

    public List<VideoCategoryResponse> getCategories(String regionCode, Supplier<List<VideoCategoryResponse>> loader) {
        return categories.get(regionCode, ignored -> List.copyOf(loader.get()));
    }

    public VideoCategorySectionResponse getVideoSection(
        String regionCode,
        String categoryId,
        String pageToken,
        Supplier<VideoCategorySectionResponse> loader
    ) {
        return videoSections.get(new VideoSectionCacheKey(regionCode, categoryId, pageToken), ignored -> loader.get());
    }

    private record VideoSectionCacheKey(
        String regionCode,
        String categoryId,
        String pageToken
    ) {
    }
}
