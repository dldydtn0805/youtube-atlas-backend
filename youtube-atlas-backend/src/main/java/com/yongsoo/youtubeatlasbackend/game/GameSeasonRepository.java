package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSeasonRepository extends JpaRepository<GameSeason, Long> {

    Optional<GameSeason> findTopByStatusOrderByStartAtDesc(SeasonStatus status);

    List<GameSeason> findByStatus(SeasonStatus status);

    List<GameSeason> findByStatusAndEndAtLessThanEqual(SeasonStatus status, Instant endAt);
}
