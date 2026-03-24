package com.yongsoo.youtubeatlasbackend.trending.api;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public record SyncTrendingRequest(
    @NotBlank(message = "regionCode는 필수입니다.") String regionCode,
    @NotBlank(message = "categoryId는 필수입니다.") String categoryId,
    @NotBlank(message = "categoryLabel은 필수입니다.") String categoryLabel,
    List<String> sourceCategoryIds
) {
}
