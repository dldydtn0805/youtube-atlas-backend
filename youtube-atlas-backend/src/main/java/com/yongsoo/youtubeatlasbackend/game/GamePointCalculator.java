package com.yongsoo.youtubeatlasbackend.game;

final class GamePointCalculator {

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

    static long calculateProfitPoints(long buyPricePoints, long currentPricePoints) {
        return currentPricePoints - buyPricePoints;
    }

    static long calculateSettledPoints(long currentPricePoints) {
        return Math.max(0L, currentPricePoints);
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
