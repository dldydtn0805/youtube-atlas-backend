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
    name = "game_season_coin_results",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_season_coin_results_season_user", columnNames = {"season_id", "user_id"})
    }
)
public class GameSeasonCoinResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "final_coin_balance", nullable = false)
    private Long finalCoinBalance;

    @Column(name = "final_tier_code", nullable = false, length = 50)
    private String finalTierCode;

    @Column(name = "final_tier_display_name", nullable = false, length = 100)
    private String finalTierDisplayName;

    @Column(name = "final_tier_min_coin_balance", nullable = false)
    private Long finalTierMinCoinBalance;

    @Column(name = "badge_code", nullable = false, length = 100)
    private String badgeCode;

    @Column(name = "title_code", nullable = false, length = 100)
    private String titleCode;

    @Column(name = "profile_theme_code", nullable = false, length = 100)
    private String profileThemeCode;

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

    public Long getFinalCoinBalance() {
        return finalCoinBalance;
    }

    public void setFinalCoinBalance(Long finalCoinBalance) {
        this.finalCoinBalance = finalCoinBalance;
    }

    public String getFinalTierCode() {
        return finalTierCode;
    }

    public void setFinalTierCode(String finalTierCode) {
        this.finalTierCode = finalTierCode;
    }

    public String getFinalTierDisplayName() {
        return finalTierDisplayName;
    }

    public void setFinalTierDisplayName(String finalTierDisplayName) {
        this.finalTierDisplayName = finalTierDisplayName;
    }

    public Long getFinalTierMinCoinBalance() {
        return finalTierMinCoinBalance;
    }

    public void setFinalTierMinCoinBalance(Long finalTierMinCoinBalance) {
        this.finalTierMinCoinBalance = finalTierMinCoinBalance;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
