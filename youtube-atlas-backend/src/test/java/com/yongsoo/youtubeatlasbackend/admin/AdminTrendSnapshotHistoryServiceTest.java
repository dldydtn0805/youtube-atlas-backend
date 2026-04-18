package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminTrendSnapshotHistoryResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

class AdminTrendSnapshotHistoryServiceTest {

    private TrendSnapshotRepository trendSnapshotRepository;
    private AdminTrendSnapshotHistoryService adminTrendSnapshotHistoryService;

    @BeforeEach
    void setUp() {
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        adminTrendSnapshotHistoryService = new AdminTrendSnapshotHistoryService(trendSnapshotRepository);
    }

    @Test
    void getSnapshotsBySavedAtRangeReturnsFlattenedHistory() {
        Instant startAt = Instant.parse("2026-04-10T00:00:00Z");
        Instant endAt = Instant.parse("2026-04-10T23:59:59Z");

        TrendRun run = new TrendRun();
        ReflectionTestUtils.setField(run, "id", 77L);
        run.setRegionCode("KR");
        run.setCategoryId("music");
        run.setCategoryLabel("Music");
        run.setSource("youtube");
        run.setCapturedAt(Instant.parse("2026-04-10T12:00:00Z"));

        TrendSnapshot snapshot = new TrendSnapshot();
        ReflectionTestUtils.setField(snapshot, "id", 88L);
        snapshot.setRun(run);
        snapshot.setRank(1);
        snapshot.setVideoId("video-1");
        snapshot.setTitle("Top video");
        snapshot.setChannelTitle("Atlas TV");
        snapshot.setThumbnailUrl("https://example.com/thumb.jpg");
        snapshot.setViewCount(12345L);
        snapshot.setVideoCategoryId("10");
        snapshot.setVideoCategoryLabel("Music");
        snapshot.setCreatedAt(Instant.parse("2026-04-10T12:00:05Z"));

        when(trendSnapshotRepository.findByCreatedAtBetweenAndRegionCodeOrderByCreatedAtDesc(startAt, endAt, "KR"))
            .thenReturn(List.of(snapshot));

        AdminTrendSnapshotHistoryResponse response =
            adminTrendSnapshotHistoryService.getSnapshotsBySavedAtRange(startAt, endAt, "kr");

        assertThat(response.count()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().runId()).isEqualTo(77L);
        assertThat(response.items().getFirst().savedAt()).isEqualTo(Instant.parse("2026-04-10T12:00:05Z"));
        assertThat(response.items().getFirst().capturedAt()).isEqualTo(Instant.parse("2026-04-10T12:00:00Z"));
    }

    @Test
    void getSnapshotsBySavedAtRangeRejectsBlankRegion() {
        Instant startAt = Instant.parse("2026-04-10T00:00:00Z");
        Instant endAt = Instant.parse("2026-04-10T23:59:59Z");

        assertThatThrownBy(() -> adminTrendSnapshotHistoryService.getSnapshotsBySavedAtRange(startAt, endAt, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("국가를 선택하세요.");
    }

    @Test
    void getSnapshotsBySavedAtRangeRejectsInvalidRange() {
        Instant startAt = Instant.parse("2026-04-11T00:00:00Z");
        Instant endAt = Instant.parse("2026-04-10T00:00:00Z");

        assertThatThrownBy(() -> adminTrendSnapshotHistoryService.getSnapshotsBySavedAtRange(startAt, endAt, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("조회 시작 시각");
    }
}
