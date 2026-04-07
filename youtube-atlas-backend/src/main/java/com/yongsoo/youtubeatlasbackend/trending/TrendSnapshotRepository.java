package com.yongsoo.youtubeatlasbackend.trending;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendSnapshotRepository extends JpaRepository<TrendSnapshot, Long> {

    List<TrendSnapshot> findTop8ByRun_IdOrderByRankAsc(Long runId);

    List<TrendSnapshot> findByRunId(Long runId);

    List<TrendSnapshot> findByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc(
        String regionCode,
        String categoryId,
        String videoId
    );

    List<TrendSnapshot> findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
        String regionCode,
        String categoryId,
        String videoId,
        Long startRunId,
        Long endRunId
    );

    Optional<TrendSnapshot> findFirstByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc(
        String regionCode,
        String categoryId,
        String videoId
    );
}
