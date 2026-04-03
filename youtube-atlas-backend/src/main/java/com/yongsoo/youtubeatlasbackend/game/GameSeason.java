package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_seasons")
public class GameSeason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeasonStatus status;

    @Column(name = "region_code", nullable = false, length = 10)
    private String regionCode;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "starting_balance_points", nullable = false)
    private Long startingBalancePoints;

    @Column(name = "min_hold_seconds", nullable = false)
    private Integer minHoldSeconds;

    @Column(name = "max_open_positions", nullable = false)
    private Integer maxOpenPositions;

    @Column(name = "rank_point_multiplier", nullable = false)
    private Integer rankPointMultiplier;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SeasonStatus getStatus() {
        return status;
    }

    public void setStatus(SeasonStatus status) {
        this.status = status;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public void setStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public void setEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public Long getStartingBalancePoints() {
        return startingBalancePoints;
    }

    public void setStartingBalancePoints(Long startingBalancePoints) {
        this.startingBalancePoints = startingBalancePoints;
    }

    public Integer getMinHoldSeconds() {
        return minHoldSeconds;
    }

    public void setMinHoldSeconds(Integer minHoldSeconds) {
        this.minHoldSeconds = minHoldSeconds;
    }

    public Integer getMaxOpenPositions() {
        return maxOpenPositions;
    }

    public void setMaxOpenPositions(Integer maxOpenPositions) {
        this.maxOpenPositions = maxOpenPositions;
    }

    public Integer getRankPointMultiplier() {
        return rankPointMultiplier;
    }

    public void setRankPointMultiplier(Integer rankPointMultiplier) {
        this.rankPointMultiplier = rankPointMultiplier;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
