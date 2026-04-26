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
@Table(name = "game_scheduled_sell_orders")
public class GameScheduledSellOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private GamePosition position;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "target_rank", nullable = false)
    private Integer targetRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_direction", nullable = false, length = 30)
    private ScheduledSellTriggerDirection triggerDirection = ScheduledSellTriggerDirection.RANK_IMPROVES_TO;

    @Column(nullable = false)
    private Long quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduledSellOrderStatus status;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "failed_reason", length = 500)
    private String failedReason;

    @Column(name = "sell_price_points")
    private Long sellPricePoints;

    @Column(name = "settled_points")
    private Long settledPoints;

    @Column(name = "pnl_points")
    private Long pnlPoints;

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

    public GamePosition getPosition() {
        return position;
    }

    public void setPosition(GamePosition position) {
        this.position = position;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public Integer getTargetRank() {
        return targetRank;
    }

    public void setTargetRank(Integer targetRank) {
        this.targetRank = targetRank;
    }

    public ScheduledSellTriggerDirection getTriggerDirection() {
        return triggerDirection;
    }

    public void setTriggerDirection(ScheduledSellTriggerDirection triggerDirection) {
        this.triggerDirection = triggerDirection;
    }

    public Long getQuantity() {
        return quantity;
    }

    public void setQuantity(Long quantity) {
        this.quantity = quantity;
    }

    public ScheduledSellOrderStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduledSellOrderStatus status) {
        this.status = status;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public void setCanceledAt(Instant canceledAt) {
        this.canceledAt = canceledAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public void setFailedAt(Instant failedAt) {
        this.failedAt = failedAt;
    }

    public String getFailedReason() {
        return failedReason;
    }

    public void setFailedReason(String failedReason) {
        this.failedReason = failedReason;
    }

    public Long getSellPricePoints() {
        return sellPricePoints;
    }

    public void setSellPricePoints(Long sellPricePoints) {
        this.sellPricePoints = sellPricePoints;
    }

    public Long getSettledPoints() {
        return settledPoints;
    }

    public void setSettledPoints(Long settledPoints) {
        this.settledPoints = settledPoints;
    }

    public Long getPnlPoints() {
        return pnlPoints;
    }

    public void setPnlPoints(Long pnlPoints) {
        this.pnlPoints = pnlPoints;
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
