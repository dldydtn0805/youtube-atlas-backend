package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminSeasonScheduleUpdateRequest;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSettlementService;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;

class AdminSeasonServiceTest {

    private GameSeasonRepository gameSeasonRepository;
    private GameSettlementService gameSettlementService;
    private AdminSeasonService adminSeasonService;

    @BeforeEach
    void setUp() {
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gameSettlementService = org.mockito.Mockito.mock(GameSettlementService.class);
        adminSeasonService = new AdminSeasonService(
            gameSeasonRepository,
            gameSettlementService,
            Clock.fixed(Instant.parse("2026-04-09T03:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void updateSeasonScheduleOverridesStartAndEndAt() {
        GameSeason season = activeSeason(3L, "KR");
        when(gameSeasonRepository.findById(3L)).thenReturn(Optional.of(season));
        when(gameSeasonRepository.save(season)).thenReturn(season);

        var response = adminSeasonService.updateSeasonSchedule(
            3L,
            new AdminSeasonScheduleUpdateRequest(
                Instant.parse("2026-04-08T00:00:00Z"),
                Instant.parse("2026-04-10T00:00:00Z")
            )
        );

        assertThat(response.startAt()).isEqualTo(Instant.parse("2026-04-08T00:00:00Z"));
        assertThat(response.endAt()).isEqualTo(Instant.parse("2026-04-10T00:00:00Z"));
        assertThat(season.getStartAt()).isEqualTo(Instant.parse("2026-04-08T00:00:00Z"));
        assertThat(season.getEndAt()).isEqualTo(Instant.parse("2026-04-10T00:00:00Z"));
    }

    @Test
    void updateSeasonScheduleRejectsPastEndAtForActiveSeason() {
        GameSeason season = activeSeason(3L, "KR");
        when(gameSeasonRepository.findById(3L)).thenReturn(Optional.of(season));

        assertThatThrownBy(() -> adminSeasonService.updateSeasonSchedule(
            3L,
            new AdminSeasonScheduleUpdateRequest(
                Instant.parse("2026-04-08T00:00:00Z"),
                Instant.parse("2026-04-09T02:00:00Z")
            )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("수동 종료");
    }

    @Test
    void closeSeasonDelegatesToSettlementService() {
        adminSeasonService.closeSeason(9L);

        verify(gameSettlementService).closeSeason(9L);
    }

    private GameSeason activeSeason(Long id, String regionCode) {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", id);
        season.setName(regionCode + " Daily Season");
        season.setStatus(SeasonStatus.ACTIVE);
        season.setRegionCode(regionCode);
        season.setStartAt(Instant.parse("2026-04-01T00:00:00Z"));
        season.setEndAt(Instant.parse("2026-04-12T00:00:00Z"));
        season.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return season;
    }
}
