package com.yongsoo.youtubeatlasbackend.admin.api;

import java.time.Instant;
import java.util.List;

public record AdminTrendSnapshotHistoryResponse(
    Instant startAt,
    Instant endAt,
    int count,
    List<AdminTrendSnapshotHistoryItemResponse> items
) {
}
