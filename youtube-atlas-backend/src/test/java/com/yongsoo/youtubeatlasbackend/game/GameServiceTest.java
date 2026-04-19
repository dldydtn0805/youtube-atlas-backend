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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.game.api.CreatePositionRequest;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionsRequest;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

class GameServiceTest {

    private static final int ONE_SHARE = GamePointCalculator.QUANTITY_SCALE;
    private static final int TWO_SHARES = ONE_SHARE * 2;
    private static final int THREE_SHARES = ONE_SHARE * 3;

    private GameSeasonRepository gameSeasonRepository;
    private GameWalletRepository gameWalletRepository;
    private GamePositionRepository gamePositionRepository;
    private GameLedgerRepository gameLedgerRepository;
    private GameSeasonCoinResultRepository gameSeasonCoinResultRepository;
    private GameCoinTierService gameCoinTierService;
    private AppUserRepository appUserRepository;
    private TrendSignalRepository trendSignalRepository;
    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private CommentService commentService;
    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameLedgerRepository = org.mockito.Mockito.mock(GameLedgerRepository.class);
        gameSeasonCoinResultRepository = org.mockito.Mockito.mock(GameSeasonCoinResultRepository.class);
        gameCoinTierService = org.mockito.Mockito.mock(GameCoinTierService.class);
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        commentService = org.mockito.Mockito.mock(CommentService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);
        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getTrending().setCaptureSlotMinutes(5);

