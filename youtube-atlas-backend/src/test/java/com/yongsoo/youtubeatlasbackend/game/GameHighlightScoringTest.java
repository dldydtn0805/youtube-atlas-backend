package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;

class GameHighlightScoringTest {

    @Test
    void sumsEachMatchedStrategyScoreIntoTotalHighlightScore() {
        GameHighlightResponse highlight = highlight(List.of(
            GameStrategyType.MOONSHOT,
            GameStrategyType.BIG_CASHOUT,
            GameStrategyType.SNIPE
        ));

        long expected = GameService.calculateStrategyHighlightScore(GameStrategyType.MOONSHOT, highlight)
            + GameService.calculateStrategyHighlightScore(GameStrategyType.BIG_CASHOUT, highlight)
            + GameService.calculateStrategyHighlightScore(GameStrategyType.SNIPE, highlight);

        assertThat(GameService.calculateHighlightScore(highlight)).isEqualTo(expected);
    }

    @Test
    void keepsSingleTagScoreEquivalentToThatStrategyOnly() {
        GameHighlightResponse highlight = highlight(List.of(GameStrategyType.MOONSHOT));

        assertThat(GameService.calculateHighlightScore(highlight))
            .isEqualTo(GameService.calculateStrategyHighlightScore(GameStrategyType.MOONSHOT, highlight));
    }

    @Test
    void appliesRaisedMoonshotBaseScore() {
        GameHighlightResponse highlight = highlight(List.of(GameStrategyType.MOONSHOT));

        assertThat(GameService.calculateStrategyHighlightScore(GameStrategyType.MOONSHOT, highlight))
            .isEqualTo(23_367L);
    }

    @Test
    void usesUnifiedProfitBonusCap() {
        assertThat(GameService.calculateProfitPointsHighlightBonus(5_000L)).isZero();
        assertThat(GameService.calculateProfitPointsHighlightBonus(1_000_000L)).isEqualTo(748L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(100_000_000L)).isEqualTo(7_500L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(400_005_000L)).isEqualTo(15_000L);
    }

    private GameHighlightResponse highlight(List<GameStrategyType> strategyTags) {
        return new GameHighlightResponse(
            "h-1",
            "MOONSHOT",
            "문샷 적중",
            "설명",
            1L,
            "video-1",
            "Video",
            "Channel",
            "https://example.com/thumb.jpg",
            180,
            15,
            15,
            165,
            GamePointCalculator.QUANTITY_SCALE,
            1_000L,
            14_000L,
            13_000L,
            1_300D,
            strategyTags,
            0L,
            "CLOSED",
            Instant.parse("2026-04-01T06:00:00Z")
        );
    }
}
