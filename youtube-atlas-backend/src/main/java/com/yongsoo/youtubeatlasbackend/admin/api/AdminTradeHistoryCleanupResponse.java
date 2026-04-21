package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;

public record AdminTradeHistoryCleanupResponse(
    Instant deleteBefore,
    Instant deletedAt,
    long deletedPositionCount,
    long deletedLedgerCount,
    long deletedDividendPayoutCount
) {
}
