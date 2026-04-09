package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminSeasonScheduleUpdateRequest;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminSeasonSummaryResponse;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSettlementService;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;

@Service
public class AdminSeasonService {

    private final GameSeasonRepository gameSeasonRepository;
    private final GameSettlementService gameSettlementService;
    private final Clock clock;

    public AdminSeasonService(
        GameSeasonRepository gameSeasonRepository,
        GameSettlementService gameSettlementService,
        Clock clock
    ) {
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameSettlementService = gameSettlementService;
        this.clock = clock;
    }

    @Transactional
    public AdminSeasonSummaryResponse updateSeasonSchedule(Long seasonId, AdminSeasonScheduleUpdateRequest request) {
        GameSeason season = requireSeason(seasonId);
        validateScheduleUpdateRequest(request, season);

        season.setStartAt(request.startAt());
        season.setEndAt(request.endAt());

        return toSummaryResponse(gameSeasonRepository.save(season));
    }

    @Transactional
    public void closeSeason(Long seasonId) {
        gameSettlementService.closeSeason(seasonId);
    }

    private GameSeason requireSeason(Long seasonId) {
        if (seasonId == null) {
            throw new IllegalArgumentException("seasonId는 필수입니다.");
        }

        return gameSeasonRepository.findById(seasonId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시즌입니다."));
    }

    private void validateScheduleUpdateRequest(AdminSeasonScheduleUpdateRequest request, GameSeason season) {
        if (request == null) {
            throw new IllegalArgumentException("시즌 수정 정보가 필요합니다.");
        }

        if (request.startAt() == null) {
            throw new IllegalArgumentException("startAt은 필수입니다.");
        }

        if (request.endAt() == null) {
            throw new IllegalArgumentException("endAt은 필수입니다.");
        }

        if (!request.endAt().isAfter(request.startAt())) {
            throw new IllegalArgumentException("endAt은 startAt보다 이후여야 합니다.");
        }

        Instant now = Instant.now(clock);
        if (season.getStatus() == SeasonStatus.ACTIVE && request.startAt().isAfter(now)) {
            throw new IllegalArgumentException("ACTIVE 시즌의 startAt은 현재 시각 이후로 변경할 수 없습니다.");
        }

        if (season.getStatus() == SeasonStatus.ACTIVE && !request.endAt().isAfter(now)) {
            throw new IllegalArgumentException("ACTIVE 시즌의 endAt을 현재 시각 이전으로 변경할 수 없습니다. 수동 종료를 사용하세요.");
        }
    }

    private AdminSeasonSummaryResponse toSummaryResponse(GameSeason season) {
        return new AdminSeasonSummaryResponse(
            season.getId(),
            season.getName(),
            season.getStatus().name(),
            season.getRegionCode(),
            season.getStartAt(),
            season.getEndAt(),
            season.getCreatedAt()
        );
    }
}
