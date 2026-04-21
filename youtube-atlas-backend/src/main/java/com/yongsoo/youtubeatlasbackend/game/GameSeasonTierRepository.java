package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeasonTierRepository extends JpaRepository<GameSeasonTier, Long> {

    List<GameSeasonTier> findBySeasonIdOrderBySortOrderAsc(Long seasonId);
}
