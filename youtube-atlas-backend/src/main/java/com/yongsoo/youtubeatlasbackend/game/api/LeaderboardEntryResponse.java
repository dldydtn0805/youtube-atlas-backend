package com.yongsoo.youtubeatlasbackend.game.api;

public record LeaderboardEntryResponse(
    Integer rank,
    Long userId,
    String displayName,
    String pictureUrl,
    Long totalAssetPoints,
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints,
    Long unrealizedPnlPoints,
    Integer openPositionCount,
    Boolean me
) {
}
