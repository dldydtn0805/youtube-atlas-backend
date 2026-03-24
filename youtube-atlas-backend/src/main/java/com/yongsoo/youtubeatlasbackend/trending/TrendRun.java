package com.yongsoo.youtubeatlasbackend.trending;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "video_trend_runs")
public class TrendRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "region_code", nullable = false)
    private String regionCode;

    @Column(name = "category_id", nullable = false)
    private String categoryId;

    @Column(name = "category_label", nullable = false)
    private String categoryLabel;

    @Column(name = "source_category_ids", nullable = false, length = 1000)
    @Convert(converter = StringListConverter.class)
    private List<String> sourceCategoryIds;

    @Column(nullable = false)
    private String source;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    public Long getId() {
        return id;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public List<String> getSourceCategoryIds() {
        return sourceCategoryIds;
    }

    public void setSourceCategoryIds(List<String> sourceCategoryIds) {
        this.sourceCategoryIds = sourceCategoryIds;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }
}
