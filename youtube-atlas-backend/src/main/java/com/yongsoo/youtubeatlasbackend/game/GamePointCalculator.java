package com.yongsoo.youtubeatlasbackend.game;

final class GamePointCalculator {

    private static final long STAKE_UNIT_POINTS = 1_000L;

    private GamePointCalculator() {
    }

    static long calculateProfitPoints(long stakePoints, int rankPointMultiplier, int rankDiff) {
        long units = stakePoints / STAKE_UNIT_POINTS;
        return (long) rankDiff * rankPointMultiplier * units;
    }

    static long calculateSettledPoints(long stakePoints, long profitPoints) {
        return Math.max(0L, stakePoints + profitPoints);
    }
}
