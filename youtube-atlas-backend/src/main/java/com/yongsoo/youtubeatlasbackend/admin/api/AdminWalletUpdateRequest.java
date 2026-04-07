package com.yongsoo.youtubeatlasbackend.admin.api;

public record AdminWalletUpdateRequest(
    Long balancePoints,
    Long reservedPoints,
    Long realizedPnlPoints
) {
}
