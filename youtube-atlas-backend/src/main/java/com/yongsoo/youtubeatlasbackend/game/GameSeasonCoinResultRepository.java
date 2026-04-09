package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameSeasonCoinResultRepository extends JpaRepository<GameSeasonCoinResult, Long> {

    Optional<GameSeasonCoinResult> findBySeasonIdAndUserId(Long seasonId, Long userId);

    @Query("""
        select result.user.id
        from GameSeasonCoinResult result
        where result.season.id = :seasonId
    """)
    List<Long> findUserIdsBySeasonId(Long seasonId);
}
