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
    name = "game_wallets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_wallets_season_user", columnNames = {"season_id", "user_id"})
    }
)
public class GameWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private GameSeason season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "balance_points", nullable = false)
    private Long balancePoints;

    @Column(name = "reserved_points", nullable = false)
    private Long reservedPoints;

    @Column(name = "realized_pnl_points", nullable = false)
    private Long realizedPnlPoints;

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

    public Long getBalancePoints() {
        return balancePoints;
    }

    public void setBalancePoints(Long balancePoints) {
        this.balancePoints = balancePoints;
    }

    public Long getReservedPoints() {
        return reservedPoints;
    }

    public void setReservedPoints(Long reservedPoints) {
        this.reservedPoints = reservedPoints;
    }

    public Long getRealizedPnlPoints() {
        return realizedPnlPoints;
    }

    public void setRealizedPnlPoints(Long realizedPnlPoints) {
        this.realizedPnlPoints = realizedPnlPoints;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
