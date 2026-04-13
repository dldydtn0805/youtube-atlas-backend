package com.yongsoo.youtubeatlasbackend.youtube.api;

import java.util.List;

public record VideoCategorySectionResponse(
    String categoryId,
    String label,
    String description,
    List<AvailableCategoryResponse> availableCategories,
    List<VideoItemResponse> items,
    String nextPageToken
) {
    public record AvailableCategoryResponse(
        String id,
        String label,
        long count
    ) {
    }
}
