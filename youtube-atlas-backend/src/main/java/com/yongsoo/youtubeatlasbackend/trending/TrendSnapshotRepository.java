package com.yongsoo.youtubeatlasbackend.trending;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrendSnapshotRepository extends JpaRepository<TrendSnapshot, Long> {

    List<TrendSnapshot> findTop8ByRun_IdOrderByRankAsc(Long runId);

    List<TrendSnapshot> findByRunId(Long runId);

    List<TrendSnapshot> findByRunIdOrderByRankAsc(Long runId);

    @Query("""
        select snapshot
        from TrendSnapshot snapshot
        where snapshot.regionCode = :regionCode
          and snapshot.categoryId = :categoryId
          and snapshot.run.id = (
              select max(candidate.run.id)
              from TrendSnapshot candidate
              where candidate.regionCode = :regionCode
                and candidate.categoryId = :categoryId
                and candidate.run.capturedAt = (
                    select max(latest.run.capturedAt)
                    from TrendSnapshot latest
                    where latest.regionCode = :regionCode
                      and latest.categoryId = :categoryId
                )
          )
        order by snapshot.rank asc
        """)
    List<TrendSnapshot> findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc(
        String regionCode,
        String categoryId
    );

    void deleteByRun_IdIn(List<Long> runIds);

    void deleteByRun_Id(Long runId);

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

    @Query("""
        select snapshot
        from TrendSnapshot snapshot
        join fetch snapshot.run run
        where snapshot.regionCode = :regionCode
          and snapshot.categoryId = :categoryId
          and snapshot.videoId = :videoId
          and run.capturedAt <= :capturedAt
        order by run.capturedAt desc, run.id desc
        """)
    List<TrendSnapshot> findSnapshotsByRegionCodeAndCategoryIdAndVideoIdCapturedBeforeOrderByCapturedAtDesc(
        String regionCode,
        String categoryId,
        String videoId,
        Instant capturedAt
    );

    @Query("""
        select snapshot
        from TrendSnapshot snapshot
        join fetch snapshot.run run
        where snapshot.createdAt >= :startAt
          and snapshot.createdAt <= :endAt
          and upper(snapshot.regionCode) = upper(:regionCode)
        order by snapshot.createdAt desc, run.id desc, snapshot.rank asc
        """)
    List<TrendSnapshot> findByCreatedAtBetweenAndRegionCodeOrderByCreatedAtDesc(
        Instant startAt,
        Instant endAt,
        String regionCode
    );
}
