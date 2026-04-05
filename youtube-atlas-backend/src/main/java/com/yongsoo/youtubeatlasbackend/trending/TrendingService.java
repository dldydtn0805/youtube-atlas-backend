package com.yongsoo.youtubeatlasbackend.trending;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.trending.api.NewChartEntriesResponse;
import com.yongsoo.youtubeatlasbackend.trending.api.RealtimeSurgingResponse;
import com.yongsoo.youtubeatlasbackend.trending.api.SyncTrendingRequest;
import com.yongsoo.youtubeatlasbackend.trending.api.SyncTrendingResponse;
import com.yongsoo.youtubeatlasbackend.trending.api.TrendSignalResponse;
import com.yongsoo.youtubeatlasbackend.trending.api.VideoRankHistoryPointResponse;
import com.yongsoo.youtubeatlasbackend.trending.api.VideoRankHistoryResponse;
import com.yongsoo.youtubeatlasbackend.youtube.CategoryCatalog;
import com.yongsoo.youtubeatlasbackend.youtube.YouTubeCatalogService;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnail;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasThumbnails;
import com.yongsoo.youtubeatlasbackend.youtube.model.AtlasVideo;

@Service
public class TrendingService {

    private static final String TRENDING_CATEGORY_ID = CategoryCatalog.ALL_VIDEO_CATEGORY_ID;
    private static final String TRENDING_CATEGORY_LABEL = "전체";

    private final AtlasProperties atlasProperties;
    private final YouTubeCatalogService youTubeCatalogService;
    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;
    private final TrendSignalRepository trendSignalRepository;

    public TrendingService(
        AtlasProperties atlasProperties,
        YouTubeCatalogService youTubeCatalogService,
        TrendRunRepository trendRunRepository,
        TrendSnapshotRepository trendSnapshotRepository,
        TrendSignalRepository trendSignalRepository
    ) {
        this.atlasProperties = atlasProperties;
        this.youTubeCatalogService = youTubeCatalogService;
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
        this.trendSignalRepository = trendSignalRepository;
    }

    @Transactional(readOnly = true)
    public List<TrendSignalResponse> getSignals(String regionCode, String categoryId, List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) {
            return List.of();
        }

        return trendSignalRepository.findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn(
            regionCode.trim().toUpperCase(),
            TRENDING_CATEGORY_ID,
            videoIds
        ).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public RealtimeSurgingResponse getRealtimeSurging(String regionCode) {
        String normalizedRegionCode = regionCode.trim().toUpperCase();
        int rankChangeThreshold = atlasProperties.getTrending().getRealtimeSurgingRankChangeThreshold();
        List<TrendSignalResponse> items = trendSignalRepository
            .findByIdRegionCodeAndIdCategoryIdAndRankChangeGreaterThanEqualOrderByRankChangeDescCurrentRankAsc(
                normalizedRegionCode,
                TRENDING_CATEGORY_ID,
                rankChangeThreshold
            )
            .stream()
            .map(this::toResponse)
            .toList();

        Instant capturedAt = resolveLatestCapturedAt(normalizedRegionCode, items);

        return new RealtimeSurgingResponse(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID,
            TRENDING_CATEGORY_LABEL,
            rankChangeThreshold,
            items.size(),
            capturedAt,
            items
        );
    }

    @Transactional(readOnly = true)
    public NewChartEntriesResponse getNewChartEntries(String regionCode) {
        String normalizedRegionCode = regionCode.trim().toUpperCase();
        List<TrendSignalResponse> items = trendSignalRepository.findNewEntriesByRegionCodeAndCategoryId(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID
        ).stream()
            .map(this::toResponse)
            .toList();

        return new NewChartEntriesResponse(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID,
            TRENDING_CATEGORY_LABEL,
            items.size(),
            resolveLatestCapturedAt(normalizedRegionCode, items),
            items
        );
    }

