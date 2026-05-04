package com.yongsoo.youtubeatlasbackend.game;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GameSeasonResultRepository extends JpaRepository<GameSeasonResult, Long> {

    List<GameSeasonResult> findBySeasonId(Long seasonId);

    @Query("""
        select result
        from GameSeasonResult result
        where result.user.id = :userId
          and result.regionCode = :regionCode
        order by result.seasonEndAt desc, result.createdAt desc
    """)
    List<GameSeasonResult> findAllByUserAndRegion(Long userId, String regionCode);

    @Query("""
        select result
        from GameSeasonResult result
        where result.user.id = :userId
          and result.regionCode = :regionCode
        order by result.seasonEndAt desc, result.createdAt desc
    """)
    List<GameSeasonResult> findRecentByUserAndRegion(Long userId, String regionCode, Pageable pageable);
}
