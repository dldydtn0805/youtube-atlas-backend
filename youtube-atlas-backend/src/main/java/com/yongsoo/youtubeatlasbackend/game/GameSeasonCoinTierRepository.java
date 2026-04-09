package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeasonCoinTierRepository extends JpaRepository<GameSeasonCoinTier, Long> {

    List<GameSeasonCoinTier> findBySeasonIdOrderBySortOrderAsc(Long seasonId);
}
