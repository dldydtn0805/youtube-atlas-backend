package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;

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
    name = "game_season_coin_tiers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_season_coin_tiers_season_code", columnNames = {"season_id", "tier_code"}),
        @UniqueConstraint(name = "uk_game_season_coin_tiers_season_order", columnNames = {"season_id", "sort_order"})
    }
)
public class GameSeasonCoinTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @Column(name = "tier_code", nullable = false, length = 50)
    private String tierCode;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "min_coin_balance", nullable = false)
    private Long minCoinBalance;

    @Column(name = "badge_code", nullable = false, length = 100)
    private String badgeCode;

    @Column(name = "title_code", nullable = false, length = 100)
    private String titleCode;

    @Column(name = "profile_theme_code", nullable = false, length = 100)
    private String profileThemeCode;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

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

    public String getTierCode() {
        return tierCode;
    }

    public void setTierCode(String tierCode) {
        this.tierCode = tierCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Long getMinCoinBalance() {
        return minCoinBalance;
    }

    public void setMinCoinBalance(Long minCoinBalance) {
        this.minCoinBalance = minCoinBalance;
    }

    public String getBadgeCode() {
        return badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    public String getTitleCode() {
        return titleCode;
    }

    public void setTitleCode(String titleCode) {
        this.titleCode = titleCode;
    }

    public String getProfileThemeCode() {
        return profileThemeCode;
    }

    public void setProfileThemeCode(String profileThemeCode) {
        this.profileThemeCode = profileThemeCode;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
