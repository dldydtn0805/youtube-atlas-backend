package com.yongsoo.youtubeatlasbackend.game.api;

public record WalletResponse(
    Long seasonId,
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints,
    Long bonusPoints,
    Long totalAssetPoints
) {
}
