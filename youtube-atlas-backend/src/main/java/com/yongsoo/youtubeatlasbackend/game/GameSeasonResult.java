package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "game_season_results",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_season_results_season_user", columnNames = {"season_id", "user_id"})
    }
)
public class GameSeasonResult {

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

    @Column(name = "season_name", nullable = false, length = 100)
    private String seasonName;

    @Column(name = "season_start_at", nullable = false)
    private Instant seasonStartAt;

    @Column(name = "season_end_at", nullable = false)
    private Instant seasonEndAt;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    @Column(name = "final_asset_points", nullable = false)
    private Long finalAssetPoints;

    @Column(name = "final_balance_points", nullable = false)
    private Long finalBalancePoints;

    @Column(name = "realized_pnl_points", nullable = false)
    private Long realizedPnlPoints;

    @Column(name = "position_count", nullable = false)
    private Long positionCount;

    @Column(name = "best_position_id")
    private Long bestPositionId;

    @Column(name = "best_position_video_id", length = 50)
    private String bestPositionVideoId;

    @Column(name = "best_position_title")
    private String bestPositionTitle;

    @Column(name = "best_position_channel_title")
    private String bestPositionChannelTitle;

    @Column(name = "best_position_thumbnail_url", length = 2000)
    private String bestPositionThumbnailUrl;

    @Column(name = "best_position_profit_points")
    private Long bestPositionProfitPoints;

    @Column(name = "title_code", length = 80)
    private String titleCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

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

    public String getSeasonName() {
        return seasonName;
    }

    public void setSeasonName(String seasonName) {
        this.seasonName = seasonName;
    }

    public Instant getSeasonStartAt() {
        return seasonStartAt;
    }

    public void setSeasonStartAt(Instant seasonStartAt) {
        this.seasonStartAt = seasonStartAt;
    }

    public Instant getSeasonEndAt() {
        return seasonEndAt;
    }

    public void setSeasonEndAt(Instant seasonEndAt) {
        this.seasonEndAt = seasonEndAt;
    }

    public Integer getFinalRank() {
        return finalRank;
    }

    public void setFinalRank(Integer finalRank) {
        this.finalRank = finalRank;
    }

    public Long getFinalAssetPoints() {
        return finalAssetPoints;
    }

    public void setFinalAssetPoints(Long finalAssetPoints) {
        this.finalAssetPoints = finalAssetPoints;
    }

    public Long getFinalBalancePoints() {
        return finalBalancePoints;
    }

    public void setFinalBalancePoints(Long finalBalancePoints) {
        this.finalBalancePoints = finalBalancePoints;
    }

    public Long getRealizedPnlPoints() {
        return realizedPnlPoints;
    }

    public void setRealizedPnlPoints(Long realizedPnlPoints) {
        this.realizedPnlPoints = realizedPnlPoints;
    }

    public Long getPositionCount() {
        return positionCount;
    }

    public void setPositionCount(Long positionCount) {
        this.positionCount = positionCount;
    }

    public Long getBestPositionId() {
        return bestPositionId;
    }

    public void setBestPositionId(Long bestPositionId) {
        this.bestPositionId = bestPositionId;
    }

    public String getBestPositionVideoId() {
        return bestPositionVideoId;
    }

    public void setBestPositionVideoId(String bestPositionVideoId) {
        this.bestPositionVideoId = bestPositionVideoId;
    }

    public String getBestPositionTitle() {
        return bestPositionTitle;
    }

    public void setBestPositionTitle(String bestPositionTitle) {
        this.bestPositionTitle = bestPositionTitle;
    }

    public String getBestPositionChannelTitle() {
        return bestPositionChannelTitle;
    }

    public void setBestPositionChannelTitle(String bestPositionChannelTitle) {
        this.bestPositionChannelTitle = bestPositionChannelTitle;
    }

    public String getBestPositionThumbnailUrl() {
        return bestPositionThumbnailUrl;
    }

    public void setBestPositionThumbnailUrl(String bestPositionThumbnailUrl) {
        this.bestPositionThumbnailUrl = bestPositionThumbnailUrl;
    }

    public Long getBestPositionProfitPoints() {
        return bestPositionProfitPoints;
    }

    public void setBestPositionProfitPoints(Long bestPositionProfitPoints) {
        this.bestPositionProfitPoints = bestPositionProfitPoints;
    }

    public String getTitleCode() {
        return titleCode;
    }

    public void setTitleCode(String titleCode) {
        this.titleCode = titleCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
