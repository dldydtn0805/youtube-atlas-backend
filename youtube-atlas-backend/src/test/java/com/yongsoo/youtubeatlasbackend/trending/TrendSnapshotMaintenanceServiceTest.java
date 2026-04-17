package com.yongsoo.youtubeatlasbackend.trending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TrendSnapshotMaintenanceServiceTest {

    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private TrendSnapshotMaintenanceService trendSnapshotMaintenanceService;

    @BeforeEach
    void setUp() {
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        trendSnapshotMaintenanceService = new TrendSnapshotMaintenanceService(
            trendRunRepository,
            trendSnapshotRepository
        );
    }

    @Test
    void sanitizeRunSnapshotsRemovesDuplicateVideosAndCompactsRanks() {
        TrendRun run = trendRun(11L, Instant.parse("2026-04-10T09:00:00Z"));
        TrendSnapshot first = snapshot(101L, run, "video-1", 1);
        TrendSnapshot duplicate = snapshot(102L, run, "video-1", 2);
        TrendSnapshot second = snapshot(103L, run, "video-2", 3);

        when(trendSnapshotRepository.findByRunIdOrderByRankAsc(11L)).thenReturn(List.of(first, duplicate, second));

        List<TrendSnapshot> sanitizedSnapshots = trendSnapshotMaintenanceService.sanitizeRunSnapshots(11L);

        assertThat(sanitizedSnapshots).extracting(TrendSnapshot::getVideoId).containsExactly("video-1", "video-2");
        assertThat(sanitizedSnapshots).extracting(TrendSnapshot::getRank).containsExactly(1, 2);
        verify(trendSnapshotRepository).deleteAllByIdInBatch(List.of(102L));
    }

    private TrendRun trendRun(Long id, Instant capturedAt) {
        TrendRun run = new TrendRun();
        ReflectionTestUtils.setField(run, "id", id);
        run.setRegionCode("KR");
        run.setCategoryId("0");
        run.setCategoryLabel("전체");
        run.setSourceCategoryIds(List.of());
        run.setSource("youtube-mostPopular");
        run.setCapturedAt(capturedAt);
        return run;
    }

    private TrendSnapshot snapshot(Long id, TrendRun run, String videoId, int rank) {
        TrendSnapshot snapshot = new TrendSnapshot();
        ReflectionTestUtils.setField(snapshot, "id", id);
        snapshot.setRun(run);
        snapshot.setRegionCode("KR");
        snapshot.setCategoryId("0");
        snapshot.setVideoCategoryId("10");
        snapshot.setVideoCategoryLabel("음악");
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
