package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;

class GameStrategyResolverTest {

    @Test
    void resolvesMultipleTagsForStrongOpenPosition() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolvePositionStrategyTags(
            position,
            15,
            130L,
            false,
            Instant.parse("2026-04-01T06:00:00Z")
        )).containsExactly(
            GameStrategyType.SOLAR_SHOT,
            GameStrategyType.MOONSHOT,
            GameStrategyType.SNIPE,
            GameStrategyType.SMALL_CASHOUT
        );
    }

    @Test
    void keepsEarlyLockedPositionOnMoonshotTrack() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:59:40Z"), 100L);

        assertThat(GameStrategyResolver.resolvePositionStrategyTags(
            position,
            15,
            130L,
            false,
            Instant.parse("2026-04-01T06:00:00Z")
        )).containsExactly(
            GameStrategyType.SOLAR_SHOT,
            GameStrategyType.MOONSHOT,
            GameStrategyType.SNIPE
        );
    }

    @Test
    void resolvesMultipleHighlightTagsWhenConditionsOverlap() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 15, 320D)).containsExactly(
            GameStrategyType.SOLAR_SHOT,
            GameStrategyType.MOONSHOT,
            GameStrategyType.SMALL_CASHOUT,
            GameStrategyType.SNIPE
        );
    }

    @Test
    void treatsTopOneFromRankTwentyAsGalaxyAndAtlasShotBoundary() {
        GamePosition position = openPosition(20, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 1, 40D)).containsExactly(
            GameStrategyType.ATLAS_SHOT,
            GameStrategyType.GALAXY_SHOT
        );
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 2, 40D)).containsExactly(
            GameStrategyType.GALAXY_SHOT
        );
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(
            openPosition(19, Instant.parse("2026-04-01T05:30:00Z"), 100L),
            1,
            40D
        )).containsExactly(GameStrategyType.ATLAS_SHOT);
    }

    @Test
    void treatsTopFiveFromRankTwentyAsGalaxyShotBoundary() {
        GamePosition position = openPosition(20, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 5, 40D)).containsExactly(
            GameStrategyType.GALAXY_SHOT
        );
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 6, 40D)).isEqualTo(List.of());
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(
            openPosition(19, Instant.parse("2026-04-01T05:30:00Z"), 100L),
            5,
            40D
        )).isEqualTo(List.of());
    }

    @Test
    void treatsTopOneFromRankFiveAsAtlasShotBoundary() {
        GamePosition position = openPosition(5, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 1, 40D)).containsExactly(
            GameStrategyType.ATLAS_SHOT
        );
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 2, 40D)).isEqualTo(List.of());
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(
            openPosition(4, Instant.parse("2026-04-01T05:30:00Z"), 100L),
            1,
            40D
        )).isEqualTo(List.of());
    }

    @Test
    void treatsTopTwentyFromRankFiftyAsSolarShotBoundary() {
        GamePosition position = openPosition(50, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 20, 40D)).containsExactly(
            GameStrategyType.SOLAR_SHOT
        );
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 21, 40D)).isEqualTo(List.of());
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(
            openPosition(49, Instant.parse("2026-04-01T05:30:00Z"), 100L),
            20,
            40D
        )).isEqualTo(List.of());
    }

    @Test
    void ordersAtlasShotBeforeOverlappingGalaxySolarMoonshotAndSnipeTags() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 1, 320D)).containsExactly(
            GameStrategyType.ATLAS_SHOT,
            GameStrategyType.GALAXY_SHOT,
            GameStrategyType.SOLAR_SHOT,
            GameStrategyType.MOONSHOT,
            GameStrategyType.SMALL_CASHOUT,
            GameStrategyType.SNIPE
        );
    }

    @Test
    void treatsRankFiftyAsMoonshotTargetBoundary() {
        GamePosition position = openPosition(100, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 50, 40D)).containsExactly(
            GameStrategyType.MOONSHOT
        );
        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 51, 40D)).isEqualTo(List.of());
    }

    @Test
    void returnsEmptyWhenNoHighlightTagMatches() {
        GamePosition position = openPosition(90, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 80, 40D)).isEqualTo(List.of());
    }

    @Test
    void returnsEmptyWhenNoOpenPositionTargetMatches() {
        GamePosition position = openPosition(90, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolvePositionStrategyTags(
            position,
            80,
            110L,
            false,
            Instant.parse("2026-04-01T06:00:00Z")
        )).isEqualTo(List.of());
    }

    @Test
    void resolvesAchievedAndTargetTagsForCashoutUpgrade() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:30:00Z"), 100L);
        List<GameStrategyType> positionTags = GameStrategyResolver.resolvePositionStrategyTags(
            position,
            15,
            420L,
            false,
            Instant.parse("2026-04-01T06:00:00Z")
        );
        List<GameStrategyType> achievedTags = GameStrategyResolver.resolveAchievedPositionStrategyTags(position, 15, 420L);

        assertThat(achievedTags).containsExactly(
            GameStrategyType.SOLAR_SHOT,
            GameStrategyType.MOONSHOT,
            GameStrategyType.SMALL_CASHOUT,
            GameStrategyType.SNIPE
        );
        assertThat(GameStrategyResolver.resolveTargetPositionStrategyTags(position, 15, positionTags, achievedTags)).containsExactly(
            GameStrategyType.BIG_CASHOUT,
            GameStrategyType.GALAXY_SHOT
        );
    }

    @Test
    void resolvesOnlyTheNextShotTarget() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(shotTargetTags(position, 120)).containsExactly(GameStrategyType.SNIPE);
        assertThat(shotTargetTags(position, 80)).containsExactly(GameStrategyType.MOONSHOT);
        assertThat(shotTargetTags(position, 45)).containsExactly(GameStrategyType.SOLAR_SHOT);
        assertThat(shotTargetTags(position, 15)).containsExactly(GameStrategyType.GALAXY_SHOT);
        assertThat(shotTargetTags(position, 4)).containsExactly(GameStrategyType.ATLAS_SHOT);
    }

    @Test
    void skipsShotTargetsThatCannotBeEarnedFromBuyRank() {
        GamePosition solarPosition = openPosition(80, Instant.parse("2026-04-01T05:30:00Z"), 100L);
        GamePosition atlasPosition = openPosition(10, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(shotTargetTags(solarPosition, 80)).containsExactly(GameStrategyType.SOLAR_SHOT);
        assertThat(shotTargetTags(atlasPosition, 4)).containsExactly(GameStrategyType.ATLAS_SHOT);
    }

    private List<GameStrategyType> shotTargetTags(GamePosition position, int currentRank) {
        return targetTags(position, currentRank).stream()
            .filter(GameStrategyResolverTest::isShotTag)
            .toList();
    }

    private static boolean isShotTag(GameStrategyType tag) {
        return switch (tag) {
            case SNIPE, MOONSHOT, SOLAR_SHOT, GALAXY_SHOT, ATLAS_SHOT -> true;
            case SMALL_CASHOUT, BIG_CASHOUT -> false;
        };
    }

    private List<GameStrategyType> targetTags(GamePosition position, int currentRank) {
        List<GameStrategyType> positionTags = GameStrategyResolver.resolvePositionStrategyTags(
            position,
            currentRank,
            position.getStakePoints(),
            false,
            Instant.parse("2026-04-01T06:00:00Z")
        );
        List<GameStrategyType> achievedTags = GameStrategyResolver.resolveAchievedPositionStrategyTags(
            position,
            currentRank,
            position.getStakePoints()
        );
        return GameStrategyResolver.resolveTargetPositionStrategyTags(position, currentRank, positionTags, achievedTags);
    }

    private GamePosition openPosition(int buyRank, Instant createdAt, long stakePoints) {
        GameSeason season = new GameSeason();
        season.setMinHoldSeconds(60);

        AppUser user = new AppUser();
        GamePosition position = new GamePosition();
        position.setSeason(season);
        position.setUser(user);
        position.setRegionCode("KR");
        position.setCategoryId("0");
        position.setVideoId("video-1");
        position.setTitle("Video");
        position.setChannelTitle("Channel");
        position.setThumbnailUrl("https://example.com/thumb.jpg");
        position.setBuyRunId(1L);
        position.setBuyRank(buyRank);
        position.setBuyCapturedAt(createdAt);
        position.setStakePoints(stakePoints);
        position.setQuantity(GamePointCalculator.QUANTITY_SCALE);
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(createdAt);
        return position;
    }
}
