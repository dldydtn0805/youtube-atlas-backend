package com.yongsoo.youtubeatlasbackend.trending;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "video_trend_signals")
public class TrendSignal {

    @EmbeddedId
    private TrendSignalId id;

    @Column(name = "category_label", nullable = false)
    private String categoryLabel;

    @Column(name = "current_run_id", nullable = false)
    private Long currentRunId;

    @Column(name = "previous_run_id")
    private Long previousRunId;

    @Column(name = "current_rank", nullable = false)
    private Integer currentRank;

    @Column(name = "previous_rank")
    private Integer previousRank;

    @Column(name = "rank_change")
    private Integer rankChange;

    @Column(name = "current_view_count")
    private Long currentViewCount;

    @Column(name = "previous_view_count")
    private Long previousViewCount;

    @Column(name = "view_count_delta")
    private Long viewCountDelta;

    @Column(name = "is_new", nullable = false)
    private boolean isNew;

    @Column(nullable = false)
    private String title;

    @Column(name = "channel_title", nullable = false)
    private String channelTitle;

    @Column(name = "thumbnail_url", nullable = false, length = 2000)
    private String thumbnailUrl;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public TrendSignalId getId() {
        return id;
    }

    public void setId(TrendSignalId id) {
        this.id = id;
    }

    public String getCategoryLabel() {
        return categoryLabel;
    }

    public void setCategoryLabel(String categoryLabel) {
        this.categoryLabel = categoryLabel;
    }

    public Long getCurrentRunId() {
        return currentRunId;
    }

    public void setCurrentRunId(Long currentRunId) {
        this.currentRunId = currentRunId;
    }

    public Long getPreviousRunId() {
        return previousRunId;
    }

    public void setPreviousRunId(Long previousRunId) {
        this.previousRunId = previousRunId;
    }

    public Integer getCurrentRank() {
        return currentRank;
    }

    public void setCurrentRank(Integer currentRank) {
        this.currentRank = currentRank;
    }

    public Integer getPreviousRank() {
        return previousRank;
    }

    public void setPreviousRank(Integer previousRank) {
        this.previousRank = previousRank;
    }

    public Integer getRankChange() {
        return rankChange;
    }

    public void setRankChange(Integer rankChange) {
        this.rankChange = rankChange;
    }

    public Long getCurrentViewCount() {
        return currentViewCount;
    }

    public void setCurrentViewCount(Long currentViewCount) {
        this.currentViewCount = currentViewCount;
    }

    public Long getPreviousViewCount() {
        return previousViewCount;
    }

    public void setPreviousViewCount(Long previousViewCount) {
        this.previousViewCount = previousViewCount;
    }

    public Long getViewCountDelta() {
        return viewCountDelta;
    }

    public void setViewCountDelta(Long viewCountDelta) {
        this.viewCountDelta = viewCountDelta;
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean aNew) {
        isNew = aNew;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
