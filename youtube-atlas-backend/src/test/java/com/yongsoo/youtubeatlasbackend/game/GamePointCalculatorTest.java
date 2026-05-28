package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GamePointCalculatorTest {

    @Test
    void calculatePositionPointsHandlesLargeQuantityWhenFinalValueFitsLong() {
        long result = GamePointCalculator.calculatePositionPoints(836_000L, 11_812_390_915_200L);

        assertThat(result).isEqualTo(98_751_588_051_072_000L);
    }

    @Test
    void calculateSellFeePointsHandlesLargeCurrentPriceWhenFinalValueFitsLong() {
        long result = GamePointCalculator.calculateSellFeePoints(4_000_000_000_000_000_000L);

        assertThat(result).isEqualTo(12_000_000_000_000_000L);
    }

    @Test
    void estimatePreFeePointsFromSettledPointsRestoresThresholdBoundary() {
        long settledPoints = GamePointCalculator.calculateSettledPoints(4_000L);

        assertThat(GamePointCalculator.estimatePreFeePointsFromSettledPoints(settledPoints)).isEqualTo(4_000L);
    }

    @Test
    void calculateMomentumAdjustedPricePointsUsesTenPercentPerRankChange() {
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, 1)).isEqualTo(7_998L);
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, -1)).isEqualTo(6_544L);
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, 10)).isEqualTo(14_542L);
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, -10)).isZero();
    }

    @Test
    void calculateMomentumAdjustedPricePointsDoesNotCapSurgingPremium() {
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, 50)).isEqualTo(43_626L);
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, 100)).isEqualTo(79_981L);
    }

    @Test
    void calculateMomentumAdjustedPricePointsFloorsExtremeDiscountAtZero() {
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, -100)).isZero();
        assertThat(GamePointCalculator.calculateMomentumAdjustedPricePoints(171, -150)).isZero();
    }

    @Test
    void calculateChartOutUnitPricePointsReturnsZero() {
        assertThat(GamePointCalculator.calculateChartOutUnitPricePoints()).isZero();
    }
}
