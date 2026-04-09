package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameCoinPayoutRepository extends JpaRepository<GameCoinPayout, Long> {

    @Query("""
        select payout.position.id
        from GameCoinPayout payout
        where payout.season.id = :seasonId
          and payout.trendRunId = :trendRunId
    """)
    List<Long> findPositionIdsBySeasonIdAndTrendRunId(Long seasonId, Long trendRunId);
}
