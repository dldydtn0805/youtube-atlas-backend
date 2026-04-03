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
@Table(name = "game_ledger")
public class GameLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private GamePosition position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerType type;

    @Column(name = "amount_points", nullable = false)
    private Long amountPoints;

    @Column(name = "balance_after_points", nullable = false)
    private Long balanceAfterPoints;

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

    public GamePosition getPosition() {
        return position;
    }

    public void setPosition(GamePosition position) {
        this.position = position;
    }

    public LedgerType getType() {
        return type;
    }

    public void setType(LedgerType type) {
        this.type = type;
    }

    public Long getAmountPoints() {
        return amountPoints;
    }

    public void setAmountPoints(Long amountPoints) {
        this.amountPoints = amountPoints;
    }

    public Long getBalanceAfterPoints() {
        return balanceAfterPoints;
    }

    public void setBalanceAfterPoints(Long balanceAfterPoints) {
        this.balanceAfterPoints = balanceAfterPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
