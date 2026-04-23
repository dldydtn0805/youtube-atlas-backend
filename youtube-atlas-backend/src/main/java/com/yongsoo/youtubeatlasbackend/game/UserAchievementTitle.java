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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "user_achievement_titles",
    indexes = {
        @Index(name = "idx_user_achievement_titles_user", columnList = "user_id"),
        @Index(name = "idx_user_achievement_titles_title", columnList = "title_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_achievement_titles_user_title", columnNames = {"user_id", "title_id"})
    }
)
public class UserAchievementTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false)
    private AchievementTitle title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id")
    private GameSeason season;

    @Column(name = "source_highlight_state_id")
    private Long sourceHighlightStateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private AchievementTitleSourceType sourceType;

    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 500)
    private String revokedReason;

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public AchievementTitle getTitle() {
        return title;
    }

    public void setTitle(AchievementTitle title) {
        this.title = title;
    }

    public GameSeason getSeason() {
        return season;
    }

    public void setSeason(GameSeason season) {
        this.season = season;
    }

    public Long getSourceHighlightStateId() {
        return sourceHighlightStateId;
    }

    public void setSourceHighlightStateId(Long sourceHighlightStateId) {
        this.sourceHighlightStateId = sourceHighlightStateId;
    }

    public AchievementTitleSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(AchievementTitleSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public Instant getEarnedAt() {
        return earnedAt;
    }

    public void setEarnedAt(Instant earnedAt) {
        this.earnedAt = earnedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public void setRevokedReason(String revokedReason) {
        this.revokedReason = revokedReason;
    }
}
