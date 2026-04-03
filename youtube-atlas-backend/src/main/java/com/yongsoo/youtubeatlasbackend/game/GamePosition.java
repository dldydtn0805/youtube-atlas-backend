package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_positions")
public class GamePosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "category_id", nullable = false, length = 20)
    private String categoryId;

    @Column(name = "video_id", nullable = false, length = 50)
    private String videoId;

    @Column(nullable = false)
    private String title;

    @Column(name = "channel_title", nullable = false)
    private String channelTitle;

    @Column(name = "thumbnail_url", nullable = false, length = 2000)
    private String thumbnailUrl;

    @Column(name = "buy_run_id", nullable = false)
    private Long buyRunId;

    @Column(name = "buy_rank", nullable = false)
    private Integer buyRank;

    @Column(name = "buy_captured_at", nullable = false)
    private Instant buyCapturedAt;

    @Column(name = "stake_points", nullable = false)
    private Long stakePoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionStatus status;

    @Column(name = "sell_run_id")
    private Long sellRunId;

    @Column(name = "sell_rank")
    private Integer sellRank;

    @Column(name = "sell_captured_at")
    private Instant sellCapturedAt;

    @Column(name = "rank_diff")
    private Integer rankDiff;

    @Column(name = "pnl_points")
    private Long pnlPoints;

    @Column(name = "settled_points")
    private Long settledPoints;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public Long getId() {
        return id;
    }

    public GameSeason getSeason() {
        return season;
    }

    public void setSeason(GameSeason season) {
        this.season = season;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
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

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
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

    public Long getBuyRunId() {
        return buyRunId;
    }

    public void setBuyRunId(Long buyRunId) {
        this.buyRunId = buyRunId;
    }

    public Integer getBuyRank() {
        return buyRank;
    }

    public void setBuyRank(Integer buyRank) {
        this.buyRank = buyRank;
    }

    public Instant getBuyCapturedAt() {
        return buyCapturedAt;
    }

    public void setBuyCapturedAt(Instant buyCapturedAt) {
        this.buyCapturedAt = buyCapturedAt;
    }

    public Long getStakePoints() {
        return stakePoints;
    }

    public void setStakePoints(Long stakePoints) {
        this.stakePoints = stakePoints;
    }

    public PositionStatus getStatus() {
        return status;
    }

    public void setStatus(PositionStatus status) {
        this.status = status;
    }

    public Long getSellRunId() {
        return sellRunId;
    }

    public void setSellRunId(Long sellRunId) {
        this.sellRunId = sellRunId;
    }

    public Integer getSellRank() {
        return sellRank;
    }

    public void setSellRank(Integer sellRank) {
        this.sellRank = sellRank;
    }

    public Instant getSellCapturedAt() {
        return sellCapturedAt;
    }

    public void setSellCapturedAt(Instant sellCapturedAt) {
        this.sellCapturedAt = sellCapturedAt;
    }

    public Integer getRankDiff() {
        return rankDiff;
    }

    public void setRankDiff(Integer rankDiff) {
        this.rankDiff = rankDiff;
    }

    public Long getPnlPoints() {
        return pnlPoints;
    }

    public void setPnlPoints(Long pnlPoints) {
        this.pnlPoints = pnlPoints;
    }

    public Long getSettledPoints() {
        return settledPoints;
    }

    public void setSettledPoints(Long settledPoints) {
        this.settledPoints = settledPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
