package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminTradeHistoryCleanupRequest;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminTradeHistoryCleanupResponse;
import com.yongsoo.youtubeatlasbackend.game.GameDividendPayoutRepository;
import com.yongsoo.youtubeatlasbackend.game.GameLedgerRepository;
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.PositionStatus;

@Service
public class AdminTradeHistoryService {

    private final GamePositionRepository gamePositionRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final GameDividendPayoutRepository gameDividendPayoutRepository;
    private final Clock clock;

    public AdminTradeHistoryService(
        GamePositionRepository gamePositionRepository,
        GameLedgerRepository gameLedgerRepository,
        GameDividendPayoutRepository gameDividendPayoutRepository,
        Clock clock
    ) {
        this.gamePositionRepository = gamePositionRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameDividendPayoutRepository = gameDividendPayoutRepository;
        this.clock = clock;
    }

    @Transactional
    public AdminTradeHistoryCleanupResponse deleteClosedTradeHistoryOlderThan(AdminTradeHistoryCleanupRequest request) {
        Instant deleteBefore = request.deleteBefore();
        Instant now = Instant.now(clock);

        if (deleteBefore.isAfter(now)) {
            throw new IllegalArgumentException("deleteBefore는 현재 시각 이전이어야 합니다.");
        }

        List<Long> positionIds = gamePositionRepository.findCleanupTargets(
            List.of(PositionStatus.CLOSED, PositionStatus.AUTO_CLOSED),
            deleteBefore
        ).stream()
            .map(position -> position.getId())
            .toList();

        if (positionIds.isEmpty()) {
            return new AdminTradeHistoryCleanupResponse(deleteBefore, now, 0L, 0L, 0L);
        }

        long deletedLedgerCount = gameLedgerRepository.deleteByPositionIds(positionIds);
        long deletedDividendPayoutCount = gameDividendPayoutRepository.deleteByPositionIds(positionIds);
        long deletedPositionCount = gamePositionRepository.deleteByIds(positionIds);

        return new AdminTradeHistoryCleanupResponse(
            deleteBefore,
            now,
            deletedPositionCount,
            deletedLedgerCount,
            deletedDividendPayoutCount
        );
    }
}
