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
    name = "game_dividend_payouts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_game_dividend_payouts_season_position_run",
            columnNames = {"season_id", "position_id", "trend_run_id"}
        )
    }
)
public class GameDividendPayout {

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

    @Column(name = "trend_run_id", nullable = false)
    private Long trendRunId;

    @Column(name = "rank_at_payout", nullable = false)
    private Integer rankAtPayout;

    @Column(name = "dividend_rate_basis_points", nullable = false)
    private Integer dividendRateBasisPoints;

    @Column(name = "amount_points", nullable = false)
    private Long amountPoints;

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

    public Long getTrendRunId() {
        return trendRunId;
    }

    public void setTrendRunId(Long trendRunId) {
        this.trendRunId = trendRunId;
    }

    public Integer getRankAtPayout() {
        return rankAtPayout;
    }

    public void setRankAtPayout(Integer rankAtPayout) {
        this.rankAtPayout = rankAtPayout;
    }

    public Integer getDividendRateBasisPoints() {
        return dividendRateBasisPoints;
    }

    public void setDividendRateBasisPoints(Integer dividendRateBasisPoints) {
        this.dividendRateBasisPoints = dividendRateBasisPoints;
    }

    public Long getAmountPoints() {
        return amountPoints;
    }

    public void setAmountPoints(Long amountPoints) {
        this.amountPoints = amountPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
