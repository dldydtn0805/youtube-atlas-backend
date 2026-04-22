package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
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
}
