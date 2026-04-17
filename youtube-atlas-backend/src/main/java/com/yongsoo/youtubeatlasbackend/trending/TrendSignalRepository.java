package com.yongsoo.youtubeatlasbackend.trending;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrendSignalRepository extends JpaRepository<TrendSignal, TrendSignalId> {

    List<TrendSignal> findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn(
        String regionCode,
        String categoryId,
        List<String> videoIds
    );

    List<TrendSignal> findByIdRegionCodeAndIdCategoryId(String regionCode, String categoryId);

    List<TrendSignal> findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(String regionCode, String categoryId);

    List<TrendSignal> findByIdRegionCodeAndIdCategoryIdAndRankChangeGreaterThanEqualOrderByRankChangeDescCurrentRankAsc(
        String regionCode,
        String categoryId,
        Integer rankChange
    );

    List<TrendSignal> findTop10ByIdRegionCodeAndIdCategoryIdAndRankChangeGreaterThanOrderByRankChangeDescCurrentRankAsc(
        String regionCode,
        String categoryId,
        Integer rankChange
    );

    @Query("""
        select signal
        from TrendSignal signal
        where signal.id.regionCode = :regionCode
          and signal.id.categoryId = :categoryId
          and signal.isNew = true
        order by signal.currentRank asc
        """)
    List<TrendSignal> findNewEntriesByRegionCodeAndCategoryId(String regionCode, String categoryId);
}
