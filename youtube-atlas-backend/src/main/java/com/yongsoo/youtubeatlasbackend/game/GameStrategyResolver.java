package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class GameStrategyResolver {

    private static final double BIG_CASHOUT_MIN_PROFIT_RATE_PERCENT = 1_000D;
    private static final double POSITION_CASHOUT_MIN_PROFIT_RATE_PERCENT = 18D;
    private static final int POSITION_CASHOUT_MIN_RANK_DIFF = 12;
    private static final int MOONSHOT_BUY_RANK_MIN = 100;
    private static final int MOONSHOT_TARGET_RANK_MAX = 20;
    private static final int SNIPE_BUY_RANK_MIN = 150;
    private static final int SNIPE_TARGET_RANK_MAX = 100;
    private static final double HIGHLIGHT_CASHOUT_MIN_PROFIT_RATE_PERCENT = 300D;

    private GameStrategyResolver() {
    }

    static List<GameStrategyType> resolvePositionStrategyTags(
        GamePosition position,
        Integer currentRank,
        Long currentPricePoints,
        boolean chartOut,
        Instant now
    ) {
        List<GameStrategyType> tags = new ArrayList<>();

        if (matchesMoonshot(position.getBuyRank(), currentRank)) {
            tags.add(GameStrategyType.MOONSHOT);
        }

        if (matchesSnipe(position.getBuyRank(), currentRank)) {
            tags.add(GameStrategyType.SNIPE);
        }

        GameStrategyType cashoutType = resolvePositionCashoutType(position, currentRank, currentPricePoints, chartOut, now);
        if (cashoutType != null) {
            tags.add(cashoutType);
        }

        return List.copyOf(tags);
    }

    static List<GameStrategyType> resolveHighlightStrategyTags(
        GamePosition position,
        Integer highlightRank,
        Double profitRatePercent
    ) {
        List<GameStrategyType> tags = new ArrayList<>();

        if (matchesMoonshot(position.getBuyRank(), highlightRank)) {
            tags.add(GameStrategyType.MOONSHOT);
        }

        GameStrategyType cashoutType = resolveHighlightCashoutType(profitRatePercent);
        if (cashoutType != null) {
            tags.add(cashoutType);
        }

        if (matchesSnipe(position.getBuyRank(), highlightRank)) {
            tags.add(GameStrategyType.SNIPE);
        }

        return List.copyOf(tags);
    }

    static List<GameStrategyType> resolveAchievedPositionStrategyTags(
        GamePosition position,
        Integer currentRank,
        Long currentPricePoints
    ) {
        return resolveHighlightStrategyTags(
            position,
            currentRank,
            calculateProfitRatePercent(position.getStakePoints(), currentPricePoints)
        );
    }

    static List<GameStrategyType> resolveTargetPositionStrategyTags(
        List<GameStrategyType> positionStrategyTags,
        List<GameStrategyType> achievedStrategyTags
    ) {
        List<GameStrategyType> targets = new ArrayList<>(positionStrategyTags);
        targets.removeAll(achievedStrategyTags);

        if (achievedStrategyTags.contains(GameStrategyType.SMALL_CASHOUT)
            && !achievedStrategyTags.contains(GameStrategyType.BIG_CASHOUT)
            && !targets.contains(GameStrategyType.BIG_CASHOUT)) {
            targets.add(0, GameStrategyType.BIG_CASHOUT);
        }

        return List.copyOf(targets);
    }

    private static boolean matchesMoonshot(Integer buyRank, Integer currentRank) {
        return buyRank != null
            && currentRank != null
            && buyRank >= MOONSHOT_BUY_RANK_MIN
            && currentRank <= MOONSHOT_TARGET_RANK_MAX;
    }

    private static boolean matchesSnipe(Integer buyRank, Integer currentRank) {
        return buyRank != null
            && currentRank != null
            && buyRank >= SNIPE_BUY_RANK_MIN
            && currentRank <= SNIPE_TARGET_RANK_MAX;
    }

    private static GameStrategyType resolvePositionCashoutType(
        GamePosition position,
        Integer currentRank,
        Long currentPricePoints,
        boolean chartOut,
        Instant now
    ) {
        if (position.getStatus() != PositionStatus.OPEN || chartOut) {
            return resolveHighlightCashoutType(calculateProfitRatePercent(position.getStakePoints(), currentPricePoints));
        }

        if (!isPastMinimumHold(position, now)) {
            return null;
        }

        Double profitRatePercent = calculateProfitRatePercent(position.getStakePoints(), currentPricePoints);
        int rankDiff = currentRank != null ? position.getBuyRank() - currentRank : 0;

        if ((profitRatePercent != null && profitRatePercent >= POSITION_CASHOUT_MIN_PROFIT_RATE_PERCENT)
            || rankDiff >= POSITION_CASHOUT_MIN_RANK_DIFF) {
            return profitRatePercent != null && profitRatePercent >= BIG_CASHOUT_MIN_PROFIT_RATE_PERCENT
                ? GameStrategyType.BIG_CASHOUT
                : GameStrategyType.SMALL_CASHOUT;
        }

        return null;
    }

    private static GameStrategyType resolveHighlightCashoutType(Double profitRatePercent) {
        if (profitRatePercent == null || profitRatePercent < HIGHLIGHT_CASHOUT_MIN_PROFIT_RATE_PERCENT) {
            return null;
        }

        return profitRatePercent >= BIG_CASHOUT_MIN_PROFIT_RATE_PERCENT
            ? GameStrategyType.BIG_CASHOUT
            : GameStrategyType.SMALL_CASHOUT;
    }

    private static boolean isPastMinimumHold(GamePosition position, Instant now) {
        Instant sellableAt = position.getCreatedAt().plusSeconds(position.getSeason().getMinHoldSeconds());
        return !sellableAt.isAfter(now);
    }

    static Double calculateProfitRatePercent(Long stakePoints, Long currentPricePoints) {
        if (stakePoints == null || stakePoints <= 0L || currentPricePoints == null) {
            return null;
        }

        long profitPoints = currentPricePoints - stakePoints;
        return ((double) profitPoints * 100D) / stakePoints;
    }
}
