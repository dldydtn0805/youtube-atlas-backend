package com.yongsoo.youtubeatlasbackend.game;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface GameScheduledSellOrderRepository extends JpaRepository<GameScheduledSellOrder, Long> {

    @Query("""
        select count(scheduledOrder) > 0
        from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.position.id = :positionId
          and scheduledOrder.status = :status
    """)
    boolean existsByPositionIdAndStatus(Long positionId, ScheduledSellOrderStatus status);

    @Query("""
        select coalesce(sum(scheduledOrder.quantity), 0)
        from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.position.id = :positionId
          and scheduledOrder.status = :status
    """)
    Long sumQuantityByPositionIdAndStatus(Long positionId, ScheduledSellOrderStatus status);

    @Query("""
        select scheduledOrder
        from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.position.id in :positionIds
          and scheduledOrder.status = :status
    """)
    List<GameScheduledSellOrder> findByPositionIdsAndStatus(Collection<Long> positionIds, ScheduledSellOrderStatus status);

    List<GameScheduledSellOrder> findBySeasonIdAndUserIdOrderByCreatedAtDesc(Long seasonId, Long userId);

    @Query("""
        select scheduledOrder
        from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.regionCode = :regionCode
          and scheduledOrder.status = :status
        order by scheduledOrder.createdAt asc, scheduledOrder.id asc
    """)
    List<GameScheduledSellOrder> findByRegionCodeAndStatusOrderByCreatedAtAsc(String regionCode, ScheduledSellOrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select scheduledOrder
        from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.id = :id
    """)
    Optional<GameScheduledSellOrder> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select scheduledOrder
        from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.id = :id
          and scheduledOrder.user.id = :userId
    """)
    Optional<GameScheduledSellOrder> findByIdAndUserIdForUpdate(Long id, Long userId);

    @Modifying
    @Query("""
        delete from GameScheduledSellOrder scheduledOrder
        where scheduledOrder.position.id in :positionIds
    """)
    long deleteByPositionIds(Collection<Long> positionIds);

    void deleteByUserId(Long userId);
}