        gameService = new GameService(
            gameSeasonRepository,
            gameWalletRepository,
            gamePositionRepository,
            gameLedgerRepository,
            gameSeasonCoinResultRepository,
            gameCoinTierService,
            appUserRepository,
            trendSignalRepository,
            trendRunRepository,
            trendSnapshotRepository,
            commentService,
            fixedClock,
            atlasProperties
        );
    }

    @Test
    void getCurrentSeasonCreatesWalletWhenMissing() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.empty());
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gameWalletRepository.saveAndFlush(any(GameWallet.class))).thenAnswer(invocation -> {
            GameWallet wallet = invocation.getArgument(0, GameWallet.class);
            ReflectionTestUtils.setField(wallet, "id", 100L);
            return wallet;
        });
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.getCurrentSeason(authenticatedUser(), "KR");

        assertThat(response.seasonId()).isEqualTo(1L);
        assertThat(response.wallet().balancePoints()).isEqualTo(10_000L);
        assertThat(response.wallet().reservedPoints()).isZero();
        assertThat(response.wallet().coinBalance()).isZero();
        verify(gameLedgerRepository).save(any(GameLedger.class));
    }

    @Test
    void buyCreatesOpenPositionAndLocksStakePoints() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendSignal signal = signal("video-1", 170, 0);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of());
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(0L);
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition position = invocation.getArgument(0, GamePosition.class);
            ReflectionTestUtils.setField(position, "id", 200L);
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, ONE_SHARE)
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(200L);
        assertThat(response.get(0).buyRank()).isEqualTo(170);
        assertThat(response.get(0).currentRank()).isEqualTo(170);
        assertThat(response.get(0).quantity()).isEqualTo(ONE_SHARE);
        assertThat(response.get(0).stakePoints()).isEqualTo(buyPricePoints);
        assertThat(response.get(0).currentPricePoints()).isEqualTo(buyPricePoints);
        assertThat(wallet.getBalancePoints()).isEqualTo(10_000L - buyPricePoints);
        assertThat(wallet.getReservedPoints()).isEqualTo(buyPricePoints);
        verify(commentService).publishTradeSystemMessage("User 7님이 [Title video-1] 1개를 매수했습니다. (7500P)");
    }

    @Test
    void buyCreatesSeparateOpenPositionForAdditionalQuantityOnSameVideo() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, 20_000L, buyPricePoints, 0L);
        TrendSignal signal = signal("video-1", 170, 0);
        GamePosition existingPosition = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        ReflectionTestUtils.setField(existingPosition, "id", 201L);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of(existingPosition));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(1L);
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition position = invocation.getArgument(0, GamePosition.class);
            ReflectionTestUtils.setField(position, "id", 202L);
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, ONE_SHARE)
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(202L);
        assertThat(response.get(0).videoId()).isEqualTo("video-1");
        assertThat(response.get(0).quantity()).isEqualTo(ONE_SHARE);
        assertThat(response.get(0).stakePoints()).isEqualTo(buyPricePoints);
        assertThat(wallet.getBalancePoints()).isEqualTo(20_000L - buyPricePoints);
        assertThat(wallet.getReservedPoints()).isEqualTo(buyPricePoints * 2);
        assertThat(existingPosition.getCreatedAt()).isEqualTo(Instant.parse("2026-04-01T05:45:00Z"));
    }

    @Test
    void buyCreatesMultiplePositionsWhenQuantityIsProvided() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, 30_000L, 0L, 0L);
        TrendSignal signal = signal("video-1", 170, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of());
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(0L);
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition position = invocation.getArgument(0, GamePosition.class);
            ReflectionTestUtils.setField(position, "id", 300L + wallet.getReservedPoints() + position.getStakePoints());
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, THREE_SHARES)
        );

        assertThat(response).hasSize(1);
        assertThat(response).extracting(position -> position.videoId()).containsOnly("video-1");
        assertThat(response.get(0).quantity()).isEqualTo(THREE_SHARES);
        assertThat(response.get(0).stakePoints()).isEqualTo(buyPricePoints * 3);
        assertThat(wallet.getBalancePoints()).isEqualTo(30_000L - (buyPricePoints * 3));
        assertThat(wallet.getReservedPoints()).isEqualTo(buyPricePoints * 3);
    }

    @Test
    void sellSettlesProfitBasedOnRankPrice() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long sellPricePoints = GamePointCalculator.calculatePricePoints(160);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(buyPricePoints, settledPoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 160, 0);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(response.sellRank()).isEqualTo(160);
        assertThat(response.sellPricePoints()).isEqualTo(sellPricePoints);
        assertThat(response.rankDiff()).isEqualTo(10);
        assertThat(response.pnlPoints()).isEqualTo(pnlPoints);
        assertThat(response.settledPoints()).isEqualTo(settledPoints);
        assertThat(response.balancePoints()).isEqualTo((10_000L - buyPricePoints) + settledPoints);
        assertThat(wallet.getReservedPoints()).isZero();
        assertThat(wallet.getRealizedPnlPoints()).isEqualTo(pnlPoints);
        verify(commentService).publishTradeSystemMessage("User 7님이 [Title video-1] 1개를 매도했습니다. (9970P)");
    }

    @Test
    void sellSettlesLossWhenRankFalls() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(160);
        long sellPricePoints = GamePointCalculator.calculatePricePoints(170);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(buyPricePoints, settledPoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            160,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 170, 0);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(sellPricePoints).isLessThan(buyPricePoints);
        assertThat(response.sellRank()).isEqualTo(170);
        assertThat(response.sellPricePoints()).isEqualTo(sellPricePoints);
        assertThat(response.rankDiff()).isEqualTo(-10);
        assertThat(response.pnlPoints()).isEqualTo(pnlPoints).isNegative();
        assertThat(response.settledPoints()).isEqualTo(settledPoints);
        assertThat(response.balancePoints()).isEqualTo((10_000L - buyPricePoints) + settledPoints);
        assertThat(wallet.getReservedPoints()).isZero();
        assertThat(wallet.getRealizedPnlPoints()).isEqualTo(pnlPoints);
    }

    @Test
    void sellAppliesRealtimeSurgingPremiumToSellPrice() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(171);
        long baseSellPricePoints = GamePointCalculator.calculatePricePoints(171);
        long premiumSellPricePoints = GamePointCalculator.calculateMomentumAdjustedPricePoints(171, 20);
        long settledPoints = GamePointCalculator.calculateSettledPoints(premiumSellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(buyPricePoints, settledPoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            171,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 171, 20);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(premiumSellPricePoints).isGreaterThan(baseSellPricePoints);
        assertThat(response.sellRank()).isEqualTo(171);
        assertThat(response.sellPricePoints()).isEqualTo(premiumSellPricePoints);
        assertThat(response.pnlPoints()).isEqualTo(pnlPoints);
        assertThat(response.settledPoints()).isEqualTo(settledPoints);
    }

    @Test
    void sellByQuantityClosesOldestEligiblePositionsFirst() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long sellPricePoints = GamePointCalculator.calculatePricePoints(160);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - (buyPricePoints * 3), buyPricePoints * 3, 0L);
        GamePosition firstPosition = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        GamePosition secondPosition = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        GamePosition thirdPosition = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:50:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 160, 0);

        ReflectionTestUtils.setField(firstPosition, "id", 301L);
        ReflectionTestUtils.setField(secondPosition, "id", 302L);
        ReflectionTestUtils.setField(thirdPosition, "id", 303L);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of(firstPosition, secondPosition, thirdPosition));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), new SellPositionsRequest("KR", null, "video-1", TWO_SHARES));

        assertThat(response).hasSize(2);
        assertThat(response).extracting(responseItem -> responseItem.positionId()).containsExactly(301L, 302L);
        assertThat(firstPosition.getStatus()).isEqualTo(PositionStatus.CLOSED);
        assertThat(secondPosition.getStatus()).isEqualTo(PositionStatus.CLOSED);
        assertThat(thirdPosition.getStatus()).isEqualTo(PositionStatus.OPEN);
        assertThat(wallet.getReservedPoints()).isEqualTo(buyPricePoints);
        assertThat(wallet.getBalancePoints()).isEqualTo((10_000L - (buyPricePoints * 3)) + (settledPoints * 2));
    }

    @Test
    void sellUsesFallbackRankWhenLatestRunLooksHealthy() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long sellPricePoints = GamePointCalculator.calculateChartOutUnitPricePoints();
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(buyPricePoints, settledPoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-01T05:00:00Z"));

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
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
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(response.sellRank()).isEqualTo(13);
        assertThat(response.sellPricePoints()).isEqualTo(sellPricePoints);
        assertThat(response.rankDiff()).isEqualTo(157);
        assertThat(response.pnlPoints()).isEqualTo(pnlPoints);
        assertThat(response.settledPoints()).isEqualTo(settledPoints);
        assertThat(response.balancePoints()).isEqualTo((10_000L - buyPricePoints) + settledPoints);
    }

    @Test
    void getMyPositionsUsesDiscountedFloorPriceWhenPositionIsChartOut() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long currentPricePoints = GamePointCalculator.calculateChartOutUnitPricePoints();
        long profitPoints = GamePointCalculator.calculateProfitPoints(buyPricePoints, currentPricePoints);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-01T05:00:00Z"));

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
            1L,
            7L,
            PositionStatus.OPEN,
            PageRequest.of(0, 10)
        ))
            .thenReturn(List.of(position));
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

        var response = gameService.getMyPositions(authenticatedUser(), "KR", "OPEN", 10);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).chartOut()).isTrue();
        assertThat(response.get(0).currentRank()).isEqualTo(13);
        assertThat(response.get(0).currentPricePoints()).isEqualTo(currentPricePoints);
        assertThat(response.get(0).profitPoints()).isEqualTo(profitPoints);
    }

    @Test
    void sellRejectsWhenLatestRunLooksUnstable() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-01T05:00:00Z"));

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
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
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> gameService.sell(authenticatedUser(), 300L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("현재 랭킹 동기화 상태를 확인할 수 없어 수동 매도할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void sellRejectsBeforeMinimumHoldTime() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            GamePointCalculator.calculatePricePoints(170),
            Instant.parse("2026-04-01T05:55:30Z")
        );

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> gameService.sell(authenticatedUser(), 300L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("최소 보유 시간이 지나야 매도할 수 있습니다.");

        verify(trendSignalRepository, never()).findById(any());
    }

    @Test
    void getMarketKeepsOwnedVideoBuyableWhenSlotsRemain() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendSignal ownedSignal = signal("video-1", 170, 0);
        TrendSignal openSignal = signal("video-2", 180, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(1L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of(openPosition(season, appUser, "video-1", 170, GamePointCalculator.calculatePricePoints(170), Instant.parse("2026-04-01T05:45:00Z"))));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(ownedSignal, openSignal));

        var response = gameService.getMarket(authenticatedUser(), "KR");

        assertThat(response).hasSize(2);
        assertThat(response.get(0).videoId()).isEqualTo("video-1");
        assertThat(response.get(0).basePricePoints()).isEqualTo(GamePointCalculator.calculatePricePoints(170));
        assertThat(response.get(0).currentPricePoints()).isEqualTo(GamePointCalculator.calculatePricePoints(170));
        assertThat(response.get(0).momentumPriceDeltaPoints()).isZero();
        assertThat(response.get(0).momentumPriceDeltaPercent()).isEqualTo(0.0D);
        assertThat(response.get(0).momentumPriceType()).isEqualTo("NONE");
        assertThat(response.get(0).canBuy()).isTrue();
        assertThat(response.get(0).buyBlockedReason()).isNull();
        assertThat(response.get(1).videoId()).isEqualTo("video-2");
        assertThat(response.get(1).currentPricePoints()).isEqualTo(GamePointCalculator.calculatePricePoints(180));
        assertThat(response.get(1).canBuy()).isTrue();
        assertThat(response.get(1).buyBlockedReason()).isNull();
    }

    @Test
    void getMarketAppliesMomentumPremiumAndDiscountToCurrentPrice() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendSignal surgingSignal = signal("video-1", 171, 20);
        TrendSignal fallingSignal = signal("video-2", 171, -20);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(0L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(surgingSignal, fallingSignal));

        var response = gameService.getMarket(authenticatedUser(), "KR");

        long basePricePoints = GamePointCalculator.calculatePricePoints(171);
        long premiumPricePoints = GamePointCalculator.calculateMomentumAdjustedPricePoints(171, 20);
        long discountPricePoints = GamePointCalculator.calculateMomentumAdjustedPricePoints(171, -20);
        assertThat(response).hasSize(2);
        assertThat(response.get(0).basePricePoints()).isEqualTo(basePricePoints);
        assertThat(response.get(0).currentPricePoints())
            .isEqualTo(premiumPricePoints)
            .isGreaterThan(basePricePoints);
        assertThat(response.get(0).momentumPriceDeltaPoints()).isEqualTo(premiumPricePoints - basePricePoints);
        assertThat(response.get(0).momentumPriceDeltaPercent())
            .isEqualTo(roundPercent(premiumPricePoints - basePricePoints, basePricePoints));
        assertThat(response.get(0).momentumPriceType()).isEqualTo("PREMIUM");
        assertThat(response.get(1).basePricePoints()).isEqualTo(basePricePoints);
        assertThat(response.get(1).currentPricePoints())
            .isEqualTo(discountPricePoints)
            .isLessThan(basePricePoints);
        assertThat(response.get(1).momentumPriceDeltaPoints()).isEqualTo(discountPricePoints - basePricePoints);
        assertThat(response.get(1).momentumPriceDeltaPercent())
            .isEqualTo(roundPercent(discountPricePoints - basePricePoints, basePricePoints));
        assertThat(response.get(1).momentumPriceType()).isEqualTo("DISCOUNT");
    }

    @Test
    void getMarketFallsBackToSnapshotDerivedNewWhenSignalPointsToSameRun() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-17T12:00:00Z"));
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-17T11:00:00Z"));
        TrendSnapshot latestSnapshot = snapshot(latestRun, "video-1", 2);
        TrendSignal brokenSignal = signal("video-1", 2, 0);
        brokenSignal.setCurrentRunId(latestRun.getId());
        brokenSignal.setPreviousRunId(latestRun.getId());
        brokenSignal.setPreviousRank(2);
        brokenSignal.setRankChange(0);
        brokenSignal.setNew(false);
        brokenSignal.setCapturedAt(latestRun.getCapturedAt());

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(0L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(brokenSignal));
        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(latestSnapshot));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", latestRun.getCapturedAt()
        )).thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(previousRun.getId())).thenReturn(List.of());

        var response = gameService.getMarket(authenticatedUser(), "KR");

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.currentRank()).isEqualTo(2);
            assertThat(item.previousRank()).isNull();
            assertThat(item.rankChange()).isNull();
            assertThat(item.isNew()).isTrue();
        });
    }

    @Test
    void getBuyableMarketChartReturnsOnlyCurrentlyBuyableTopVideos() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSignal blockedSignal = signal("video-2", 1, 0);
        TrendSignal affordableSignal = signal("video-1", 170, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN))
            .thenReturn(0L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(blockedSignal, affordableSignal));
        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot(latestRun, "video-2", 1), snapshot(latestRun, "video-1", 170)));

        var response = gameService.getBuyableMarketChart(authenticatedUser(), "KR", null);

        assertThat(response.categoryId()).isEqualTo("buyable-market");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo("video-1");
        assertThat(response.items().getFirst().trend().currentRank()).isEqualTo(170);
    }

    @Test
    void getBuyableMarketChartReadsLatestStoredSnapshots() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSignal blockedSignal = signal("video-2", 1, 0);
        TrendSignal affordableSignal = signal("video-1", 170, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN))
            .thenReturn(0L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(blockedSignal, affordableSignal));
        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot(latestRun, "video-2", 1), snapshot(latestRun, "video-1", 170)));

        var response = gameService.getBuyableMarketChart(authenticatedUser(), "KR", null);

        assertThat(response.categoryId()).isEqualTo("buyable-market");
        assertThat(response.items()).singleElement().extracting("id").isEqualTo("video-1");
        assertThat(response.items().getFirst().trend().currentRank()).isEqualTo(170);
    }

    @Test
    void getBuyableMarketChartFiltersUsingSnapshotRankEvenWhenSignalRankIsStale() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        TrendRun previousRun = trendRun(54L, Instant.parse("2026-04-01T05:00:00Z"));
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSignal staleSignal = signal("video-1", 120, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN))
            .thenReturn(0L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of(staleSignal));
        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(List.of(snapshot(latestRun, "video-1", 190)));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
            "KR", "0", "youtube-mostPopular", latestRun.getCapturedAt()
        ))
            .thenReturn(Optional.of(previousRun));
        when(trendSnapshotRepository.findByRunId(previousRun.getId()))
            .thenReturn(List.of(snapshot(previousRun, "video-1", 170)));

        var response = gameService.getBuyableMarketChart(authenticatedUser(), "KR", null);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo("video-1");
        assertThat(response.items().getFirst().trend().currentRank()).isEqualTo(190);
        assertThat(response.items().getFirst().trend().previousRank()).isEqualTo(170);
        assertThat(response.items().getFirst().trend().rankChange()).isEqualTo(-20);
    }

    @Test
    void getBuyableMarketChartReturnsFiftyItemsPerPage() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 1_000_000_000L, 0L, 0L);
        TrendRun latestRun = trendRun(55L, Instant.parse("2026-04-01T06:00:00Z"));
        List<TrendSnapshot> snapshots = java.util.stream.IntStream.rangeClosed(1, 60)
            .mapToObj(rank -> snapshot(latestRun, "video-" + rank, 140 + rank))
            .toList();

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN))
            .thenReturn(0L);
        when(gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(1L, 7L, PositionStatus.OPEN))
            .thenReturn(List.of());
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryId("KR", "0"))
            .thenReturn(List.of());
        when(trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc("KR", "0"))
            .thenReturn(snapshots);

        var firstPage = gameService.getBuyableMarketChart(authenticatedUser(), "KR", null);
        var secondPage = gameService.getBuyableMarketChart(authenticatedUser(), "KR", "50");

        assertThat(firstPage.items()).hasSize(50);
        assertThat(firstPage.nextPageToken()).isEqualTo("50");
        assertThat(firstPage.items().getFirst().id()).isEqualTo("video-1");
        assertThat(firstPage.items().getLast().id()).isEqualTo("video-50");

        assertThat(secondPage.items()).hasSize(10);
        assertThat(secondPage.nextPageToken()).isNull();
        assertThat(secondPage.items().getFirst().id()).isEqualTo("video-51");
        assertThat(secondPage.items().getLast().id()).isEqualTo("video-60");
    }

    @Test
    void buyAllowsSameVideoPastDistinctVideoLimitWhenWalletCanCoverIt() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, buyPricePoints * 3, buyPricePoints, 0L);
        TrendSignal signal = signal("video-1", 170, 0);
        GamePosition existingPosition = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of(existingPosition));
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN))
            .thenReturn((long) season.getMaxOpenPositions());
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition position = invocation.getArgument(0, GamePosition.class);
            ReflectionTestUtils.setField(position, "id", 305L);
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, TWO_SHARES)
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).quantity()).isEqualTo(TWO_SHARES);
        assertThat(response.get(0).stakePoints()).isEqualTo(buyPricePoints * 2);
        assertThat(wallet.getBalancePoints()).isEqualTo(buyPricePoints);
    }

    @Test
    void buyUsesLockedWalletLookupForBalanceValidation() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, buyPricePoints - 1, 0L, 0L);
        TrendSignal signal = signal("video-1", 170, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of());
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN)).thenReturn(0L);
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));

        assertThatThrownBy(() -> gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, ONE_SHARE)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("보유 포인트가 부족합니다.");

        verify(gameWalletRepository).findBySeasonIdAndUserIdForUpdate(1L, 7L);
        verify(gamePositionRepository, never()).save(any(GamePosition.class));
    }

    @Test
    void buyRejectsFractionalQuantityOrders() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, ONE_SHARE + (ONE_SHARE / 2))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity는 1개 단위로만 주문할 수 있습니다.");
    }

    @Test
    void sellRejectsFractionalQuantityOrders() {
        GameSeason season = activeSeason();

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));

        assertThatThrownBy(() -> gameService.sell(
            authenticatedUser(),
            new SellPositionsRequest("KR", null, "video-1", ONE_SHARE + (ONE_SHARE / 2))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("quantity는 1개 단위로만 주문할 수 있습니다.");
    }

    @Test
    void getLeaderboardOrdersByCoinBalanceBeforeMarkedToMarketTotalAssets() {
        GameSeason season = activeSeason();
        AppUser me = user(7L, "Atlas User");
        AppUser rival = user(8L, "Rival User");
        GameSeasonCoinTier platinumTier = coinTier(season, "PLATINUM", "플래티넘", 1_200_000L, 4);
        GameSeasonCoinTier goldTier = coinTier(season, "GOLD", "골드", 300_000L, 3);
        long myBuyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long rivalBuyPricePoints = GamePointCalculator.calculatePricePoints(180);
        long myMarkedPricePoints = GamePointCalculator.calculatePricePoints(180);
        long rivalMarkedPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet myWallet = wallet(season, me, 10_000L - myBuyPricePoints, myBuyPricePoints, 0L);
        GameWallet rivalWallet = wallet(season, rival, 10_000L - rivalBuyPricePoints, rivalBuyPricePoints, 0L);
        myWallet.setCoinBalance(2_500_000L);
        rivalWallet.setCoinBalance(1_199_999L);
        GamePosition myPosition = openPosition(
            season,
            me,
            "video-1",
            170,
            myBuyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        GamePosition rivalPosition = openPosition(
            season,
            rival,
            "video-2",
            180,
            rivalBuyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        TrendSignal mySignal = signal("video-1", 180, 0);
        TrendSignal rivalSignal = signal("video-2", 170, 0);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(myWallet));
        when(gameCoinTierService.getOrCreateTiers(season)).thenReturn(List.of(goldTier, platinumTier));
        when(gameCoinTierService.resolveTier(List.of(goldTier, platinumTier), 2_500_000L)).thenReturn(platinumTier);
        when(gameCoinTierService.resolveTier(List.of(goldTier, platinumTier), 1_199_999L)).thenReturn(goldTier);
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(mySignal, rivalSignal));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN))
            .thenReturn(List.of(myPosition, rivalPosition));
        when(gameWalletRepository.findBySeasonId(1L)).thenReturn(List.of(myWallet, rivalWallet));

        var response = gameService.getLeaderboard(authenticatedUser(), "KR");

        assertThat(response).hasSize(2);
        assertThat(response.get(0).userId()).isEqualTo(7L);
        assertThat(response.get(0).rank()).isEqualTo(1);
        assertThat(response.get(0).currentTier().tierCode()).isEqualTo("PLATINUM");
        assertThat(response.get(0).currentTier().displayName()).isEqualTo("플래티넘");
        assertThat(response.get(0).coinBalance()).isEqualTo(2_500_000L);
        assertThat(response.get(0).totalStakePoints()).isEqualTo(myBuyPricePoints);
        assertThat(response.get(0).totalEvaluationPoints()).isEqualTo(myMarkedPricePoints);
        assertThat(response.get(0).profitRatePercent()).isEqualTo(-26.7D);
        assertThat(response.get(0).totalAssetPoints()).isEqualTo((10_000L - myBuyPricePoints) + myMarkedPricePoints);
        assertThat(response.get(0).unrealizedPnlPoints()).isEqualTo(myMarkedPricePoints - myBuyPricePoints);
        assertThat(response.get(0).me()).isTrue();
        assertThat(response.get(1).userId()).isEqualTo(8L);
        assertThat(response.get(1).rank()).isEqualTo(2);
        assertThat(response.get(1).currentTier().tierCode()).isEqualTo("GOLD");
        assertThat(response.get(1).currentTier().displayName()).isEqualTo("골드");
        assertThat(response.get(1).coinBalance()).isEqualTo(1_199_999L);
        assertThat(response.get(1).totalStakePoints()).isEqualTo(rivalBuyPricePoints);
        assertThat(response.get(1).totalEvaluationPoints()).isEqualTo(rivalMarkedPricePoints);
        assertThat(response.get(1).profitRatePercent()).isEqualTo(36.4D);
        assertThat(response.get(1).totalAssetPoints()).isEqualTo((10_000L - rivalBuyPricePoints) + rivalMarkedPricePoints);
        assertThat(response.get(1).unrealizedPnlPoints()).isEqualTo(rivalMarkedPricePoints - rivalBuyPricePoints);
        assertThat(response.get(1).me()).isFalse();
        assertThat(response.get(0).totalAssetPoints()).isLessThan(response.get(1).totalAssetPoints());
    }

    @Test
    void getCoinOverviewReturnsFixedEstimatedCoinYieldAndWarmupPositions() {
        GameSeason season = activeSeason();
        AppUser me = user(7L, "Atlas User");
        AppUser rival = user(8L, "Rival User");
        GameWallet wallet = wallet(season, me, 10_000L, 0L, 0L);
        GamePosition myEligiblePosition = openPosition(
            season,
            me,
            "video-1",
            12,
            GamePointCalculator.calculatePricePoints(12),
            Instant.parse("2026-04-01T05:40:00Z")
        );
        GamePosition myWarmupPosition = openPosition(
            season,
            me,
            "video-2",
            6,
            GamePointCalculator.calculatePricePoints(6),
            Instant.parse("2026-04-01T05:55:30Z")
        );
        GamePosition rivalEligiblePosition = openPosition(
            season,
            rival,
            "video-3",
            15,
            GamePointCalculator.calculatePricePoints(15),
            Instant.parse("2026-04-01T05:35:00Z")
        );
        long myEligibleValuePoints = GamePointCalculator.calculatePricePoints(5);
        int myHoldBoostBasisPoints = GameService.calculateHoldBoostBasisPoints(1_200L, 600, 600, 1_000, 10_000);
        int myEffectiveCoinRateBasisPoints = GameService.calculateEffectiveCoinRateBasisPoints(76, myHoldBoostBasisPoints);
        long myEstimatedCoinYield = GameService.calculateEstimatedCoinYield(myEligibleValuePoints, myEffectiveCoinRateBasisPoints);

        ReflectionTestUtils.setField(myEligiblePosition, "id", 501L);
        ReflectionTestUtils.setField(myWarmupPosition, "id", 502L);
        ReflectionTestUtils.setField(rivalEligiblePosition, "id", 503L);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(
                signal("video-2", 2, 0),
                signal("video-1", 5, 0),
                signal("video-3", 10, 0)
            ));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN))
            .thenReturn(List.of(myEligiblePosition, myWarmupPosition, rivalEligiblePosition));

        var response = gameService.getCoinOverview(authenticatedUser(), "KR");

        assertThat(response.eligibleRankCutoff()).isEqualTo(200);
        assertThat(response.minimumHoldSeconds()).isEqualTo(600);
        assertThat(response.myCoinBalance()).isZero();
        assertThat(response.myEstimatedCoinYield()).isEqualTo(myEstimatedCoinYield);
        assertThat(response.myActiveProducerCount()).isEqualTo(1);
        assertThat(response.myWarmingUpPositionCount()).isEqualTo(1);
        assertThat(response.ranks()).hasSize(200);
        assertThat(response.ranks().getFirst().rank()).isEqualTo(1);
        assertThat(response.ranks().getFirst().coinRatePercent()).isEqualTo(1.0D);
        assertThat(response.ranks().get(4).coinRatePercent()).isEqualTo(0.76D);
        assertThat(response.ranks().get(19).coinRatePercent()).isEqualTo(0.42D);
        assertThat(response.ranks().get(199).coinRatePercent()).isZero();
        assertThat(response.positions()).hasSize(2);
        assertThat(response.positions().get(0).positionId()).isEqualTo(501L);
        assertThat(response.positions().get(0).rankEligible()).isTrue();
        assertThat(response.positions().get(0).productionActive()).isTrue();
        assertThat(response.positions().get(0).coinRatePercent()).isEqualTo(0.76D);
        assertThat(response.positions().get(0).holdBoostPercent()).isEqualTo(10.0D);
        assertThat(response.positions().get(0).effectiveCoinRatePercent()).isEqualTo(0.84D);
        assertThat(response.positions().get(0).estimatedCoinYield()).isEqualTo(myEstimatedCoinYield);
        assertThat(response.positions().get(0).nextPayoutInSeconds()).isEqualTo(300L);
        assertThat(response.positions().get(1).positionId()).isEqualTo(502L);
        assertThat(response.positions().get(1).rankEligible()).isTrue();
        assertThat(response.positions().get(1).productionActive()).isFalse();
        assertThat(response.positions().get(1).coinRatePercent()).isEqualTo(0.94D);
        assertThat(response.positions().get(1).holdBoostPercent()).isZero();
        assertThat(response.positions().get(1).effectiveCoinRatePercent()).isEqualTo(0.94D);
        assertThat(response.positions().get(1).estimatedCoinYield()).isZero();
        assertThat(response.positions().get(1).nextProductionInSeconds()).isEqualTo(330L);
        assertThat(response.positions().get(1).nextPayoutInSeconds()).isNull();
    }

    @Test
    void calculateHoldBoostCapsAtDoubleBaseRate() {
        int holdBoostBasisPoints = GameService.calculateHoldBoostBasisPoints(8_400L, 600, 600, 1_000, 10_000);
        int effectiveCoinRateBasisPoints = GameService.calculateEffectiveCoinRateBasisPoints(300, holdBoostBasisPoints);

        assertThat(holdBoostBasisPoints).isEqualTo(10_000);
        assertThat(effectiveCoinRateBasisPoints).isEqualTo(600);
    }

    @Test
    void resolveCoinRateUsesReducedLowerRankAnchors() {
        assertThat(GameService.resolveCoinRateBasisPoints(1)).isEqualTo(100);
        assertThat(GameService.resolveCoinRateBasisPoints(10)).isEqualTo(46);
        assertThat(GameService.resolveCoinRateBasisPoints(50)).isEqualTo(30);
        assertThat(GameService.resolveCoinRateBasisPoints(100)).isEqualTo(15);
        assertThat(GameService.resolveCoinRateBasisPoints(150)).isEqualTo(4);
        assertThat(GameService.resolveCoinRateBasisPoints(200)).isZero();
    }

    @Test
    void getCurrentCoinTierReturnsCurrentAndNextTierFromCoinBalance() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        wallet.setCoinBalance(2_500_000L);
        GameSeasonCoinTier bronzeTier = coinTier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonCoinTier silverTier = coinTier(season, "SILVER", "실버", 100_000L, 2);
        GameSeasonCoinTier goldTier = coinTier(season, "GOLD", "골드", 300_000L, 3);
        GameSeasonCoinTier platinumTier = coinTier(season, "PLATINUM", "플래티넘", 1_200_000L, 4);
        GameSeasonCoinTier diamondTier = coinTier(season, "DIAMOND", "다이아몬드", 6_000_000L, 5);
        GameSeasonCoinTier masterTier = coinTier(season, "MASTER", "마스터", 36_000_000L, 6);
        GameSeasonCoinTier legendTier = coinTier(season, "LEGEND", "레전드", 252_000_000L, 7);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameCoinTierService.getOrCreateTiers(season))
            .thenReturn(List.of(bronzeTier, silverTier, goldTier, platinumTier, diamondTier, masterTier, legendTier));
        when(gameCoinTierService.resolveTier(
            List.of(bronzeTier, silverTier, goldTier, platinumTier, diamondTier, masterTier, legendTier),
            2_500_000L
        ))
            .thenReturn(platinumTier);

        var response = gameService.getCurrentCoinTier(authenticatedUser(), "KR");

        assertThat(response.seasonId()).isEqualTo(1L);
        assertThat(response.coinBalance()).isEqualTo(2_500_000L);
        assertThat(response.currentTier().tierCode()).isEqualTo("PLATINUM");
        assertThat(response.currentTier().displayName()).isEqualTo("플래티넘");
        assertThat(response.nextTier().tierCode()).isEqualTo("DIAMOND");
        assertThat(response.tiers()).hasSize(7);
    }

    @Test
    void getSeasonCoinResultReturnsFinalizedSeasonCoinResult() {
        GameSeason season = activeSeason();
        season.setStatus(SeasonStatus.ENDED);
        GameSeasonCoinResult result = new GameSeasonCoinResult();
        result.setSeason(season);
        result.setUser(user(7L));
        result.setFinalCoinBalance(5_500_000L);
        result.setFinalTierCode("PLATINUM");
        result.setFinalTierDisplayName("플래티넘");
        result.setFinalTierMinCoinBalance(5_000_000L);
        result.setBadgeCode("season-platinum");
        result.setTitleCode("platinum-investor");
        result.setProfileThemeCode("platinum");
        result.setCreatedAt(Instant.parse("2026-04-08T00:01:00Z"));

        when(gameSeasonRepository.findById(1L)).thenReturn(Optional.of(season));
        when(gameSeasonCoinResultRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(result));

        var response = gameService.getSeasonCoinResult(authenticatedUser(), 1L);

        assertThat(response.seasonId()).isEqualTo(1L);
        assertThat(response.finalCoinBalance()).isEqualTo(5_500_000L);
        assertThat(response.finalTier().tierCode()).isEqualTo("PLATINUM");
        assertThat(response.finalTier().displayName()).isEqualTo("플래티넘");
        assertThat(response.finalizedAt()).isEqualTo(Instant.parse("2026-04-08T00:01:00Z"));
    }

    @Test
    void getPositionRankHistoryReturnsObservedRunsBetweenBuyAndSell() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            GamePointCalculator.calculatePricePoints(170),
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendRun preBuyRun = trendRun(10L, Instant.parse("2026-04-01T05:30:00Z"));
        TrendRun buyRun = trendRun(11L, Instant.parse("2026-04-01T05:40:00Z"));
        TrendRun middleRun = trendRun(12L, Instant.parse("2026-04-01T05:50:00Z"));
        TrendRun sellRun = trendRun(13L, Instant.parse("2026-04-01T06:00:00Z"));

        position.setStatus(PositionStatus.CLOSED);
        position.setSellRunId(13L);
        position.setSellRank(150);
        position.setSellCapturedAt(sellRun.getCapturedAt());
        position.setClosedAt(Instant.parse("2026-04-01T06:01:00Z"));

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSnapshotRepository.findFirstByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(Optional.of(snapshot(preBuyRun, "video-1", 181)));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc("KR", "0", 10L, 13L))
            .thenReturn(List.of(preBuyRun, buyRun, middleRun, sellRun));
        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
            "KR",
            "0",
            "video-1",
            10L,
            13L
        )).thenReturn(List.of(
            snapshot(preBuyRun, "video-1", 181),
            snapshot(buyRun, "video-1", 170),
            snapshot(middleRun, "video-1", 161),
            snapshot(sellRun, "video-1", 150)
        ));

        var response = gameService.getPositionRankHistory(authenticatedUser(), 300L);

        assertThat(response.positionId()).isEqualTo(300L);
        assertThat(response.latestRank()).isEqualTo(150);
        assertThat(response.latestChartOut()).isFalse();
        assertThat(response.points()).hasSize(4);
        assertThat(response.points().get(0).buyPoint()).isFalse();
        assertThat(response.points().get(0).rank()).isEqualTo(181);
        assertThat(response.points().get(1).buyPoint()).isTrue();
        assertThat(response.points().get(2).rank()).isEqualTo(161);
        assertThat(response.points().get(3).sellPoint()).isTrue();
        assertThat(response.points().get(3).chartOut()).isFalse();
    }

    @Test
    void getPositionRankHistoryMarksMissingRunsAsChartOut() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            GamePointCalculator.calculatePricePoints(170),
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendRun buyRun = trendRun(11L, Instant.parse("2026-04-01T05:40:00Z"));
        TrendRun latestRun = trendRun(12L, Instant.parse("2026-04-01T06:00:00Z"));

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSnapshotRepository.findFirstByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(Optional.of(snapshot(buyRun, "video-1", 170)));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.empty());
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc("KR", "0")).thenReturn(Optional.of(latestRun));
        when(trendSnapshotRepository.findByRunId(12L)).thenReturn(List.of(
            snapshot(latestRun, "video-2", 8),
            snapshot(latestRun, "video-3", 12)
        ));
        when(trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc("KR", "0", 12L))
            .thenReturn(Optional.empty());
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc("KR", "0", 11L, 12L))
            .thenReturn(List.of(buyRun, latestRun));
        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
            "KR",
            "0",
            "video-1",
            11L,
            12L
        )).thenReturn(List.of(snapshot(buyRun, "video-1", 170)));

        var response = gameService.getPositionRankHistory(authenticatedUser(), 300L);

        assertThat(response.latestRank()).isEqualTo(13);
        assertThat(response.latestChartOut()).isTrue();
        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0).rank()).isEqualTo(170);
        assertThat(response.points().get(1).rank()).isNull();
        assertThat(response.points().get(1).chartOut()).isTrue();
    }

    @Test
    void getPositionRankHistoryCollapsesDuplicateRunsInSameCaptureSlot() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            133,
            GamePointCalculator.calculatePricePoints(133),
            Instant.parse("2026-04-10T07:00:10Z")
        );
        TrendRun firstRun = trendRun(20L, Instant.parse("2026-04-10T07:00:05Z"));
        TrendRun secondRun = trendRun(21L, Instant.parse("2026-04-10T07:04:42Z"));
        TrendRun latestRun = trendRun(22L, Instant.parse("2026-04-10T08:00:11Z"));

        position.setStatus(PositionStatus.CLOSED);
        position.setSellRunId(22L);
        position.setSellRank(128);
        position.setSellCapturedAt(latestRun.getCapturedAt());
        position.setClosedAt(Instant.parse("2026-04-10T08:01:00Z"));

        when(gamePositionRepository.findByIdAndUserId(300L, 7L)).thenReturn(Optional.of(position));
        when(trendSnapshotRepository.findFirstByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(Optional.of(snapshot(firstRun, "video-1", 133)));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc("KR", "0", 20L, 22L))
            .thenReturn(List.of(firstRun, secondRun, latestRun));
        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
            "KR",
            "0",
            "video-1",
            20L,
            22L
        )).thenReturn(List.of(
            snapshot(firstRun, "video-1", 133),
            snapshot(secondRun, "video-1", 133),
            snapshot(latestRun, "video-1", 128)
        ));

        var response = gameService.getPositionRankHistory(authenticatedUser(), 300L);

        assertThat(response.points()).hasSize(2);
        assertThat(response.points().get(0).capturedAt()).isEqualTo(Instant.parse("2026-04-10T07:00:00Z"));
        assertThat(response.points().get(1).capturedAt()).isEqualTo(Instant.parse("2026-04-10T08:00:00Z"));
        assertThat(response.points().get(1).sellPoint()).isTrue();
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
        wallet.setCoinBalance(0L);
        wallet.setUpdatedAt(Instant.parse("2026-04-01T05:30:00Z"));
        return wallet;
    }

    private GameSeasonCoinTier coinTier(GameSeason season, String tierCode, String displayName, long minCoinBalance, int sortOrder) {
        GameSeasonCoinTier tier = new GameSeasonCoinTier();
        ReflectionTestUtils.setField(tier, "id", (long) sortOrder);
        tier.setSeason(season);
        tier.setTierCode(tierCode);
        tier.setDisplayName(displayName);
        tier.setMinCoinBalance(minCoinBalance);
        tier.setBadgeCode("badge-" + tierCode.toLowerCase());
        tier.setTitleCode("title-" + tierCode.toLowerCase());
        tier.setProfileThemeCode(tierCode.toLowerCase());
        tier.setSortOrder(sortOrder);
        tier.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return tier;
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
        position.setQuantity(ONE_SHARE);
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

    private double roundPercent(long deltaPoints, long basePoints) {
        return Math.round((double) deltaPoints * 1_000D / basePoints) / 10D;
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
