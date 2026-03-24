package com.yongsoo.youtubeatlasbackend.youtube;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteVideoCategoryItem;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeApiClient.RemoteVideoPage;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategoryResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnail;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideoCategory;

@Service
public class YouTubeCatalogService {

    private static final int MIN_VIDEOS_PER_SOURCE_PAGE = 12;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final CategoryCatalog categoryCatalog;
    private final CatalogResponseCache catalogResponseCache;
    private final YouTubeApiClient youTubeApiClient;
    private final ObjectMapper objectMapper;

    public YouTubeCatalogService(
        CategoryCatalog categoryCatalog,
        CatalogResponseCache catalogResponseCache,
        YouTubeApiClient youTubeApiClient,
        ObjectMapper objectMapper
    ) {
        this.categoryCatalog = categoryCatalog;
        this.catalogResponseCache = catalogResponseCache;
        this.youTubeApiClient = youTubeApiClient;
        this.objectMapper = objectMapper;
    }

    public List<VideoCategoryResponse> getCategories(String regionCode) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        return catalogResponseCache.getCategories(normalizedRegionCode, () -> loadCategories(normalizedRegionCode));
    }

    public VideoCategorySectionResponse getPopularVideosByCategory(String regionCode, String categoryId, String pageToken) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        return catalogResponseCache.getVideoSection(
            normalizedRegionCode,
            categoryId,
            pageToken,
            () -> loadPopularVideosByCategory(normalizedRegionCode, categoryId, pageToken)
        );
    }

    private List<VideoCategoryResponse> loadCategories(String normalizedRegionCode) {
        List<RemoteVideoCategoryItem> remoteCategories = youTubeApiClient.fetchVideoCategories(normalizedRegionCode);
        List<AtlasVideoCategory> categories = new ArrayList<>();

        for (RemoteVideoCategoryItem remoteCategory : remoteCategories) {
            if (remoteCategory.snippet() == null) {
                continue;
            }

            AtlasVideoCategory category = categoryCatalog.toCategory(
                remoteCategory.id(),
                remoteCategory.snippet().title(),
                remoteCategory.snippet().assignable()
            );

            if (category != null) {
                categories.add(category);
            }
        }

        List<VideoCategoryResponse> mergedResponses = new ArrayList<>();
        mergedResponses.add(toResponse(categoryCatalog.allCategory()));

        for (AtlasVideoCategory category : categoryCatalog.mergeCategories(categories)) {
            mergedResponses.add(toResponse(category));
        }

        if (mergedResponses.size() == 1) {
            throw new IllegalArgumentException("표시할 수 있는 카테고리가 없습니다.");
        }

        return List.copyOf(mergedResponses);
    }

    private VideoCategorySectionResponse loadPopularVideosByCategory(String normalizedRegionCode, String categoryId, String pageToken) {
        if (CategoryCatalog.ALL_VIDEO_CATEGORY_ID.equals(categoryId)) {
            RemoteVideoPage page = fetchPopularVideosPageForSource(normalizedRegionCode, null, pageToken);
            return new VideoCategorySectionResponse(
                categoryId,
                categoryCatalog.allCategory().label(),
                categoryCatalog.allCategory().description(),
                page.items().stream().map(this::toVideoResponse).toList(),
                page.nextPageToken()
            );
        }

        AtlasVideoCategory category = getCategories(normalizedRegionCode).stream()
            .filter(item -> item.id().equals(categoryId))
            .findFirst()
            .map(item -> new AtlasVideoCategory(item.id(), item.label(), item.description(), item.sourceIds()))
            .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 카테고리입니다: " + categoryId));

        if (category.sourceIds().size() <= 1) {
            String sourceCategoryId = category.sourceIds().isEmpty() ? category.id() : category.sourceIds().getFirst();
            RemoteVideoPage page = fetchPopularVideosPageForSource(normalizedRegionCode, sourceCategoryId, pageToken);

            return new VideoCategorySectionResponse(
                category.id(),
                category.label(),
                category.description(),
                page.items().stream().map(this::toVideoResponse).toList(),
                page.nextPageToken()
            );
        }

        MergedCategoryPageState pageState = parseMergedCategoryPageState(pageToken);
        List<String> activeSourceIds = category.sourceIds().stream()
            .filter(sourceId -> !pageState.exhaustedSourceIds().contains(sourceId))
            .toList();
        Map<String, String> nextPageTokens = new LinkedHashMap<>();
        List<String> exhaustedSourceIds = new ArrayList<>(pageState.exhaustedSourceIds());
        List<AtlasVideo> mergedVideos = new ArrayList<>();

        for (String sourceId : activeSourceIds) {
            RemoteVideoPage sourcePage = fetchPopularVideosPageForSource(
                normalizedRegionCode,
                sourceId,
                pageState.nextPageTokens().get(sourceId)
            );
            mergedVideos.addAll(sourcePage.items());

            if (StringUtils.hasText(sourcePage.nextPageToken())) {
                nextPageTokens.put(sourceId, sourcePage.nextPageToken());
            } else {
                exhaustedSourceIds.add(sourceId);
            }
        }

        return new VideoCategorySectionResponse(
            category.id(),
            category.label(),
            category.description(),
            dedupeVideos(mergedVideos).stream().map(this::toVideoResponse).toList(),
            buildMergedCategoryPageToken(new MergedCategoryPageState(exhaustedSourceIds, nextPageTokens), category.sourceIds())
        );
    }

    public List<AtlasVideo> fetchMergedCategoryVideos(String regionCode, List<String> sourceCategoryIds) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);

        if (sourceCategoryIds == null || sourceCategoryIds.isEmpty()) {
            return fetchPopularVideosPageForSource(normalizedRegionCode, null, null).items();
        }

        if (sourceCategoryIds.size() == 1) {
            return fetchPopularVideosPageForSource(normalizedRegionCode, sourceCategoryIds.getFirst(), null).items();
        }

        List<AtlasVideo> mergedVideos = new ArrayList<>();
        int supportedSourceCount = 0;

        for (String sourceCategoryId : sourceCategoryIds) {
            try {
                mergedVideos.addAll(fetchPopularVideosPageForSource(normalizedRegionCode, sourceCategoryId, null).items());
                supportedSourceCount++;
            } catch (IllegalArgumentException exception) {
                if (!youTubeApiClient.isIgnorableCategoryFetchError(exception)) {
                    throw exception;
                }
            }
        }

        if (supportedSourceCount == 0) {
            throw new IllegalArgumentException("현재 " + normalizedRegionCode + "에서는 요청한 병합 카테고리 인기 차트를 지원하지 않습니다.");
        }

        return dedupeVideos(mergedVideos);
    }

    private RemoteVideoPage fetchPopularVideosPageForSource(String regionCode, String sourceCategoryId, String pageToken) {
        String nextPageToken = pageToken;
        List<AtlasVideo> items = new ArrayList<>();
        RemoteVideoPage currentPage;

        try {
            do {
                currentPage = youTubeApiClient.fetchMostPopularVideos(regionCode, sourceCategoryId, nextPageToken);
                items.addAll(currentPage.items());
                nextPageToken = currentPage.nextPageToken();
            } while (items.size() < MIN_VIDEOS_PER_SOURCE_PAGE && StringUtils.hasText(nextPageToken));
        } catch (RuntimeException exception) {
            if (!youTubeApiClient.isIgnorableCategoryFetchError(exception)) {
                throw exception;
            }

            if (!StringUtils.hasText(sourceCategoryId)) {
                throw exception;
            }

            throw new IllegalArgumentException("현재 " + regionCode + "에서는 요청한 카테고리 인기 차트를 지원하지 않습니다.");
        }

        return new RemoteVideoPage(items, nextPageToken);
    }

    private List<AtlasVideo> dedupeVideos(List<AtlasVideo> items) {
        Map<String, AtlasVideo> uniqueItems = new LinkedHashMap<>();

        for (AtlasVideo item : items) {
            uniqueItems.putIfAbsent(item.id(), item);
        }

        return new ArrayList<>(uniqueItems.values());
    }

    private MergedCategoryPageState parseMergedCategoryPageState(String pageToken) {
        if (!StringUtils.hasText(pageToken)) {
            return new MergedCategoryPageState(List.of(), Map.of());
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(pageToken, MAP_TYPE);
            List<String> exhaustedSourceIds = parsed.get("exhaustedSourceIds") instanceof List<?> rawExhaustedIds
                ? rawExhaustedIds.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                : List.of();
            Map<String, String> nextPageTokens = new LinkedHashMap<>();

            if (parsed.get("nextPageTokens") instanceof Map<?, ?> rawTokens) {
                rawTokens.forEach((key, value) -> {
                    if (key instanceof String stringKey && value instanceof String stringValue) {
                        nextPageTokens.put(stringKey, stringValue);
                    }
                });
            }

            return new MergedCategoryPageState(exhaustedSourceIds, nextPageTokens);
        } catch (JsonProcessingException ignored) {
            return new MergedCategoryPageState(List.of(), Map.of());
        }
    }

    private String buildMergedCategoryPageToken(MergedCategoryPageState pageState, List<String> sourceIds) {
        boolean hasRemainingPages = sourceIds.stream().anyMatch(sourceId -> StringUtils.hasText(pageState.nextPageTokens().get(sourceId)));
        if (!hasRemainingPages) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(pageState);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("병합 카테고리 페이지 토큰을 직렬화할 수 없습니다.", exception);
        }
    }

    private VideoCategoryResponse toResponse(AtlasVideoCategory category) {
        return new VideoCategoryResponse(category.id(), category.label(), category.description(), category.sourceIds());
    }

    private VideoItemResponse toVideoResponse(AtlasVideo video) {
        return new VideoItemResponse(
            video.id(),
            new VideoItemResponse.ContentDetailsResponse(video.contentDetails() != null ? video.contentDetails().duration() : null),
            new VideoItemResponse.SnippetResponse(
                video.snippet() != null ? video.snippet().title() : null,
                video.snippet() != null ? video.snippet().channelTitle() : null,
                video.snippet() != null ? video.snippet().categoryId() : null,
                video.snippet() != null && video.snippet().publishedAt() != null ? video.snippet().publishedAt().toString() : null,
                toThumbnailsResponse(video.snippet() != null ? video.snippet().thumbnails() : null)
            ),
            new VideoItemResponse.StatisticsResponse(video.statistics() != null ? video.statistics().viewCount() : null)
        );
    }

    private VideoItemResponse.ThumbnailsResponse toThumbnailsResponse(AtlasThumbnails thumbnails) {
        if (thumbnails == null) {
            return new VideoItemResponse.ThumbnailsResponse(null, null, null, null, null);
        }

        return new VideoItemResponse.ThumbnailsResponse(
            toThumbnailResponse(thumbnails.defaultThumbnail()),
            toThumbnailResponse(thumbnails.medium()),
            toThumbnailResponse(thumbnails.high()),
            toThumbnailResponse(thumbnails.standard()),
            toThumbnailResponse(thumbnails.maxres())
        );
    }

    private VideoItemResponse.ThumbnailResponse toThumbnailResponse(AtlasThumbnail thumbnail) {
        if (thumbnail == null) {
            return null;
        }

        return new VideoItemResponse.ThumbnailResponse(thumbnail.url(), thumbnail.width(), thumbnail.height());
    }

    private String normalizeRegionCode(String regionCode) {
        if (!StringUtils.hasText(regionCode)) {
            throw new IllegalArgumentException("regionCode는 비어 있을 수 없습니다.");
        }

        return regionCode.trim().toUpperCase();
    }

    private record MergedCategoryPageState(
        List<String> exhaustedSourceIds,
        Map<String, String> nextPageTokens
    ) {
    }
}
