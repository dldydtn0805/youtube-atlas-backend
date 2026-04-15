package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GameCoinPayoutRepository extends JpaRepository<GameCoinPayout, Long> {

    @Modifying
    @Query("""
        delete from GameCoinPayout payout
        where payout.position.id in :positionIds
    """)
    long deleteByPositionIds(List<Long> positionIds);

    @Query("""
        select payout.position.id
        from GameCoinPayout payout
        where payout.season.id = :seasonId
          and payout.payoutSlotAt = :payoutSlotAt
    """)
    List<Long> findPositionIdsBySeasonIdAndPayoutSlotAt(Long seasonId, Instant payoutSlotAt);

    void deleteByUserId(Long userId);
}
