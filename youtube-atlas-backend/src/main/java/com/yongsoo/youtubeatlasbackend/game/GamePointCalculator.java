package com.yongsoo.youtubeatlasbackend.game;

final class GamePointCalculator {

    static final int QUANTITY_SCALE = 100;
    static final int MIN_QUANTITY = 1;
    static final int ORDER_QUANTITY_STEP = QUANTITY_SCALE;
    static final int MAX_TRACKED_RANK = 200;
    private static final long CHART_OUT_DELISTING_PENALTY_POINTS = 100L;
    private static final long SELL_FEE_NUMERATOR = 3L;
    private static final long SELL_FEE_DENOMINATOR = 1_000L;
    private static final int MAX_MOMENTUM_RANK_CHANGE = 30;
    private static final double UPWARD_MOMENTUM_COEFFICIENT = 0.002D;
    private static final double DOWNWARD_MOMENTUM_COEFFICIENT = 0.003D;

    private static final PriceAnchor[] PRICE_ANCHORS = {
        new PriceAnchor(1, 2_000_000L),
        new PriceAnchor(2, 1_333_333L),
        new PriceAnchor(3, 1_320_000L),
        new PriceAnchor(4, 1_300_000L),
        new PriceAnchor(5, 1_270_000L),
        new PriceAnchor(6, 1_240_000L),
        new PriceAnchor(7, 1_200_000L),
        new PriceAnchor(8, 1_150_000L),
        new PriceAnchor(9, 1_100_000L),
        new PriceAnchor(10, 1_050_000L),
        new PriceAnchor(20, 750_000L),
        new PriceAnchor(30, 550_000L),
        new PriceAnchor(40, 400_000L),
        new PriceAnchor(50, 290_000L),
        new PriceAnchor(60, 210_000L),
        new PriceAnchor(70, 150_000L),
        new PriceAnchor(80, 110_000L),
        new PriceAnchor(90, 80_000L),
        new PriceAnchor(100, 58_000L),
        new PriceAnchor(110, 42_000L),
        new PriceAnchor(120, 31_000L),
        new PriceAnchor(130, 23_000L),
        new PriceAnchor(140, 17_000L),
        new PriceAnchor(150, 13_000L),
        new PriceAnchor(160, 10_000L),
        new PriceAnchor(170, 7_500L),
        new PriceAnchor(180, 5_500L),
        new PriceAnchor(190, 4_000L),
        new PriceAnchor(200, 3_000L)
    };

    private GamePointCalculator() {
    }

    static long calculatePricePoints(int rank) {
        if (rank > PRICE_ANCHORS[PRICE_ANCHORS.length - 1].rank()) {
            return 0L;
        }

        int normalizedRank = Math.max(1, rank);
        PriceAnchor previousAnchor = PRICE_ANCHORS[0];

        for (PriceAnchor anchor : PRICE_ANCHORS) {
            if (anchor.rank() == normalizedRank) {
                return anchor.pricePoints();
            }

            if (anchor.rank() > normalizedRank) {
                return interpolateGeometrically(previousAnchor, anchor, normalizedRank);
            }

            previousAnchor = anchor;
        }

        return PRICE_ANCHORS[PRICE_ANCHORS.length - 1].pricePoints();
    }

    static long calculateMomentumAdjustedPricePoints(int rank, Integer rankChange) {
        long basePricePoints = calculatePricePoints(rank);
        if (basePricePoints <= 0L || rankChange == null || rankChange == 0) {
            return basePricePoints;
        }

        int cappedRankChange = Math.max(
            -MAX_MOMENTUM_RANK_CHANGE,
            Math.min(MAX_MOMENTUM_RANK_CHANGE, rankChange)
        );
        double coefficient = cappedRankChange > 0
            ? UPWARD_MOMENTUM_COEFFICIENT
            : DOWNWARD_MOMENTUM_COEFFICIENT;
        return Math.max(0L, Math.round(basePricePoints * Math.exp(coefficient * cappedRankChange)));
    }

    static long calculateChartOutUnitPricePoints() {
        return Math.max(0L, calculatePricePoints(MAX_TRACKED_RANK) - CHART_OUT_DELISTING_PENALTY_POINTS);
    }

    static long calculateProfitPoints(long buyPricePoints, long currentPricePoints) {
        return currentPricePoints - buyPricePoints;
    }

    static long calculatePositionPoints(long unitPricePoints, int quantity) {
        if (unitPricePoints <= 0L || quantity <= 0) {
            return 0L;
        }

        long scaledPoints = Math.multiplyExact(unitPricePoints, quantity);
        return Math.floorDiv(scaledPoints + (QUANTITY_SCALE / 2L), QUANTITY_SCALE);
    }

    static long estimateUnitPricePoints(long totalPricePoints, int quantity) {
        if (totalPricePoints <= 0L || quantity <= 0) {
            return 0L;
        }

        return Math.round((double) totalPricePoints * QUANTITY_SCALE / quantity);
    }

    static long calculateSettledPoints(long currentPricePoints) {
        return Math.max(0L, currentPricePoints - calculateSellFeePoints(currentPricePoints));
    }

    static long calculateSellFeePoints(long currentPricePoints) {
        if (currentPricePoints <= 0L) {
            return 0L;
        }

        return (currentPricePoints * SELL_FEE_NUMERATOR) / SELL_FEE_DENOMINATOR;
    }

    static int estimateRankForPricePoints(long pricePoints) {
        if (pricePoints <= 0L) {
            return 201;
        }

        int closestRank = 1;
        long closestDifference = Long.MAX_VALUE;

        for (int rank = 1; rank <= 200; rank++) {
            long candidatePricePoints = calculatePricePoints(rank);
            long difference = Math.abs(candidatePricePoints - pricePoints);
            if (difference < closestDifference) {
                closestDifference = difference;
                closestRank = rank;
            }
        }

        return closestRank;
    }

    private static long interpolateGeometrically(PriceAnchor betterAnchor, PriceAnchor worseAnchor, int rank) {
        if (betterAnchor.rank() == worseAnchor.rank()) {
            return betterAnchor.pricePoints();
        }

        double progress = (double) (rank - betterAnchor.rank()) / (worseAnchor.rank() - betterAnchor.rank());
        double interpolated = betterAnchor.pricePoints()
            * Math.pow((double) worseAnchor.pricePoints() / betterAnchor.pricePoints(), progress);
        return Math.round(interpolated);
    }

    private record PriceAnchor(int rank, long pricePoints) {
    }
}
