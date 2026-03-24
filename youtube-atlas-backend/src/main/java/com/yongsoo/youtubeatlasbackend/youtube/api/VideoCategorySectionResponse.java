package com.yongsoo.youtubeatlasbackend.youtube.api;

import java.util.List;

public record VideoCategorySectionResponse(
    String categoryId,
    String label,
    String description,
    List<VideoItemResponse> items,
    String nextPageToken
) {
}
