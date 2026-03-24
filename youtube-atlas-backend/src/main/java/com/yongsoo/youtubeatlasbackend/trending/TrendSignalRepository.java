package com.yongsoo.youtubeatlasbackend.trending;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendSignalRepository extends JpaRepository<TrendSignal, TrendSignalId> {

    List<TrendSignal> findByIdRegionCodeAndIdCategoryIdAndIdVideoIdIn(
        String regionCode,
        String categoryId,
        List<String> videoIds
    );

    List<TrendSignal> findByIdRegionCodeAndIdCategoryId(String regionCode, String categoryId);
}
