package com.yongsoo.youtubeatlasbackend.trending;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrendRunRepository extends JpaRepository<TrendRun, Long> {

    Optional<TrendRun> findTopByOrderByCapturedAtDesc();

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdOrderByIdDesc(String regionCode, String categoryId);

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdOrderByCapturedAtDescIdDesc(String regionCode, String categoryId);

    Optional<TrendRun> findByRegionCodeAndCategoryIdAndSourceAndCapturedAt(
        String regionCode,
        String categoryId,
        String source,
        java.time.Instant capturedAt
    );

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(
        String regionCode,
        String categoryId,
        Long id
    );

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
        String regionCode,
        String categoryId,
        String source,
        java.time.Instant capturedAt
    );

    List<TrendRun> findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc(
        String regionCode,
        String categoryId,
        Long startId,
        Long endId
    );

    @Query("""
        select run.id
        from TrendRun run
        where run.regionCode = :regionCode
          and run.categoryId = :categoryId
          and run.capturedAt < :capturedBefore
        order by run.id asc
        """)
    List<Long> findIdsByRegionCodeAndCategoryIdAndCapturedAtBefore(
        String regionCode,
        String categoryId,
        java.time.Instant capturedBefore
    );
}
