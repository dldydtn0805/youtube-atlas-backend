package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminHighlightHistoryCleanupRequest;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminHighlightHistoryCleanupResponse;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightStateRepository;

@Service
public class AdminHighlightHistoryService {

    private final GameHighlightStateRepository gameHighlightStateRepository;
    private final Clock clock;

    public AdminHighlightHistoryService(
        GameHighlightStateRepository gameHighlightStateRepository,
        Clock clock
    ) {
        this.gameHighlightStateRepository = gameHighlightStateRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminHighlightHistoryCleanupResponse deleteHighlightsOlderThan(
        AdminHighlightHistoryCleanupRequest request
    ) {
        Instant deleteBefore = request.deleteBefore();
        Instant now = Instant.now(clock);

        if (deleteBefore.isAfter(now)) {
            throw new IllegalArgumentException("deleteBefore는 현재 시각 이전이어야 합니다.");
        }

        long deletedCount = gameHighlightStateRepository.deleteByBestSettledCreatedAtBefore(deleteBefore);
        return new AdminHighlightHistoryCleanupResponse(deleteBefore, now, deletedCount);
    }
}
