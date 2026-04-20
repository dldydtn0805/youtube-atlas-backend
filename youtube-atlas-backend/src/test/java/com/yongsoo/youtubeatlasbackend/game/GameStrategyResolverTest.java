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
            GameStrategyType.MOONSHOT,
            GameStrategyType.SNIPE
        );
    }

    @Test
    void resolvesMultipleHighlightTagsWhenConditionsOverlap() {
        GamePosition position = openPosition(180, Instant.parse("2026-04-01T05:30:00Z"), 100L);

        assertThat(GameStrategyResolver.resolveHighlightStrategyTags(position, 15, 320D)).containsExactly(
            GameStrategyType.MOONSHOT,
            GameStrategyType.SMALL_CASHOUT,
            GameStrategyType.SNIPE
        );
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
            GameStrategyType.MOONSHOT,
            GameStrategyType.SMALL_CASHOUT,
            GameStrategyType.SNIPE
        );
        assertThat(GameStrategyResolver.resolveTargetPositionStrategyTags(positionTags, achievedTags)).containsExactly(
            GameStrategyType.BIG_CASHOUT
        );
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
