package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;

class GameNotificationFactoryTest {

    @Test
    void projectedCashoutMessageRoundsProfitRateToSingleDecimal() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", 300L);
        position.setSeason(season);
        position.setUser(user);
        position.setVideoId("video-1");
        position.setTitle("테스트 영상");
        position.setChannelTitle("테스트 채널");
        position.setThumbnailUrl("https://example.com/thumb.jpg");
        position.setBuyRank(150);
        position.setStakePoints(100L);

        List<GameNotificationResponse> notifications = GameNotificationFactory.fromPositionSnapshot(
            position,
            20,
            491L,
            Instant.parse("2026-04-22T12:00:00Z")
        );

        assertThat(notifications)
            .extracting(GameNotificationResponse::message)
            .anyMatch(message -> message.contains("수익률 391% 플레이가 기록됐습니다."));
    }

    @Test
    void projectedSolarShotUsesSolarShotTitleAndMessage() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", 300L);
        position.setSeason(season);
        position.setUser(user);
        position.setVideoId("video-1");
        position.setTitle("테스트 영상");
        position.setChannelTitle("테스트 채널");
        position.setThumbnailUrl("https://example.com/thumb.jpg");
        position.setBuyRank(50);
        position.setStakePoints(100L);

        List<GameNotificationResponse> notifications = GameNotificationFactory.fromPositionSnapshot(
            position,
            20,
            130L,
            Instant.parse("2026-04-22T12:00:00Z")
        );

        assertThat(notifications)
            .anySatisfy(notification -> {
                assertThat(notification.notificationType()).isEqualTo("SOLAR_SHOT");
                assertThat(notification.title()).isEqualTo("솔라 샷 예상");
                assertThat(notification.message()).contains("50위에서 잡은 영상이 20위까지 올라왔습니다.");
            });
    }

    @Test
    void projectedGalaxyShotUsesGalaxyShotTitleAndMessage() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);

        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);

        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", 300L);
        position.setSeason(season);
        position.setUser(user);
        position.setVideoId("video-1");
        position.setTitle("테스트 영상");
        position.setChannelTitle("테스트 채널");
        position.setThumbnailUrl("https://example.com/thumb.jpg");
        position.setBuyRank(20);
        position.setStakePoints(100L);

        List<GameNotificationResponse> notifications = GameNotificationFactory.fromPositionSnapshot(
            position,
            5,
            130L,
            Instant.parse("2026-04-22T12:00:00Z")
        );

        assertThat(notifications)
            .anySatisfy(notification -> {
                assertThat(notification.notificationType()).isEqualTo("GALAXY_SHOT");
                assertThat(notification.title()).isEqualTo("갤럭시 샷 예상");
                assertThat(notification.message()).contains("20위에서 잡은 영상이 5위까지 올라왔습니다.");
            });
    }

    @Test
    void settledHighlightNotificationUsesScoreGain() {
        GameHighlightResponse highlight = highlight(List.of(GameStrategyType.SMALL_CASHOUT), 12_000L);

        List<GameNotificationResponse> notifications = GameNotificationFactory.fromHighlight(
            highlight,
            3_500L,
            List.of(GameStrategyType.SMALL_CASHOUT)
        );

        assertThat(notifications)
            .singleElement()
            .satisfies(notification -> {
                assertThat(notification.notificationType()).isEqualTo("SMALL_CASHOUT");
                assertThat(notification.highlightScore()).isEqualTo(3_500L);
            });
    }

    @Test
    void settledHighlightNotificationSkipsNonPositiveScoreGain() {
        GameHighlightResponse highlight = highlight(List.of(GameStrategyType.SMALL_CASHOUT), 12_000L);

        assertThat(GameNotificationFactory.fromHighlight(
            highlight,
            0L,
            List.of(GameStrategyType.SMALL_CASHOUT)
        )).isEmpty();
    }

    @Test
    void settledHighlightNotificationUsesPerTagScoreGain() {
        GameHighlightResponse highlight = highlight(
            List.of(GameStrategyType.SMALL_CASHOUT, GameStrategyType.SNIPE),
            12_000L
        );

        List<GameNotificationResponse> notifications = GameNotificationFactory.fromHighlight(
            highlight,
            Map.of(
                GameStrategyType.SMALL_CASHOUT, 1_200L,
                GameStrategyType.SNIPE, 3_400L
            ),
            highlight.strategyTags()
        );

        assertThat(notifications)
            .anySatisfy(notification -> {
                assertThat(notification.notificationType()).isEqualTo("SMALL_CASHOUT");
                assertThat(notification.highlightScore()).isEqualTo(1_200L);
            })
            .anySatisfy(notification -> {
                assertThat(notification.notificationType()).isEqualTo("SNIPE");
                assertThat(notification.highlightScore()).isEqualTo(3_400L);
            });
    }

    private GameHighlightResponse highlight(List<GameStrategyType> strategyTags, long highlightScore) {
        return new GameHighlightResponse(
            "300-SMALL_CASHOUT",
            "SMALL_CASHOUT",
            "스몰 캐시아웃",
            "수익률 300% 플레이가 기록됐습니다.",
            300L,
            "video-1",
            "테스트 영상",
            "테스트 채널",
            "https://example.com/thumb.jpg",
            140,
            80,
            80,
            60,
            GamePointCalculator.QUANTITY_SCALE,
            17_000L,
            110_000L,
            93_000L,
            547D,
            strategyTags,
            highlightScore,
            PositionStatus.CLOSED.name(),
            Instant.parse("2026-04-22T12:00:00Z")
        );
    }
}
