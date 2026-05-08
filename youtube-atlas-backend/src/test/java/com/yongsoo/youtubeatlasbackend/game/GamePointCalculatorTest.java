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
}
