package com.yongsoo.youtubeatlasbackend.youtube;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategoryResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;

@Component
public class CatalogResponseCache {

    private final Cache<String, List<VideoCategoryResponse>> categories;
    private final Cache<VideoSectionCacheKey, VideoCategorySectionResponse> videoSections;
    private final Cache<SourceSupportCacheKey, Boolean> unsupportedSourceCategories;

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
        this.unsupportedSourceCategories = Caffeine.newBuilder()
            .maximumSize(Math.max(1, cacheProperties.getCategoriesMaxSize() * 8))
            .expireAfterWrite(Duration.ofSeconds(Math.max(1, cacheProperties.getCategoriesTtlSeconds())))
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

    public boolean isKnownUnsupportedSource(String regionCode, String sourceCategoryId) {
        if (!StringUtils.hasText(regionCode) || !StringUtils.hasText(sourceCategoryId)) {
            return false;
        }

        return Boolean.TRUE.equals(unsupportedSourceCategories.getIfPresent(
            new SourceSupportCacheKey(regionCode.trim().toUpperCase(), sourceCategoryId.trim())
        ));
    }

    public void markUnsupportedSource(String regionCode, String sourceCategoryId) {
        if (!StringUtils.hasText(regionCode) || !StringUtils.hasText(sourceCategoryId)) {
            return;
        }

        unsupportedSourceCategories.put(
            new SourceSupportCacheKey(regionCode.trim().toUpperCase(), sourceCategoryId.trim()),
            true
        );
    }

    private record VideoSectionCacheKey(
        String regionCode,
        String categoryId,
        String pageToken
    ) {
    }

    private record SourceSupportCacheKey(
        String regionCode,
        String sourceCategoryId
    ) {
    }
}
