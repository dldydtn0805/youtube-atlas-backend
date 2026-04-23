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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "user_achievement_title_settings",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_achievement_title_settings_user", columnNames = "user_id")
    }
)
public class UserAchievementTitleSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_title_id")
    private AchievementTitle selectedTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false, length = 20)
    private AchievementTitleSelectionMode selectionMode = AchievementTitleSelectionMode.AUTO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public AchievementTitle getSelectedTitle() {
        return selectedTitle;
    }

    public void setSelectedTitle(AchievementTitle selectedTitle) {
        this.selectedTitle = selectedTitle;
    }

    public AchievementTitleSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(AchievementTitleSelectionMode selectionMode) {
        this.selectionMode = selectionMode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
