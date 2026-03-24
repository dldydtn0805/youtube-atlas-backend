package com.yongsoo.youtubeatlasbackend.trending;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendRunRepository extends JpaRepository<TrendRun, Long> {

    Optional<TrendRun> findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(
        String regionCode,
        String categoryId,
        Long id
    );
}
