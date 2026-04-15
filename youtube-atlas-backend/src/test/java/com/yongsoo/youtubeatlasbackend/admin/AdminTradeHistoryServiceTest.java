package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminTradeHistoryCleanupRequest;
import com.yongsoo.youtubeatlasbackend.game.GameCoinPayoutRepository;
import com.yongsoo.youtubeatlasbackend.game.GameDividendPayoutRepository;
import com.yongsoo.youtubeatlasbackend.game.GameLedgerRepository;
import com.yongsoo.youtubeatlasbackend.game.GamePosition;
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.PositionStatus;

class AdminTradeHistoryServiceTest {

    private GamePositionRepository gamePositionRepository;
    private GameLedgerRepository gameLedgerRepository;
    private GameCoinPayoutRepository gameCoinPayoutRepository;
    private GameDividendPayoutRepository gameDividendPayoutRepository;
    private AdminTradeHistoryService adminTradeHistoryService;

    @BeforeEach
    void setUp() {
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameLedgerRepository = org.mockito.Mockito.mock(GameLedgerRepository.class);
        gameCoinPayoutRepository = org.mockito.Mockito.mock(GameCoinPayoutRepository.class);
        gameDividendPayoutRepository = org.mockito.Mockito.mock(GameDividendPayoutRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-15T03:00:00Z"), ZoneOffset.UTC);
        adminTradeHistoryService = new AdminTradeHistoryService(
            gamePositionRepository,
            gameLedgerRepository,
            gameCoinPayoutRepository,
            gameDividendPayoutRepository,
            clock
        );
    }

    @Test
    void deleteClosedTradeHistoryOlderThanPurgesClosedPositionsAndDependents() {
        Instant deleteBefore = Instant.parse("2026-03-01T00:00:00Z");
        GamePosition first = closedPosition(101L, "2026-02-01T00:00:00Z", PositionStatus.CLOSED);
        GamePosition second = closedPosition(102L, "2026-02-10T00:00:00Z", PositionStatus.AUTO_CLOSED);

        when(gamePositionRepository.findCleanupTargets(
            List.of(PositionStatus.CLOSED, PositionStatus.AUTO_CLOSED),
            deleteBefore
        )).thenReturn(List.of(first, second));
        when(gameLedgerRepository.deleteByPositionIds(List.of(101L, 102L))).thenReturn(4L);
        when(gameCoinPayoutRepository.deleteByPositionIds(List.of(101L, 102L))).thenReturn(6L);
        when(gameDividendPayoutRepository.deleteByPositionIds(List.of(101L, 102L))).thenReturn(2L);
        when(gamePositionRepository.deleteByIds(List.of(101L, 102L))).thenReturn(2L);

        var response = adminTradeHistoryService.deleteClosedTradeHistoryOlderThan(
            new AdminTradeHistoryCleanupRequest(deleteBefore)
        );

        assertThat(response.deleteBefore()).isEqualTo(deleteBefore);
        assertThat(response.deletedAt()).isEqualTo(Instant.parse("2026-04-15T03:00:00Z"));
        assertThat(response.deletedPositionCount()).isEqualTo(2L);
        assertThat(response.deletedLedgerCount()).isEqualTo(4L);
        assertThat(response.deletedCoinPayoutCount()).isEqualTo(6L);
        assertThat(response.deletedDividendPayoutCount()).isEqualTo(2L);
        verify(gameLedgerRepository).deleteByPositionIds(List.of(101L, 102L));
        verify(gameCoinPayoutRepository).deleteByPositionIds(List.of(101L, 102L));
        verify(gameDividendPayoutRepository).deleteByPositionIds(List.of(101L, 102L));
        verify(gamePositionRepository).deleteByIds(List.of(101L, 102L));
    }

    @Test
    void deleteClosedTradeHistoryOlderThanReturnsZeroWhenNothingMatches() {
        Instant deleteBefore = Instant.parse("2026-03-01T00:00:00Z");
        when(gamePositionRepository.findCleanupTargets(
            List.of(PositionStatus.CLOSED, PositionStatus.AUTO_CLOSED),
            deleteBefore
        )).thenReturn(List.of());

        var response = adminTradeHistoryService.deleteClosedTradeHistoryOlderThan(
            new AdminTradeHistoryCleanupRequest(deleteBefore)
        );

        assertThat(response.deletedPositionCount()).isZero();
        assertThat(response.deletedLedgerCount()).isZero();
        assertThat(response.deletedCoinPayoutCount()).isZero();
        assertThat(response.deletedDividendPayoutCount()).isZero();
    }

    @Test
    void deleteClosedTradeHistoryOlderThanRejectsFutureTimestamp() {
        Instant future = Instant.parse("2026-04-16T00:00:00Z");

        assertThatThrownBy(() -> adminTradeHistoryService.deleteClosedTradeHistoryOlderThan(
            new AdminTradeHistoryCleanupRequest(future)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("deleteBefore");
    }

    private GamePosition closedPosition(Long id, String closedAt, PositionStatus status) {
        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", id);
        position.setStatus(status);
        position.setClosedAt(Instant.parse(closedAt));
        return position;
    }
}
