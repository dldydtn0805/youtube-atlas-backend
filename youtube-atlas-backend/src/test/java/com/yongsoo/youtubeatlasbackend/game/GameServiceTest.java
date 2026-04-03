package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.game.api.CreatePositionRequest;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

class GameServiceTest {

    private GameSeasonRepository gameSeasonRepository;
    private GameWalletRepository gameWalletRepository;
    private GamePositionRepository gamePositionRepository;
    private GameLedgerRepository gameLedgerRepository;
    private AppUserRepository appUserRepository;
    private TrendSignalRepository trendSignalRepository;
    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameLedgerRepository = org.mockito.Mockito.mock(GameLedgerRepository.class);
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);

        gameService = new GameService(
            gameSeasonRepository,
            gameWalletRepository,
            gamePositionRepository,
            gameLedgerRepository,
            appUserRepository,
            trendSignalRepository,
            trendRunRepository,
            trendSnapshotRepository,
            fixedClock
        );
    }

    @Test
    void getCurrentSeasonCreatesWalletWhenMissing() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);

        when(gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)).thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gameWalletRepository.save(any(GameWallet.class))).thenAnswer(invocation -> {
            GameWallet wallet = invocation.getArgument(0, GameWallet.class);
            ReflectionTestUtils.setField(wallet, "id", 100L);
            return wallet;
        });
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.getCurrentSeason(authenticatedUser());

        assertThat(response.seasonId()).isEqualTo(1L);
        assertThat(response.wallet().balancePoints()).isEqualTo(10_000L);
        assertThat(response.wallet().reservedPoints()).isZero();
        verify(gameLedgerRepository).save(any(GameLedger.class));
    }

    @Test
    void buyCreatesOpenPositionAndLocksStakePoints() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendSignal signal = signal("video-1", 12, 3);

        when(gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)).thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(0L);
        when(gamePositionRepository.existsBySeasonIdAndUserIdAndVideoIdAndStatus(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(false);
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition position = invocation.getArgument(0, GamePosition.class);
            ReflectionTestUtils.setField(position, "id", 200L);
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(authenticatedUser(), new CreatePositionRequest("KR", "0", "video-1", 2_000L));

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.buyRank()).isEqualTo(12);
        assertThat(response.currentRank()).isEqualTo(12);
        assertThat(response.stakePoints()).isEqualTo(2_000L);
        assertThat(wallet.getBalancePoints()).isEqualTo(8_000L);
        assertThat(wallet.getReservedPoints()).isEqualTo(2_000L);
    }

    @Test
    void sellSettlesProfitBasedOnRankDiff() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 8_000L, 2_000L, 0L);
        GamePosition position = openPosition(season, appUser, "video-1", 20, 2_000L, Instant.parse("2026-04-01T05:45:00Z"));
        TrendSignal latestSignal = signal("video-1", 8, 5);

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(response.sellRank()).isEqualTo(8);
        assertThat(response.rankDiff()).isEqualTo(12);
        assertThat(response.pnlPoints()).isEqualTo(2_400L);
        assertThat(response.settledPoints()).isEqualTo(4_400L);
        assertThat(response.balancePoints()).isEqualTo(12_400L);
        assertThat(wallet.getReservedPoints()).isZero();
        assertThat(wallet.getRealizedPnlPoints()).isEqualTo(2_400L);
    }

    @Test
    void sellUsesFallbackRankWhenLatestRunLooksHealthy() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 8_000L, 2_000L, 0L);
        GamePosition position = openPosition(season, appUser, "video-1", 20, 2_000L, Instant.parse("2026-04-01T05:45:00Z"));
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-01T05:00:00Z"));

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.empty());
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0")).thenReturn(Optional.of(latestRun));
        when(trendSnapshotRepository.findByRunId(55L)).thenReturn(List.of(
            snapshot(latestRun, "video-2", 8),
            snapshot(latestRun, "video-3", 12)
        ));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc("KR", "0", 55L))
            .thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(54L)).thenReturn(List.of(
            snapshot(previousRun, "video-1", 19),
            snapshot(previousRun, "video-2", 7),
            snapshot(previousRun, "video-3", 11)
        ));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(response.sellRank()).isEqualTo(13);
        assertThat(response.rankDiff()).isEqualTo(7);
        assertThat(response.pnlPoints()).isEqualTo(1_400L);
        assertThat(response.settledPoints()).isEqualTo(3_400L);
        assertThat(response.balancePoints()).isEqualTo(11_400L);
    }

    @Test
    void sellRejectsWhenLatestRunLooksUnstable() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 8_000L, 2_000L, 0L);
        GamePosition position = openPosition(season, appUser, "video-1", 20, 2_000L, Instant.parse("2026-04-01T05:45:00Z"));
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-01T05:00:00Z"));

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.empty());
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0")).thenReturn(Optional.of(latestRun));
        when(trendSnapshotRepository.findByRunId(55L)).thenReturn(List.of(
            snapshot(latestRun, "video-2", 8),
            snapshot(latestRun, "video-3", 12)
        ));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc("KR", "0", 55L))
            .thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(54L)).thenReturn(List.of(
            snapshot(previousRun, "video-1", 19),
            snapshot(previousRun, "video-2", 7),
            snapshot(previousRun, "video-3", 11),
            snapshot(previousRun, "video-4", 15),
            snapshot(previousRun, "video-5", 16)
        ));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> gameService.sell(authenticatedUser(), 300L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("현재 랭킹 동기화 상태를 확인할 수 없어 수동 매도할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void sellRejectsBeforeMinimumHoldTime() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(season, appUser, "video-1", 20, 1_000L, Instant.parse("2026-04-01T05:55:30Z"));

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> gameService.sell(authenticatedUser(), 300L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("최소 보유 시간이 지나야 매도할 수 있습니다.");

        verify(trendSignalRepository, never()).findById(any());
    }

    @Test
    void getMarketMarksOwnedVideoAsNotBuyable() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendSignal ownedSignal = signal("video-1", 1, 2);
        TrendSignal openSignal = signal("video-2", 2, 1);

        when(gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)).thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(1L);
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(ownedSignal, openSignal));
        when(gamePositionRepository.existsBySeasonIdAndUserIdAndVideoIdAndStatus(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(true);
        when(gamePositionRepository.existsBySeasonIdAndUserIdAndVideoIdAndStatus(1L, 7L, "video-2", PositionStatus.OPEN))
            .thenReturn(false);

        var response = gameService.getMarket(authenticatedUser());

        assertThat(response).hasSize(2);
        assertThat(response.get(0).videoId()).isEqualTo("video-1");
        assertThat(response.get(0).canBuy()).isFalse();
        assertThat(response.get(0).buyBlockedReason()).isEqualTo("이미 보유 중인 영상입니다.");
        assertThat(response.get(1).videoId()).isEqualTo("video-2");
        assertThat(response.get(1).canBuy()).isTrue();
        assertThat(response.get(1).buyBlockedReason()).isNull();
    }

    @Test
    void getLeaderboardOrdersByMarkedToMarketTotalAssets() {
        GameSeason season = activeSeason();
        AppUser me = user(7L, "Atlas User");
        AppUser rival = user(8L, "Rival User");
        GameWallet myWallet = wallet(season, me, 8_000L, 2_000L, 0L);
        GameWallet rivalWallet = wallet(season, rival, 7_500L, 2_000L, 1_000L);
        GamePosition myPosition = openPosition(season, me, "video-1", 20, 2_000L, Instant.parse("2026-04-01T05:45:00Z"));
        GamePosition rivalPosition = openPosition(season, rival, "video-2", 10, 2_000L, Instant.parse("2026-04-01T05:40:00Z"));
        TrendSignal mySignal = signal("video-1", 8, 3);
        TrendSignal rivalSignal = signal("video-2", 14, -4);

        when(gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)).thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(myWallet));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(mySignal, rivalSignal));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN))
            .thenReturn(List.of(myPosition, rivalPosition));
        when(gameWalletRepository.findBySeasonId(1L)).thenReturn(List.of(myWallet, rivalWallet));

        var response = gameService.getLeaderboard(authenticatedUser());

        assertThat(response).hasSize(2);
        assertThat(response.get(0).userId()).isEqualTo(7L);
        assertThat(response.get(0).rank()).isEqualTo(1);
        assertThat(response.get(0).totalAssetPoints()).isEqualTo(12_400L);
        assertThat(response.get(0).unrealizedPnlPoints()).isEqualTo(2_400L);
        assertThat(response.get(0).me()).isTrue();
        assertThat(response.get(1).userId()).isEqualTo(8L);
        assertThat(response.get(1).rank()).isEqualTo(2);
        assertThat(response.get(1).totalAssetPoints()).isEqualTo(8_700L);
        assertThat(response.get(1).unrealizedPnlPoints()).isEqualTo(-800L);
        assertThat(response.get(1).me()).isFalse();
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null);
    }

    private AppUser user(Long id) {
        AppUser appUser = new AppUser();
        ReflectionTestUtils.setField(appUser, "id", id);
        appUser.setDisplayName("User " + id);
        return appUser;
    }

    private AppUser user(Long id, String displayName) {
        AppUser appUser = user(id);
        appUser.setDisplayName(displayName);
        return appUser;
    }

    private GameSeason activeSeason() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        season.setName("KR Daily Season");
        season.setStatus(SeasonStatus.ACTIVE);
        season.setRegionCode("KR");
        season.setStartAt(Instant.parse("2026-04-01T00:00:00Z"));
        season.setEndAt(Instant.parse("2026-04-08T00:00:00Z"));
        season.setStartingBalancePoints(10_000L);
        season.setMinHoldSeconds(600);
        season.setMaxOpenPositions(5);
        season.setRankPointMultiplier(100);
        season.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return season;
    }

    private GameWallet wallet(GameSeason season, AppUser appUser, long balance, long reserved, long realizedPnl) {
        GameWallet wallet = new GameWallet();
        ReflectionTestUtils.setField(wallet, "id", 10L);
        wallet.setSeason(season);
        wallet.setUser(appUser);
        wallet.setBalancePoints(balance);
        wallet.setReservedPoints(reserved);
        wallet.setRealizedPnlPoints(realizedPnl);
        wallet.setUpdatedAt(Instant.parse("2026-04-01T05:30:00Z"));
        return wallet;
    }

    private GamePosition openPosition(
        GameSeason season,
        AppUser appUser,
        String videoId,
        int buyRank,
        long stakePoints,
        Instant createdAt
    ) {
        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", 300L);
        position.setSeason(season);
        position.setUser(appUser);
        position.setRegionCode("KR");
        position.setCategoryId("0");
        position.setVideoId(videoId);
        position.setTitle("Title " + videoId);
        position.setChannelTitle("Channel");
        position.setThumbnailUrl("https://example.com/" + videoId + ".jpg");
        position.setBuyRunId(11L);
        position.setBuyRank(buyRank);
        position.setBuyCapturedAt(Instant.parse("2026-04-01T05:40:00Z"));
        position.setStakePoints(stakePoints);
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(createdAt);
        return position;
    }

    private TrendSignal signal(String videoId, int currentRank, int rankChange) {
        TrendSignal signal = new TrendSignal();
        signal.setId(new TrendSignalId("KR", "0", videoId));
        signal.setCategoryLabel("전체");
        signal.setCurrentRunId(55L);
        signal.setPreviousRunId(54L);
        signal.setCurrentRank(currentRank);
        signal.setPreviousRank(currentRank + rankChange);
        signal.setRankChange(rankChange);
        signal.setCurrentViewCount(5_000L);
        signal.setPreviousViewCount(4_500L);
        signal.setViewCountDelta(500L);
        signal.setNew(false);
        signal.setTitle("Title " + videoId);
        signal.setChannelTitle("Channel");
        signal.setChannelId("channel-1");
        signal.setThumbnailUrl("https://example.com/" + videoId + ".jpg");
        signal.setCapturedAt(Instant.parse("2026-04-01T06:00:00Z"));
        signal.setUpdatedAt(Instant.parse("2026-04-01T06:00:00Z"));
        return signal;
    }

    private TrendRun trendRun(Long id, Instant capturedAt) {
        TrendRun run = new TrendRun();
        ReflectionTestUtils.setField(run, "id", id);
        run.setRegionCode("KR");
        run.setCategoryId("0");
        run.setCategoryLabel("전체");
        run.setSourceCategoryIds(List.of());
        run.setSource("youtube-mostPopular");
        run.setCapturedAt(capturedAt);
        return run;
    }

    private TrendSnapshot snapshot(TrendRun run, String videoId, int rank) {
        TrendSnapshot snapshot = new TrendSnapshot();
        snapshot.setRun(run);
        snapshot.setRegionCode("KR");
        snapshot.setCategoryId("0");
        snapshot.setVideoId(videoId);
        snapshot.setRank(rank);
        snapshot.setTitle("Title " + videoId);
        snapshot.setChannelTitle("Channel");
        snapshot.setChannelId("channel-1");
        snapshot.setThumbnailUrl("https://example.com/" + videoId + ".jpg");
        snapshot.setViewCount(5_000L);
        snapshot.setCreatedAt(run.getCapturedAt());
        return snapshot;
    }
}
