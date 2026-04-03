package com.yongsoo.youtubeatlasbackend.trending;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendRunRepository extends JpaRepository<TrendRun, Long> {

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdOrderByIdDesc(String regionCode, String categoryId);

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(
        String regionCode,
        String categoryId,
        Long id
    );

    List<TrendRun> findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc(
        String regionCode,
        String categoryId,
        Long startId,
        Long endId
    );
}
