package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GamePositionRepository extends JpaRepository<GamePosition, Long> {

    long countBySeasonIdAndUserIdAndStatus(Long seasonId, Long userId, PositionStatus status);

    long countBySeasonIdAndUserIdAndVideoIdAndStatus(Long seasonId, Long userId, String videoId, PositionStatus status);

    @Query("""
        select count(distinct position.videoId)
        from GamePosition position
        where position.season.id = :seasonId
          and position.user.id = :userId
          and position.status = :status
    """)
    long countDistinctVideoIdBySeasonIdAndUserIdAndStatus(Long seasonId, Long userId, PositionStatus status);

    Optional<GamePosition> findByIdAndUserId(Long id, Long userId);

    List<GamePosition> findBySeasonIdAndUserIdOrderByCreatedAtDesc(Long seasonId, Long userId);

    List<GamePosition> findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAsc(
        Long seasonId,
        Long userId,
        String videoId,
        PositionStatus status
    );

    List<GamePosition> findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(Long seasonId, Long userId, PositionStatus status);

    List<GamePosition> findBySeasonIdAndStatus(Long seasonId, PositionStatus status);
}
