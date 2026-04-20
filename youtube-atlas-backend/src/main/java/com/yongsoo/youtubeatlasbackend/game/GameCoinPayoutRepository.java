package com.yongsoo.youtubeatlasbackend.game;

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

    void deleteByUserId(Long userId);
}
