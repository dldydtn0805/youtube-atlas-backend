package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminTrendSnapshotHistoryItemResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminTrendSnapshotHistoryResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

@Service
public class AdminTrendSnapshotHistoryService {

    private final TrendSnapshotRepository trendSnapshotRepository;

    public AdminTrendSnapshotHistoryService(TrendSnapshotRepository trendSnapshotRepository) {
        this.trendSnapshotRepository = trendSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public AdminTrendSnapshotHistoryResponse getSnapshotsBySavedAtRange(Instant startAt, Instant endAt, String regionCode) {
        validateRange(startAt, endAt);
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        validateRegionCode(normalizedRegionCode);

        List<AdminTrendSnapshotHistoryItemResponse> items = trendSnapshotRepository
            .findByCreatedAtBetweenAndRegionCodeOrderByCreatedAtDesc(startAt, endAt, normalizedRegionCode)
            .stream()
            .map(snapshot -> new AdminTrendSnapshotHistoryItemResponse(
                snapshot.getId(),
                snapshot.getRun().getId(),
                snapshot.getRun().getRegionCode(),
                snapshot.getRun().getCategoryId(),
                snapshot.getRun().getCategoryLabel(),
                snapshot.getRun().getSource(),
                snapshot.getRun().getCapturedAt(),
                snapshot.getCreatedAt(),
                snapshot.getRank(),
                snapshot.getVideoId(),
                snapshot.getTitle(),
                snapshot.getChannelTitle(),
                snapshot.getThumbnailUrl(),
                snapshot.getViewCount(),
                snapshot.getVideoCategoryId(),
                snapshot.getVideoCategoryLabel()
            ))
            .toList();

        return new AdminTrendSnapshotHistoryResponse(startAt, endAt, items.size(), items);
    }

    private void validateRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("조회 시작 시각과 종료 시각을 모두 입력해주세요.");
        }

        if (startAt.isAfter(endAt)) {
            throw new IllegalArgumentException("조회 시작 시각은 종료 시각보다 늦을 수 없습니다.");
        }
    }

    private String normalizeRegionCode(String regionCode) {
        if (regionCode == null) {
            return null;
        }

        String normalized = regionCode.trim().toUpperCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateRegionCode(String regionCode) {
        if (regionCode == null) {
            throw new IllegalArgumentException("국가를 선택하세요.");
        }
    }
}
