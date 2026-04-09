package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeasonRepository extends JpaRepository<GameSeason, Long> {

    Optional<GameSeason> findTopByStatusOrderByStartAtDesc(SeasonStatus status);

    Optional<GameSeason> findByIdAndStatus(Long id, SeasonStatus status);

    Optional<GameSeason> findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus status, String regionCode);

    Optional<GameSeason> findTopByRegionCodeOrderByStartAtDesc(String regionCode);

    List<GameSeason> findByStatus(SeasonStatus status);

    List<GameSeason> findByStatusAndEndAtLessThanEqual(SeasonStatus status, Instant endAt);
}
