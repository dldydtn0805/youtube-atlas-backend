package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface GameHighlightStateRepository extends JpaRepository<GameHighlightState, Long> {

    long countBySeasonId(Long seasonId);

    long countBySeasonIdAndUserId(Long seasonId, Long userId);

    List<GameHighlightState> findBySeasonIdAndUserId(Long seasonId, Long userId);

    List<GameHighlightState> findBySeasonIdAndUserIdAndBestSettledHighlightScoreGreaterThanOrderByBestSettledCreatedAtDesc(
        Long seasonId,
        Long userId,
        Long score
    );

    List<GameHighlightState> findBySeasonIdAndBestSettledHighlightScoreGreaterThan(Long seasonId, Long score);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select state
        from GameHighlightState state
        where state.season.id = :seasonId
          and state.user.id = :userId
          and state.rootPositionId = :rootPositionId
    """)
    Optional<GameHighlightState> findBySeasonIdAndUserIdAndRootPositionIdForUpdate(
        Long seasonId,
        Long userId,
        Long rootPositionId
    );
}
