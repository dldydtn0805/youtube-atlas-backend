package com.yongsoo.youtubeatlasbackend.trending;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class TrendSignalId implements Serializable {

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "video_id", nullable = false)
    private String videoId;

    public TrendSignalId() {
    }

    public TrendSignalId(String regionCode, String categoryId, String videoId) {
        this.regionCode = regionCode;
        this.categoryId = categoryId;
        this.videoId = videoId;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getVideoId() {
        return videoId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof TrendSignalId other)) {
            return false;
        }

        return Objects.equals(regionCode, other.regionCode)
            && Objects.equals(categoryId, other.categoryId)
            && Objects.equals(videoId, other.videoId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(regionCode, categoryId, videoId);
    }
}
