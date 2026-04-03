package com.yongsoo.youtubeatlasbackend.trending;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendSnapshotRepository extends JpaRepository<TrendSnapshot, Long> {

    List<TrendSnapshot> findByRunId(Long runId);

    List<TrendSnapshot> findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
        String regionCode,
        String categoryId,
        String videoId,
        Long startRunId,
        Long endRunId
    );
}