    @Transactional(readOnly = true)
    public VideoRankHistoryResponse getVideoHistory(String regionCode, String videoId) {
        String normalizedRegionCode = regionCode.trim().toUpperCase();
        String normalizedVideoId = normalizeVideoId(videoId);
        List<TrendSnapshot> snapshots = trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID,
            normalizedVideoId
        );

        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("해당 영상의 랭킹 기록을 찾을 수 없습니다.");
        }

        TrendRun latestRun = trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID
        ).orElse(null);
        TrendSnapshot firstSnapshot = snapshots.getFirst();
        TrendSnapshot lastSnapshot = snapshots.getLast();
        long endRunId = latestRun != null ? latestRun.getId() : lastSnapshot.getRun().getId();
        List<TrendRun> runs = trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID,
            firstSnapshot.getRun().getId(),
            endRunId
        );
        Map<Long, TrendSnapshot> snapshotsByRunId = snapshots.stream()
            .collect(Collectors.toMap(snapshot -> snapshot.getRun().getId(), Function.identity()));
        TrendSignal currentSignal = trendSignalRepository.findById(
            new TrendSignalId(normalizedRegionCode, TRENDING_CATEGORY_ID, normalizedVideoId)
        ).orElse(null);

        List<VideoRankHistoryPointResponse> points = runs.stream()
            .map(run -> {
                TrendSnapshot snapshot = snapshotsByRunId.get(run.getId());

                return new VideoRankHistoryPointResponse(
                    run.getId(),
                    run.getCapturedAt(),
                    snapshot != null ? snapshot.getRank() : null,
                    snapshot != null ? snapshot.getViewCount() : null,
                    snapshot == null
                );
            })
            .toList();

        Instant latestCapturedAt = currentSignal != null
            ? currentSignal.getCapturedAt()
            : latestRun != null
                ? latestRun.getCapturedAt()
                : lastSnapshot.getRun().getCapturedAt();
        Integer latestRank = currentSignal != null ? currentSignal.getCurrentRank() : null;
        boolean latestChartOut = currentSignal == null;

        if (currentSignal == null && latestRun == null) {
            latestRank = lastSnapshot.getRank();
            latestChartOut = false;
        }

        TrendSnapshot displaySnapshot = currentSignal == null
            ? snapshots.stream().max(Comparator.comparing(snapshot -> snapshot.getRun().getId())).orElse(lastSnapshot)
            : null;

        return new VideoRankHistoryResponse(
            normalizedRegionCode,
            TRENDING_CATEGORY_ID,
            TRENDING_CATEGORY_LABEL,
            normalizedVideoId,
            currentSignal != null ? currentSignal.getTitle() : displaySnapshot.getTitle(),
            currentSignal != null ? currentSignal.getChannelTitle() : displaySnapshot.getChannelTitle(),
            currentSignal != null ? currentSignal.getThumbnailUrl() : displaySnapshot.getThumbnailUrl(),
            latestRank,
            latestChartOut,
            latestCapturedAt,
            points
        );
    }

    @Transactional
    public SyncTrendingResponse sync(SyncTrendingRequest request) {
        String regionCode = request.regionCode().trim().toUpperCase();
        String categoryId = TRENDING_CATEGORY_ID;
        String categoryLabel = TRENDING_CATEGORY_LABEL;
        List<String> sourceCategoryIds = List.of();
        Instant now = Instant.now();

        TrendRun currentRun = new TrendRun();
        currentRun.setRegionCode(regionCode);
        currentRun.setCategoryId(categoryId);
        currentRun.setCategoryLabel(categoryLabel);
        currentRun.setSourceCategoryIds(sourceCategoryIds);
        currentRun.setSource("youtube-mostPopular");
        currentRun.setCapturedAt(now);
        currentRun = trendRunRepository.save(currentRun);

        List<AtlasVideo> videos = youTubeCatalogService.fetchMergedCategoryVideos(
            regionCode,
            sourceCategoryIds,
            atlasProperties.getTrending().getSyncMaxPagesPerSource()
        );
        List<TrendSnapshot> snapshots = new ArrayList<>();

        for (int index = 0; index < videos.size(); index++) {
            AtlasVideo video = videos.get(index);
            TrendSnapshot snapshot = new TrendSnapshot();
            snapshot.setRun(currentRun);
            snapshot.setRegionCode(regionCode);
            snapshot.setCategoryId(categoryId);
            snapshot.setVideoId(video.id());
            snapshot.setRank(index + 1);
            snapshot.setTitle(video.snippet() != null ? video.snippet().title() : "");
            snapshot.setChannelTitle(video.snippet() != null ? video.snippet().channelTitle() : "");
            snapshot.setChannelId(video.snippet() != null ? video.snippet().channelId() : null);
            snapshot.setThumbnailUrl(resolveThumbnailUrl(video.snippet() != null ? video.snippet().thumbnails() : null));
            snapshot.setViewCount(video.statistics() != null ? video.statistics().viewCount() : null);
            snapshot.setPublishedAt(
                video.snippet() != null && video.snippet().publishedAt() != null
                    ? video.snippet().publishedAt().toInstant()
                    : null
            );
            snapshot.setCreatedAt(now);
            snapshots.add(snapshot);
        }

        trendSnapshotRepository.saveAll(snapshots);
        TrendRun previousRun = trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(
            regionCode,
            categoryId,
            currentRun.getId()
        ).orElse(null);
        List<TrendSnapshot> previousSnapshots = previousRun != null
            ? trendSnapshotRepository.findByRunId(previousRun.getId())
            : List.of();

        List<TrendSignal> existingSignals = trendSignalRepository.findByIdRegionCodeAndIdCategoryId(regionCode, categoryId);
        Set<String> currentVideoIds = snapshots.stream().map(TrendSnapshot::getVideoId).collect(LinkedHashSet::new, Set::add, Set::addAll);

        for (TrendSignal existingSignal : existingSignals) {
            if (!currentVideoIds.contains(existingSignal.getId().getVideoId())) {
                trendSignalRepository.delete(existingSignal);
            }
        }

        for (TrendSnapshot snapshot : snapshots) {
            TrendSnapshot previousSnapshot = previousSnapshots.stream()
                .filter(item -> item.getVideoId().equals(snapshot.getVideoId()))
                .findFirst()
                .orElse(null);

            TrendSignal signal = trendSignalRepository.findById(new TrendSignalId(regionCode, categoryId, snapshot.getVideoId()))
                .orElseGet(TrendSignal::new);

            signal.setId(new TrendSignalId(regionCode, categoryId, snapshot.getVideoId()));
            signal.setCategoryLabel(categoryLabel);
            signal.setCurrentRunId(currentRun.getId());
            signal.setPreviousRunId(previousRun != null ? previousRun.getId() : null);
            signal.setCurrentRank(snapshot.getRank());
            signal.setPreviousRank(previousSnapshot != null ? previousSnapshot.getRank() : null);
            signal.setRankChange(previousSnapshot != null ? previousSnapshot.getRank() - snapshot.getRank() : null);
            signal.setCurrentViewCount(snapshot.getViewCount());
            signal.setPreviousViewCount(previousSnapshot != null ? previousSnapshot.getViewCount() : null);
            signal.setViewCountDelta(
                previousSnapshot != null && previousSnapshot.getViewCount() != null && snapshot.getViewCount() != null
                    ? snapshot.getViewCount() - previousSnapshot.getViewCount()
                    : null
            );
            signal.setNew(previousSnapshot == null);
            signal.setTitle(snapshot.getTitle());
            signal.setChannelTitle(snapshot.getChannelTitle());
            signal.setChannelId(snapshot.getChannelId());
            signal.setThumbnailUrl(snapshot.getThumbnailUrl());
            signal.setCapturedAt(currentRun.getCapturedAt());
            signal.setUpdatedAt(now);
            trendSignalRepository.save(signal);
        }

        return new SyncTrendingResponse(
            regionCode,
            categoryId,
            snapshots.size(),
            snapshots.size(),
            currentRun.getCapturedAt()
        );
    }

    @Scheduled(cron = "${atlas.trending.cron}")
    public void syncScheduledJobs() {
        if (!atlasProperties.getTrending().isSchedulerEnabled()) {
            return;
        }

        Set<String> scheduledRegions = new LinkedHashSet<>();

        for (AtlasProperties.SyncJob job : atlasProperties.getTrending().getJobs()) {
            if (StringUtils.hasText(job.getRegionCode())) {
                scheduledRegions.add(job.getRegionCode().trim().toUpperCase());
            }
        }

        for (String regionCode : scheduledRegions) {
            syncAllCategory(regionCode);
        }
    }

    private TrendSignalResponse toResponse(TrendSignal signal) {
        return new TrendSignalResponse(
            signal.getId().getRegionCode(),
            signal.getId().getCategoryId(),
            signal.getCategoryLabel(),
            signal.getId().getVideoId(),
            signal.getCurrentRank(),
            signal.getPreviousRank(),
            signal.getRankChange(),
            signal.getCurrentViewCount(),
            signal.getPreviousViewCount(),
            signal.getViewCountDelta(),
            signal.isNew(),
            signal.getTitle(),
            signal.getChannelTitle(),
            signal.getChannelId(),
            signal.getThumbnailUrl(),
            signal.getCapturedAt()
        );
    }

    private Instant resolveLatestCapturedAt(String regionCode, List<TrendSignalResponse> items) {
        return items.stream()
            .map(TrendSignalResponse::capturedAt)
            .findFirst()
            .orElseGet(() -> trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc(
                regionCode,
                TRENDING_CATEGORY_ID
            ).map(TrendRun::getCapturedAt).orElse(null));
    }

    public SyncTrendingResponse syncAllCategory(String regionCode) {
        return sync(new SyncTrendingRequest(
            regionCode,
            TRENDING_CATEGORY_ID,
            TRENDING_CATEGORY_LABEL,
            List.of()
        ));
    }

    private String resolveThumbnailUrl(AtlasThumbnails thumbnails) {
        if (thumbnails == null) {
            return "";
        }

        AtlasThumbnail thumbnail = thumbnails.maxres() != null
            ? thumbnails.maxres()
            : thumbnails.standard() != null
                ? thumbnails.standard()
                : thumbnails.high() != null
                    ? thumbnails.high()
                    : thumbnails.medium() != null
                        ? thumbnails.medium()
                        : thumbnails.defaultThumbnail();

        return thumbnail != null ? thumbnail.url() : "";
    }

    private String normalizeVideoId(String videoId) {
        if (!StringUtils.hasText(videoId)) {
            throw new IllegalArgumentException("videoId는 비어 있을 수 없습니다.");
        }

        return videoId.trim();
    }
}
