package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;

class GameSettlementServiceTest {

    private static final int ONE_SHARE = GamePointCalculator.QUANTITY_SCALE;

    private AtlasProperties atlasProperties;
    private GameSeasonRepository gameSeasonRepository;
    private GamePositionRepository gamePositionRepository;
    private GameWalletRepository gameWalletRepository;
    private GameLedgerRepository gameLedgerRepository;
    private GameCoinPayoutRepository gameCoinPayoutRepository;
    private TrendSignalRepository trendSignalRepository;
    private GameSettlementService gameSettlementService;

    @BeforeEach
    void setUp() {
        atlasProperties = new AtlasProperties();
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gameLedgerRepository = org.mockito.Mockito.mock(GameLedgerRepository.class);
        gameCoinPayoutRepository = org.mockito.Mockito.mock(GameCoinPayoutRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-08T00:01:00Z"), ZoneOffset.UTC);

        gameSettlementService = new GameSettlementService(
            atlasProperties,
            gameSeasonRepository,
            gamePositionRepository,
            gameWalletRepository,
            gameLedgerRepository,
            gameCoinPayoutRepository,
            trendSignalRepository,
            fixedClock
        );
    }

    @Test
    void distributeActiveSeasonCoinsPaysEligibleTopRankPositionOncePerRun() {
        GameSeason season = activeSeasonStillRunning();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(season, appUser, "video-1", 12, GamePointCalculator.calculatePricePoints(12));
        GameWallet wallet = wallet(season, appUser, 10_000L, position.getStakePoints(), 0L);
        TrendSignal signal = signal("video-1", 5);
        long currentValuePoints = GamePointCalculator.calculatePricePoints(5);
        long producedCoins = Math.round(currentValuePoints * 0.022D);

        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of(season));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(signal));
        when(gameCoinPayoutRepository.findPositionIdsBySeasonIdAndTrendRunId(1L, 55L)).thenReturn(List.of());
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN)).thenReturn(List.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameCoinPayoutRepository.saveAndFlush(any(GameCoinPayout.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);

        gameSettlementService.distributeActiveSeasonCoins();

        assertThat(wallet.getBalancePoints()).isEqualTo(10_000L);
        assertThat(wallet.getCoinBalance()).isEqualTo(producedCoins);
        verify(gameCoinPayoutRepository).saveAndFlush(any(GameCoinPayout.class));
    }

    @Test
    void distributeActiveSeasonCoinsSkipsPositionAlreadyPaidForCurrentRun() {
        GameSeason season = activeSeasonStillRunning();
        AppUser appUser = user(7L);
        GamePosition position = openPosition(season, appUser, "video-1", 12, GamePointCalculator.calculatePricePoints(12));
        GameWallet wallet = wallet(season, appUser, 10_000L, position.getStakePoints(), 0L);
        TrendSignal signal = signal("video-1", 5);

        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of(season));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(signal));
        when(gameCoinPayoutRepository.findPositionIdsBySeasonIdAndTrendRunId(1L, 55L)).thenReturn(List.of(300L));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN)).thenReturn(List.of(position));

        gameSettlementService.distributeActiveSeasonCoins();

        assertThat(wallet.getBalancePoints()).isEqualTo(10_000L);
        assertThat(wallet.getCoinBalance()).isZero();
        verify(gameCoinPayoutRepository, org.mockito.Mockito.never()).saveAndFlush(any(GameCoinPayout.class));
        verify(gameLedgerRepository, org.mockito.Mockito.never()).save(any(GameLedger.class));
    }

    @Test
    void settleEndedSeasonsAutoClosesOpenPositionsAndEndsSeason() {
        GameSeason season = activeSeasonEndingNow();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(120);
        long sellPricePoints = GamePointCalculator.calculatePricePoints(100);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(buyPricePoints, settledPoints);
        GamePosition position = openPosition(season, appUser, "video-1", 120, buyPricePoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        TrendSignal signal = signal("video-1", 100);

        when(gameSeasonRepository.findByStatusAndEndAtLessThanEqual(SeasonStatus.ACTIVE, Instant.parse("2026-04-08T00:01:00Z")))
            .thenReturn(List.of(season));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(signal));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN))
            .thenReturn(List.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameSeasonRepository.save(season)).thenReturn(season);

        gameSettlementService.settleEndedSeasons();

        assertThat(position.getStatus()).isEqualTo(PositionStatus.AUTO_CLOSED);
        assertThat(position.getSellRank()).isEqualTo(100);
        assertThat(position.getRankDiff()).isEqualTo(20);
        assertThat(position.getPnlPoints()).isEqualTo(pnlPoints);
        assertThat(position.getSettledPoints()).isEqualTo(settledPoints);
        assertThat(wallet.getBalancePoints()).isEqualTo((10_000L - buyPricePoints) + settledPoints);
        assertThat(wallet.getReservedPoints()).isZero();
        assertThat(wallet.getRealizedPnlPoints()).isEqualTo(pnlPoints);
        assertThat(season.getStatus()).isEqualTo(SeasonStatus.ENDED);
        verify(gameLedgerRepository).save(any(GameLedger.class));
        verify(gameSeasonRepository).save(season);
    }

    @Test
    void settleEndedSeasonsUsesFallbackRankWhenVideoDropsOut() {
        GameSeason season = activeSeasonEndingNow();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(150);
        GamePosition position = openPosition(season, appUser, "video-1", 150, buyPricePoints);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        TrendSignal otherSignal = signal("video-2", 200);

        when(gameSeasonRepository.findByStatusAndEndAtLessThanEqual(SeasonStatus.ACTIVE, Instant.parse("2026-04-08T00:01:00Z")))
            .thenReturn(List.of(season));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(otherSignal));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN))
            .thenReturn(List.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameSeasonRepository.save(season)).thenReturn(season);

        gameSettlementService.settleEndedSeasons();

        assertThat(position.getStatus()).isEqualTo(PositionStatus.AUTO_CLOSED);
        assertThat(position.getSellRank()).isEqualTo(201);
        assertThat(position.getRankDiff()).isEqualTo(-51);
        assertThat(position.getSettledPoints()).isZero();
    }

    @Test
    void settleEndedSeasonsScheduledCreatesNewSeasonForManagedRegionFromLatestTemplate() {
        atlasProperties.getGame().setSchedulerEnabled(true);
        atlasProperties.getTrending().setJobs(List.of(syncJob("US")));

        GameSeason latestUsSeason = endedSeason("US");

        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of());
        when(gameSeasonRepository.findByStatusAndEndAtLessThanEqual(SeasonStatus.ACTIVE, Instant.parse("2026-04-08T00:01:00Z")))
            .thenReturn(List.of());
        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "US"))
            .thenReturn(Optional.empty());
        when(gameSeasonRepository.findTopByRegionCodeOrderByStartAtDesc("US"))
            .thenReturn(Optional.of(latestUsSeason));
        when(gameSeasonRepository.save(any(GameSeason.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameSettlementService.settleEndedSeasonsScheduled();

        org.mockito.ArgumentCaptor<GameSeason> captor = org.mockito.ArgumentCaptor.forClass(GameSeason.class);
        verify(gameSeasonRepository).save(captor.capture());
        GameSeason createdSeason = captor.getValue();

        assertThat(createdSeason.getRegionCode()).isEqualTo("US");
        assertThat(createdSeason.getStatus()).isEqualTo(SeasonStatus.ACTIVE);
        assertThat(createdSeason.getStartAt()).isEqualTo(Instant.parse("2026-04-08T00:01:00Z"));
        assertThat(createdSeason.getEndAt()).isEqualTo(Instant.parse("2026-04-15T00:01:00Z"));
        assertThat(createdSeason.getStartingBalancePoints()).isEqualTo(latestUsSeason.getStartingBalancePoints());
        assertThat(createdSeason.getMinHoldSeconds()).isEqualTo(latestUsSeason.getMinHoldSeconds());
        assertThat(createdSeason.getMaxOpenPositions()).isEqualTo(latestUsSeason.getMaxOpenPositions());
        assertThat(createdSeason.getRankPointMultiplier()).isEqualTo(latestUsSeason.getRankPointMultiplier());
    }

    @Test
    void settleEndedSeasonsScheduledCreatesInitialSeasonFromDefaultsWhenNoHistoryExists() {
        atlasProperties.getGame().setSchedulerEnabled(true);
        atlasProperties.getGame().setSeasonDurationDays(3);
        atlasProperties.getGame().setStartingBalancePoints(20_000L);
        atlasProperties.getGame().setMinHoldSeconds(300);
        atlasProperties.getGame().setMaxOpenPositions(7);
        atlasProperties.getGame().setRankPointMultiplier(150);
        atlasProperties.getTrending().setJobs(List.of(syncJob("BR")));

        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of());
        when(gameSeasonRepository.findByStatusAndEndAtLessThanEqual(SeasonStatus.ACTIVE, Instant.parse("2026-04-08T00:01:00Z")))
            .thenReturn(List.of());
        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "BR"))
            .thenReturn(Optional.empty());
        when(gameSeasonRepository.findTopByRegionCodeOrderByStartAtDesc("BR"))
            .thenReturn(Optional.empty());
        when(gameSeasonRepository.save(any(GameSeason.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameSettlementService.settleEndedSeasonsScheduled();

        org.mockito.ArgumentCaptor<GameSeason> captor = org.mockito.ArgumentCaptor.forClass(GameSeason.class);
        verify(gameSeasonRepository).save(captor.capture());
        GameSeason createdSeason = captor.getValue();

        assertThat(createdSeason.getRegionCode()).isEqualTo("BR");
        assertThat(createdSeason.getStartAt()).isEqualTo(Instant.parse("2026-04-08T00:01:00Z"));
        assertThat(createdSeason.getEndAt()).isEqualTo(Instant.parse("2026-04-11T00:01:00Z"));
        assertThat(createdSeason.getStartingBalancePoints()).isEqualTo(20_000L);
        assertThat(createdSeason.getMinHoldSeconds()).isEqualTo(300);
        assertThat(createdSeason.getMaxOpenPositions()).isEqualTo(7);
        assertThat(createdSeason.getRankPointMultiplier()).isEqualTo(150);
    }

    private GameSeason activeSeasonEndingNow() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        season.setName("KR Weekly Season");
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

    private GameSeason activeSeasonStillRunning() {
        GameSeason season = activeSeasonEndingNow();
        season.setEndAt(Instant.parse("2026-04-09T00:00:00Z"));
        return season;
    }

    private GameSeason endedSeason(String regionCode) {
        GameSeason season = activeSeasonEndingNow();
        season.setRegionCode(regionCode);
        season.setStatus(SeasonStatus.ENDED);
        return season;
    }

    private AtlasProperties.SyncJob syncJob(String regionCode) {
        AtlasProperties.SyncJob job = new AtlasProperties.SyncJob();
        job.setRegionCode(regionCode);
        job.setCategoryId("0");
        job.setCategoryLabel("전체");
        return job;
    }

    private AppUser user(Long id) {
        AppUser appUser = new AppUser();
        ReflectionTestUtils.setField(appUser, "id", id);
        appUser.setDisplayName("User " + id);
        return appUser;
    }

    private GamePosition openPosition(GameSeason season, AppUser appUser, String videoId, int buyRank, long stakePoints) {
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
        position.setBuyCapturedAt(Instant.parse("2026-04-07T23:30:00Z"));
        position.setQuantity(ONE_SHARE);
        position.setStakePoints(stakePoints);
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(Instant.parse("2026-04-07T23:30:00Z"));
        return position;
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
        wallet.setUpdatedAt(Instant.parse("2026-04-07T23:55:00Z"));
        return wallet;
    }

    private TrendSignal signal(String videoId, int currentRank) {
        TrendSignal signal = new TrendSignal();
        signal.setId(new TrendSignalId("KR", "0", videoId));
        signal.setCategoryLabel("전체");
        signal.setCurrentRunId(55L);
        signal.setPreviousRunId(54L);
        signal.setCurrentRank(currentRank);
        signal.setPreviousRank(currentRank + 1);
        signal.setRankChange(1);
        signal.setCurrentViewCount(5_000L);
        signal.setPreviousViewCount(4_500L);
        signal.setViewCountDelta(500L);
        signal.setNew(false);
        signal.setTitle("Title " + videoId);
        signal.setChannelTitle("Channel");
        signal.setChannelId("channel-1");
        signal.setThumbnailUrl("https://example.com/" + videoId + ".jpg");
        signal.setCapturedAt(Instant.parse("2026-04-08T00:00:00Z"));
        signal.setUpdatedAt(Instant.parse("2026-04-08T00:00:00Z"));
        return signal;
    }
}
