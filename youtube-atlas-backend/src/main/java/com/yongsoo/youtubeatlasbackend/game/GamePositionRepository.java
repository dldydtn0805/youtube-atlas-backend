package com.yongsoo.youtubeatlasbackend.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface GamePositionRepository extends JpaRepository<GamePosition, Long> {

    @Query("""
        select position
        from GamePosition position
        where position.status in :statuses
          and (
            position.closedAt < :deleteBefore
            or (position.closedAt is null and position.createdAt < :deleteBefore)
          )
        order by position.closedAt asc nulls first, position.createdAt asc
    """)
    List<GamePosition> findCleanupTargets(Collection<PositionStatus> statuses, java.time.Instant deleteBefore);

    long countBySeasonIdAndUserIdAndStatus(Long seasonId, Long userId, PositionStatus status);

    long countBySeasonIdAndUserIdAndStatusIn(Long seasonId, Long userId, Collection<PositionStatus> statuses);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select position
        from GamePosition position
        where position.id = :id
          and position.user.id = :userId
    """)
    Optional<GamePosition> findByIdAndUserIdForUpdate(Long id, Long userId);

    List<GamePosition> findBySeasonIdAndUserIdOrderByCreatedAtDesc(Long seasonId, Long userId);

    List<GamePosition> findBySeasonIdAndUserIdOrderByCreatedAtDesc(Long seasonId, Long userId, Pageable pageable);

    List<GamePosition> findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAsc(
        Long seasonId,
        Long userId,
        String videoId,
        PositionStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select position
        from GamePosition position
        where position.season.id = :seasonId
          and position.user.id = :userId
          and position.videoId = :videoId
          and position.status = :status
        order by position.createdAt asc
    """)
    List<GamePosition> findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(
        Long seasonId,
        Long userId,
        String videoId,
        PositionStatus status
    );

    List<GamePosition> findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(Long seasonId, Long userId, PositionStatus status);

    List<GamePosition> findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
        Long seasonId,
        Long userId,
        PositionStatus status,
        Pageable pageable
    );

    List<GamePosition> findBySeasonIdAndStatus(Long seasonId, PositionStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        delete from GamePosition position
        where position.id in :ids
    """)
    long deleteByIds(Collection<Long> ids);

    void deleteByUserId(Long userId);
}
