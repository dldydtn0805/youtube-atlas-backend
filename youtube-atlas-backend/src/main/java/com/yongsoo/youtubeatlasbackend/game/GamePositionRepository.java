package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GamePositionRepository extends JpaRepository<GamePosition, Long> {

    long countBySeasonIdAndUserIdAndStatus(Long seasonId, Long userId, PositionStatus status);

    boolean existsBySeasonIdAndUserIdAndVideoIdAndStatus(Long seasonId, Long userId, String videoId, PositionStatus status);

    Optional<GamePosition> findByIdAndUserId(Long id, Long userId);

    List<GamePosition> findBySeasonIdAndUserIdOrderByCreatedAtDesc(Long seasonId, Long userId);

    List<GamePosition> findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(Long seasonId, Long userId, PositionStatus status);

    List<GamePosition> findBySeasonIdAndStatus(Long seasonId, PositionStatus status);
}
