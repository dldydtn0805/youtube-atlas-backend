package com.yongsoo.youtubeatlasbackend.youtube.api;

import java.util.List;

public record VideoCategoryResponse(
    String id,
    String label,
    String description,
    List<String> sourceIds
) {
}
