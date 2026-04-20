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
    name = "game_notifications",
    indexes = {
        @Index(name = "idx_game_notifications_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_game_notifications_season_user", columnList = "season_id, user_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_notifications_user_event", columnNames = {"user_id", "event_key"})
    }
)
public class GameNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @Column(name = "event_key", nullable = false, length = 120)
    private String eventKey;

    @Column(name = "notification_type", nullable = false, length = 40)
    private String notificationType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "position_id")
    private Long positionId;

    @Column(name = "video_id", length = 50)
    private String videoId;

    @Column(name = "video_title")
    private String videoTitle;

    @Column(name = "channel_title")
    private String channelTitle;

    @Column(name = "thumbnail_url", length = 2000)
    private String thumbnailUrl;

    @Column(name = "strategy_tags", length = 200)
    private String strategyTags;

    @Column(name = "highlight_score")
    private Long highlightScore;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public GameSeason getSeason() {
        return season;
    }

    public void setSeason(GameSeason season) {
        this.season = season;
    }

    public String getEventKey() {
        return eventKey;
    }

    public void setEventKey(String eventKey) {
        this.eventKey = eventKey;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getPositionId() {
        return positionId;
    }

    public void setPositionId(Long positionId) {
        this.positionId = positionId;
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

    public String getStrategyTags() {
        return strategyTags;
    }

    public void setStrategyTags(String strategyTags) {
        this.strategyTags = strategyTags;
    }

    public Long getHighlightScore() {
        return highlightScore;
    }

    public void setHighlightScore(Long highlightScore) {
        this.highlightScore = highlightScore;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
