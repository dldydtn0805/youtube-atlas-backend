package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "game_highlight_states",
    indexes = {
        @Index(name = "idx_game_highlight_states_season_user", columnList = "season_id, user_id"),
        @Index(name = "idx_game_highlight_states_season_score", columnList = "season_id, best_settled_highlight_score")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_highlight_states_root", columnNames = {"season_id", "user_id", "root_position_id"})
    }
)
public class GameHighlightState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "root_position_id", nullable = false)
    private Long rootPositionId;

    @Column(name = "best_settled_position_id")
    private Long bestSettledPositionId;

    @Column(name = "best_settled_highlight_type", length = 40)
    private String bestSettledHighlightType;

    @Column(name = "best_settled_title", length = 120)
    private String bestSettledTitle;

    @Column(name = "best_settled_description", length = 1000)
    private String bestSettledDescription;

    @Column(name = "video_id", length = 50)
    private String videoId;

    @Column(name = "video_title")
    private String videoTitle;

    @Column(name = "channel_title")
    private String channelTitle;

    @Column(name = "thumbnail_url", length = 2000)
    private String thumbnailUrl;

    @Column(name = "buy_rank")
    private Integer buyRank;

    @Column(name = "highlight_rank")
    private Integer highlightRank;

    @Column(name = "sell_rank")
    private Integer sellRank;

    @Column(name = "rank_diff")
    private Integer rankDiff;

    @Column(name = "quantity")
    private Long quantity;

    @Column(name = "stake_points")
    private Long stakePoints;

    @Column(name = "current_price_points")
    private Long currentPricePoints;

    @Column(name = "profit_points")
    private Long profitPoints;

    @Column(name = "profit_rate_percent")
    private Double profitRatePercent;

    @Column(name = "strategy_tags", length = 200)
    private String strategyTags;

    @Column(name = "best_settled_highlight_score", nullable = false)
    private Long bestSettledHighlightScore = 0L;

    @Column(name = "best_settled_created_at")
    private Instant bestSettledCreatedAt;

    @Column(name = "best_projected_notification_score", nullable = false)
    private Long bestProjectedNotificationScore = 0L;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public Long getRootPositionId() {
        return rootPositionId;
    }

    public void setRootPositionId(Long rootPositionId) {
        this.rootPositionId = rootPositionId;
    }

    public Long getBestSettledPositionId() {
        return bestSettledPositionId;
    }

    public void setBestSettledPositionId(Long bestSettledPositionId) {
        this.bestSettledPositionId = bestSettledPositionId;
    }

    public String getBestSettledHighlightType() {
        return bestSettledHighlightType;
    }

    public void setBestSettledHighlightType(String bestSettledHighlightType) {
        this.bestSettledHighlightType = bestSettledHighlightType;
    }

    public String getBestSettledTitle() {
        return bestSettledTitle;
    }

    public void setBestSettledTitle(String bestSettledTitle) {
        this.bestSettledTitle = bestSettledTitle;
    }

    public String getBestSettledDescription() {
        return bestSettledDescription;
    }

    public void setBestSettledDescription(String bestSettledDescription) {
        this.bestSettledDescription = bestSettledDescription;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getVideoTitle() {
        return videoTitle;
    }

    public void setVideoTitle(String videoTitle) {
        this.videoTitle = videoTitle;
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

    public Integer getBuyRank() {
        return buyRank;
    }

    public void setBuyRank(Integer buyRank) {
        this.buyRank = buyRank;
    }

    public Integer getHighlightRank() {
        return highlightRank;
    }

    public void setHighlightRank(Integer highlightRank) {
        this.highlightRank = highlightRank;
    }

    public Integer getSellRank() {
        return sellRank;
    }

    public void setSellRank(Integer sellRank) {
        this.sellRank = sellRank;
    }

    public Integer getRankDiff() {
        return rankDiff;
    }

    public void setRankDiff(Integer rankDiff) {
        this.rankDiff = rankDiff;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public Long getStakePoints() {
        return stakePoints;
    }

    public void setStakePoints(Long stakePoints) {
        this.stakePoints = stakePoints;
    }

    public Long getCurrentPricePoints() {
        return currentPricePoints;
    }

    public void setCurrentPricePoints(Long currentPricePoints) {
        this.currentPricePoints = currentPricePoints;
    }

    public Long getProfitPoints() {
        return profitPoints;
    }

    public void setProfitPoints(Long profitPoints) {
        this.profitPoints = profitPoints;
    }

    public Double getProfitRatePercent() {
        return profitRatePercent;
    }

    public void setProfitRatePercent(Double profitRatePercent) {
        this.profitRatePercent = profitRatePercent;
    }

    public String getStrategyTags() {
        return strategyTags;
    }

    public void setStrategyTags(String strategyTags) {
        this.strategyTags = strategyTags;
    }

    public Long getBestSettledHighlightScore() {
        return bestSettledHighlightScore;
    }

    public void setBestSettledHighlightScore(Long bestSettledHighlightScore) {
        this.bestSettledHighlightScore = bestSettledHighlightScore;
    }

    public Instant getBestSettledCreatedAt() {
        return bestSettledCreatedAt;
    }

    public void setBestSettledCreatedAt(Instant bestSettledCreatedAt) {
        this.bestSettledCreatedAt = bestSettledCreatedAt;
    }

    public Long getBestProjectedNotificationScore() {
        return bestProjectedNotificationScore;
    }

    public void setBestProjectedNotificationScore(Long bestProjectedNotificationScore) {
        this.bestProjectedNotificationScore = bestProjectedNotificationScore;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
