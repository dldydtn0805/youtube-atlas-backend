package com.yongsoo.youtubeatlasbackend.youtube.model;

import java.util.List;

public record AtlasVideoCategory(
    String id,
    String label,
    String description,
    List<String> sourceIds
) {
}
