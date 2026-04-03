package com.yongsoo.youtubeatlasbackend.youtube;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
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

    private final CategoryCatalog categoryCatalog;
    private final CatalogResponseCache catalogResponseCache;
    private final YouTubeApiClient youTubeApiClient;
    private final TrendSignalRepository trendSignalRepository;

    public YouTubeCatalogService(
        CategoryCatalog categoryCatalog,
        CatalogResponseCache catalogResponseCache,
        YouTubeApiClient youTubeApiClient,
        TrendSignalRepository trendSignalRepository
    ) {
        this.categoryCatalog = categoryCatalog;
        this.catalogResponseCache = catalogResponseCache;
        this.youTubeApiClient = youTubeApiClient;
        this.trendSignalRepository = trendSignalRepository;
    }

    public List<VideoCategoryResponse> getCategories(String regionCode) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        return catalogResponseCache.getCategories(normalizedRegionCode, () -> loadCategories(normalizedRegionCode));
    }

    public VideoCategorySectionResponse getPopularVideosByCategory(String regionCode, String categoryId, String pageToken) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        VideoCategorySectionResponse section = catalogResponseCache.getVideoSection(
            normalizedRegionCode,
            categoryId,
            pageToken,
            () -> loadPopularVideosByCategory(normalizedRegionCode, categoryId, pageToken)
        );
        return attachTrendSignals(normalizedRegionCode, categoryId, section);
    }

    public VideoItemResponse getVideoById(String videoId) {
        String normalizedVideoId = normalizeVideoId(videoId);

        return youTubeApiClient.fetchVideosByIds(List.of(normalizedVideoId)).stream()
            .findFirst()
            .map(this::toVideoResponse)
            .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 영상입니다: " + normalizedVideoId));
    }

    public VideoCategorySectionResponse getPopularVideosForChannels(
        String regionCode,
        List<String> channelIds,
        String pageToken
    ) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        Set<String> normalizedChannelIds = normalizeChannelIds(channelIds);

        if (normalizedChannelIds.isEmpty()) {
            return new VideoCategorySectionResponse(
                "favorite-streamers",
                "즐겨찾기 채널",
                "전체 인기 영상 중 즐겨찾기한 채널의 영상만 모았습니다.",
                List.of(),
                null
            );
        }

        String nextPageToken = pageToken;
        List<VideoItemResponse> matchedItems = new ArrayList<>();

        do {
            RemoteVideoPage page = fetchPopularVideosPageForSource(normalizedRegionCode, null, nextPageToken);
            for (AtlasVideo item : page.items()) {
                String channelId = item.snippet() != null ? item.snippet().channelId() : null;
                if (StringUtils.hasText(channelId) && normalizedChannelIds.contains(channelId.trim())) {
                    matchedItems.add(toVideoResponse(item));
                }
            }
            nextPageToken = page.nextPageToken();
        } while (matchedItems.size() < MIN_VIDEOS_PER_SOURCE_PAGE && StringUtils.hasText(nextPageToken));

        return attachTrendSignals(
            normalizedRegionCode,
            CategoryCatalog.ALL_VIDEO_CATEGORY_ID,
            new VideoCategorySectionResponse(
                "favorite-streamers",
                "즐겨찾기 채널",
                "전체 인기 영상 중 즐겨찾기한 채널의 영상만 모았습니다.",
                List.copyOf(matchedItems),
                nextPageToken
            )
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

        List<VideoCategoryResponse> responses = new ArrayList<>();
        responses.add(toResponse(categoryCatalog.allCategory()));
        categories.stream()
            .sorted(Comparator.comparing(AtlasVideoCategory::label, Comparator.naturalOrder()))
            .map(this::toResponse)
            .forEach(responses::add);

        if (responses.size() == 1) {
            throw new IllegalArgumentException("표시할 수 있는 카테고리가 없습니다.");
        }

        return List.copyOf(responses);
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

        String sourceCategoryId = category.sourceIds().isEmpty() ? category.id() : category.sourceIds().getFirst();
        RemoteVideoPage page;

        try {
            page = fetchPopularVideosPageForSource(normalizedRegionCode, sourceCategoryId, pageToken);
        } catch (UnsupportedSourceCategoryException exception) {
            throw unsupportedCategoryException(normalizedRegionCode);
        }

        return new VideoCategorySectionResponse(
            category.id(),
            category.label(),
            category.description(),
            page.items().stream().map(this::toVideoResponse).toList(),
            page.nextPageToken()
        );
    }

    public List<AtlasVideo> fetchMergedCategoryVideos(String regionCode, List<String> sourceCategoryIds) {
        return fetchMergedCategoryVideos(regionCode, sourceCategoryIds, 1);
    }

    public List<AtlasVideo> fetchMergedCategoryVideos(String regionCode, List<String> sourceCategoryIds, int maxPagesPerSource) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        int pageLimit = Math.max(1, maxPagesPerSource);

        if (sourceCategoryIds == null || sourceCategoryIds.isEmpty()) {
            return fetchPopularVideosPagesForSource(normalizedRegionCode, null, pageLimit);
        }

        if (sourceCategoryIds.size() == 1) {
            return fetchPopularVideosPagesForSource(normalizedRegionCode, sourceCategoryIds.getFirst(), pageLimit);
        }

        List<AtlasVideo> mergedVideos = new ArrayList<>();
        int supportedSourceCount = 0;

        for (String sourceCategoryId : sourceCategoryIds) {
            try {
                mergedVideos.addAll(fetchPopularVideosPagesForSource(normalizedRegionCode, sourceCategoryId, pageLimit));
                supportedSourceCount++;
            } catch (RuntimeException exception) {
                if (!isIgnorableMergedSourceFetchError(exception, normalizedRegionCode)) {
                    throw exception;
                }
            }
        }

        if (supportedSourceCount == 0) {
            throw new IllegalArgumentException("현재 " + normalizedRegionCode + "에서는 요청한 병합 카테고리 인기 차트를 지원하지 않습니다.");
        }

        return dedupeVideos(mergedVideos);
    }

    private List<AtlasVideo> fetchPopularVideosPagesForSource(String regionCode, String sourceCategoryId, int maxPages) {
        String nextPageToken = null;
        int pageLimit = Math.max(1, maxPages);
        int fetchedPages = 0;
        List<AtlasVideo> items = new ArrayList<>();

        if (catalogResponseCache.isKnownUnsupportedSource(regionCode, sourceCategoryId)) {
            throw new IllegalArgumentException("현재 " + regionCode + "에서는 요청한 카테고리 인기 차트를 지원하지 않습니다.");
        }

        try {
            do {
                RemoteVideoPage page = youTubeApiClient.fetchMostPopularVideos(regionCode, sourceCategoryId, nextPageToken);
                items.addAll(page.items());
                nextPageToken = page.nextPageToken();
                fetchedPages++;
            } while (fetchedPages < pageLimit && StringUtils.hasText(nextPageToken));
        } catch (RuntimeException exception) {
            if (!youTubeApiClient.isIgnorableCategoryFetchError(exception)) {
                throw exception;
            }

            if (!StringUtils.hasText(sourceCategoryId)) {
                throw exception;
            }

            catalogResponseCache.markUnsupportedSource(regionCode, sourceCategoryId);
            throw new IllegalArgumentException("현재 " + regionCode + "에서는 요청한 카테고리 인기 차트를 지원하지 않습니다.");
        }

        return items;
    }

    private RemoteVideoPage fetchPopularVideosPageForSource(String regionCode, String sourceCategoryId, String pageToken) {
        String nextPageToken = pageToken;
        List<AtlasVideo> items = new ArrayList<>();
        RemoteVideoPage currentPage;

        if (catalogResponseCache.isKnownUnsupportedSource(regionCode, sourceCategoryId)) {
            throw new UnsupportedSourceCategoryException(sourceCategoryId);
        }

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

            catalogResponseCache.markUnsupportedSource(regionCode, sourceCategoryId);
            throw new UnsupportedSourceCategoryException(sourceCategoryId, exception);
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

    private VideoCategoryResponse toResponse(AtlasVideoCategory category) {
        return new VideoCategoryResponse(category.id(), category.label(), category.description(), category.sourceIds());
    }

    private VideoCategorySectionResponse attachTrendSignals(
        String regionCode,
        String categoryId,
        VideoCategorySectionResponse section
    ) {
        if (!CategoryCatalog.ALL_VIDEO_CATEGORY_ID.equals(categoryId)) {
            return section;
        }

        if (section.items().isEmpty()) {
            return section;
        }

        List<String> videoIds = section.items().stream()
            .map(VideoItemResponse::id)
            .toList();
        Map<String, TrendSignal> signalsByVideoId = new HashMap<>();

        for (TrendSignal signal : trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn(
            regionCode,
            categoryId.trim(),
            videoIds
        )) {
            signalsByVideoId.put(signal.getId().getVideoId(), signal);
        }

        List<VideoItemResponse> itemsWithTrends = section.items().stream()
            .map(item -> withTrend(item, signalsByVideoId.get(item.id())))
            .toList();

        return new VideoCategorySectionResponse(
            section.categoryId(),
            section.label(),
            section.description(),
            itemsWithTrends,
            section.nextPageToken()
        );
    }

    private VideoItemResponse withTrend(VideoItemResponse item, TrendSignal signal) {
        return new VideoItemResponse(
            item.id(),
            item.contentDetails(),
            item.snippet(),
            item.statistics(),
            signal != null ? toTrendResponse(signal) : null
        );
    }

    private VideoItemResponse.TrendResponse toTrendResponse(TrendSignal signal) {
        return new VideoItemResponse.TrendResponse(
            signal.getCategoryLabel(),
            signal.getCurrentRank(),
            signal.getPreviousRank(),
            signal.getRankChange(),
            signal.getCurrentViewCount(),
            signal.getPreviousViewCount(),
            signal.getViewCountDelta(),
            signal.isNew(),
            signal.getCapturedAt()
        );
    }

    private VideoItemResponse toVideoResponse(AtlasVideo video) {
        return new VideoItemResponse(
            video.id(),
            new VideoItemResponse.ContentDetailsResponse(video.contentDetails() != null ? video.contentDetails().duration() : null),
            new VideoItemResponse.SnippetResponse(
                video.snippet() != null ? video.snippet().title() : null,
                video.snippet() != null ? video.snippet().channelTitle() : null,
                video.snippet() != null ? video.snippet().channelId() : null,
                video.snippet() != null ? video.snippet().categoryId() : null,
                video.snippet() != null && video.snippet().publishedAt() != null ? video.snippet().publishedAt().toString() : null,
                toThumbnailsResponse(video.snippet() != null ? video.snippet().thumbnails() : null)
            ),
            new VideoItemResponse.StatisticsResponse(video.statistics() != null ? video.statistics().viewCount() : null),
            null
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

    private String normalizeVideoId(String videoId) {
        if (!StringUtils.hasText(videoId)) {
            throw new IllegalArgumentException("videoId는 비어 있을 수 없습니다.");
        }

        return videoId.trim();
    }

    private Set<String> normalizeChannelIds(List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            return Set.of();
        }

        Set<String> normalizedChannelIds = new HashSet<>();
        for (String channelId : channelIds) {
            if (StringUtils.hasText(channelId)) {
                normalizedChannelIds.add(channelId.trim());
            }
        }
        return Set.copyOf(normalizedChannelIds);
    }

    private boolean isIgnorableMergedSourceFetchError(RuntimeException exception, String regionCode) {
        return youTubeApiClient.isIgnorableCategoryFetchError(exception)
            || unsupportedCategoryException(regionCode).getMessage().equals(exception.getMessage());
    }

    private IllegalArgumentException unsupportedCategoryException(String regionCode) {
        return new IllegalArgumentException("현재 " + regionCode + "에서는 요청한 카테고리 인기 차트를 지원하지 않습니다.");
    }

    private static class UnsupportedSourceCategoryException extends RuntimeException {

        private UnsupportedSourceCategoryException(String sourceCategoryId) {
            super("지원되지 않는 유튜브 카테고리입니다: " + sourceCategoryId);
        }

        private UnsupportedSourceCategoryException(String sourceCategoryId, RuntimeException cause) {
            super("지원되지 않는 유튜브 카테고리입니다: " + sourceCategoryId, cause);
        }
    }
}
