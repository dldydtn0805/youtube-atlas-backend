package com.yongsoo.youtubeatlasbackend.game.api;

import java.time.Instant;

public record SeasonResultResponse(
    Long id,
    Long seasonId,
    String seasonName,
    String regionCode,
    Instant seasonStartAt,
    Instant seasonEndAt,
    Integer finalRank,
    Long finalAssetPoints,
    Long finalBalancePoints,
    Long realizedPnlPoints,
    Long positionCount,
    Long bestPositionId,
    String bestPositionVideoId,
    String bestPositionTitle,
    String bestPositionChannelTitle,
    String bestPositionThumbnailUrl,
    Long bestPositionProfitPoints,
    String titleCode,
    Instant createdAt
) {
}
