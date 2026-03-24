package com.yongsoo.youtubeatlasbackend.youtube.model;

public record AtlasThumbnails(
    AtlasThumbnail defaultThumbnail,
    AtlasThumbnail medium,
    AtlasThumbnail high,
    AtlasThumbnail standard,
    AtlasThumbnail maxres
) {
}
