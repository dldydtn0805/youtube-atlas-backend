package com.yongsoo.youtubeatlasbackend.game.api;

public record DividendRankResponse(
    Integer rank,
    Integer weight,
    Double equalValuePoolSharePercent
) {
}
