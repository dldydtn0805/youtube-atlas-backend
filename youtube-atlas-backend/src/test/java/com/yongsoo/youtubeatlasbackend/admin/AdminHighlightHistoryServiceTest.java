package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminHighlightHistoryCleanupRequest;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightStateRepository;

class AdminHighlightHistoryServiceTest {

    private GameHighlightStateRepository gameHighlightStateRepository;
    private AdminHighlightHistoryService adminHighlightHistoryService;

    @BeforeEach
    void setUp() {
        gameHighlightStateRepository = org.mockito.Mockito.mock(GameHighlightStateRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-21T03:00:00Z"), ZoneOffset.UTC);
        adminHighlightHistoryService = new AdminHighlightHistoryService(gameHighlightStateRepository, clock);
    }

    @Test
    void deleteHighlightsOlderThanDeletesBySettledCreatedAt() {
        Instant deleteBefore = Instant.parse("2026-04-01T00:00:00Z");
        when(gameHighlightStateRepository.deleteByBestSettledCreatedAtBefore(deleteBefore)).thenReturn(7L);

        var response = adminHighlightHistoryService.deleteHighlightsOlderThan(
            new AdminHighlightHistoryCleanupRequest(deleteBefore, null)
        );

        assertThat(response.deleteBefore()).isEqualTo(deleteBefore);
        assertThat(response.deletedAt()).isEqualTo(Instant.parse("2026-04-21T03:00:00Z"));
        assertThat(response.deletedCount()).isEqualTo(7L);
    }

    @Test
    void deleteHighlightsOlderThanRejectsFutureTime() {
        assertThatThrownBy(() -> adminHighlightHistoryService.deleteHighlightsOlderThan(
            new AdminHighlightHistoryCleanupRequest(Instant.parse("2026-04-22T00:00:00Z"), null)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deleteBefore");
    }

    @Test
    void deleteHighlightsOlderThanCanTargetSingleUser() {
        Instant deleteBefore = Instant.parse("2026-04-01T00:00:00Z");
        Long userId = 55L;
        when(gameHighlightStateRepository.deleteByUserIdAndBestSettledCreatedAtBefore(userId, deleteBefore)).thenReturn(3L);

        var response = adminHighlightHistoryService.deleteHighlightsOlderThan(
            new AdminHighlightHistoryCleanupRequest(deleteBefore, userId)
        );

        assertThat(response.deletedCount()).isEqualTo(3L);
    }
}
