package com.yongsoo.youtubeatlasbackend.trending;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrendSnapshotMaintenanceService {

    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;

    public TrendSnapshotMaintenanceService(
        TrendRunRepository trendRunRepository,
        TrendSnapshotRepository trendSnapshotRepository
    ) {
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void sanitizeSnapshotsOnStartup() {
        sanitizeAllRuns();
    }

    @Transactional
    public void sanitizeAllRuns() {
        for (TrendRun run : trendRunRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))) {
            sanitizeRunSnapshots(run.getId());
        }
    }

    @Transactional
    public List<TrendSnapshot> sanitizeRunSnapshots(Long runId) {
        List<TrendSnapshot> snapshots = trendSnapshotRepository.findByRunIdOrderByRankAsc(runId);

        if (snapshots.isEmpty()) {
            return List.of();
        }

        Map<String, TrendSnapshot> uniqueSnapshotsByVideoId = new LinkedHashMap<>();
        List<Long> duplicateSnapshotIds = new ArrayList<>();

        for (TrendSnapshot snapshot : snapshots) {
            TrendSnapshot existingSnapshot = uniqueSnapshotsByVideoId.putIfAbsent(snapshot.getVideoId(), snapshot);

            if (existingSnapshot != null && snapshot.getId() != null) {
                duplicateSnapshotIds.add(snapshot.getId());
            }
        }

        List<TrendSnapshot> sanitizedSnapshots = new ArrayList<>(uniqueSnapshotsByVideoId.values());

        for (int index = 0; index < sanitizedSnapshots.size(); index++) {
            TrendSnapshot snapshot = sanitizedSnapshots.get(index);
            int sanitizedRank = index + 1;

            if (!Objects.equals(snapshot.getRank(), sanitizedRank)) {
                snapshot.setRank(sanitizedRank);
            }
        }

        if (!duplicateSnapshotIds.isEmpty()) {
            trendSnapshotRepository.deleteAllByIdInBatch(duplicateSnapshotIds);
        }

        return sanitizedSnapshots;
    }
}
