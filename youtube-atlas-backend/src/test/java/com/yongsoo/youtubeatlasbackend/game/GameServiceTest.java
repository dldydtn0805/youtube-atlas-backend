package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
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
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionsRequest;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

class GameServiceTest {

    private static final long ONE_SHARE = GamePointCalculator.QUANTITY_SCALE;
    private static final long TWO_SHARES = ONE_SHARE * 2;
    private static final long THREE_SHARES = ONE_SHARE * 3;

    private GameSeasonRepository gameSeasonRepository;
    private GameWalletRepository gameWalletRepository;
    private GamePositionRepository gamePositionRepository;
    private GameScheduledSellOrderRepository gameScheduledSellOrderRepository;
    private GameHighlightStateRepository gameHighlightStateRepository;
    private GameLedgerRepository gameLedgerRepository;
    private GameSeasonResultRepository gameSeasonResultRepository;
    private GameTierService gameTierService;
    private GameNotificationService gameNotificationService;
    private AchievementTitleService achievementTitleService;
    private AppUserRepository appUserRepository;
    private TrendSignalRepository trendSignalRepository;
    private TrendRunRepository trendRunRepository;
    private TrendSnapshotRepository trendSnapshotRepository;
    private CommentService commentService;
    private GameService gameService;
    private List<GameHighlightState> storedHighlightStates;

    @BeforeEach
    void setUp() {
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameScheduledSellOrderRepository = org.mockito.Mockito.mock(GameScheduledSellOrderRepository.class);
        gameHighlightStateRepository = org.mockito.Mockito.mock(GameHighlightStateRepository.class);
        gameLedgerRepository = org.mockito.Mockito.mock(GameLedgerRepository.class);
        gameSeasonResultRepository = org.mockito.Mockito.mock(GameSeasonResultRepository.class);
        gameTierService = org.mockito.Mockito.mock(GameTierService.class);
        gameNotificationService = org.mockito.Mockito.mock(GameNotificationService.class);
        achievementTitleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        trendRunRepository = org.mockito.Mockito.mock(TrendRunRepository.class);
        trendSnapshotRepository = org.mockito.Mockito.mock(TrendSnapshotRepository.class);
        commentService = org.mockito.Mockito.mock(CommentService.class);
        storedHighlightStates = new ArrayList<>();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);
        AtlasProperties atlasProperties = new AtlasProperties();
        atlasProperties.getTrending().setCaptureSlotMinutes(5);

        gameService = new GameService(
            gameSeasonRepository,
            gameWalletRepository,
            gamePositionRepository,
            gameScheduledSellOrderRepository,
            gameHighlightStateRepository,
            gameLedgerRepository,
            gameSeasonResultRepository,
            gameTierService,
            gameNotificationService,
            achievementTitleService,
            appUserRepository,
            trendSignalRepository,
            trendRunRepository,
            trendSnapshotRepository,
            commentService,
            fixedClock,
            atlasProperties
        );
        when(gameHighlightStateRepository.findBySeasonIdAndUserId(any(), any())).thenAnswer(invocation ->
            storedHighlightStates.stream()
                .filter(state -> state.getSeason().getId().equals(invocation.getArgument(0, Long.class)))
                .filter(state -> state.getUser().getId().equals(invocation.getArgument(1, Long.class)))
                .toList()
        );
        when(gameHighlightStateRepository.findBySeasonIdAndUserIdAndBestSettledHighlightScoreGreaterThanOrderByBestSettledCreatedAtDesc(any(), any(), any()))
            .thenAnswer(invocation ->
                storedHighlightStates.stream()
                    .filter(state -> state.getSeason().getId().equals(invocation.getArgument(0, Long.class)))
                    .filter(state -> state.getUser().getId().equals(invocation.getArgument(1, Long.class)))
                    .filter(state -> state.getBestSettledHighlightScore() > invocation.getArgument(2, Long.class))
                    .sorted(
                        Comparator.comparing(
                            GameHighlightState::getBestSettledCreatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())
                        )
                    )
                    .toList()
            );
        when(gameHighlightStateRepository.findBySeasonIdAndBestSettledHighlightScoreGreaterThan(any(), any())).thenAnswer(invocation ->
            storedHighlightStates.stream()
                .filter(state -> state.getSeason().getId().equals(invocation.getArgument(0, Long.class)))
                .filter(state -> state.getBestSettledHighlightScore() > invocation.getArgument(1, Long.class))
                .toList()
        );
        when(gameHighlightStateRepository.findBySeasonIdAndUserIdAndRootPositionIdForUpdate(any(), any(), any()))
            .thenAnswer(invocation ->
                storedHighlightStates.stream()
                    .filter(state -> state.getSeason().getId().equals(invocation.getArgument(0, Long.class)))
                    .filter(state -> state.getUser().getId().equals(invocation.getArgument(1, Long.class)))
                    .filter(state -> state.getRootPositionId().equals(invocation.getArgument(2, Long.class)))
                    .findFirst()
            );
        when(gameHighlightStateRepository.save(any(GameHighlightState.class))).thenAnswer(invocation -> {
            GameHighlightState state = invocation.getArgument(0, GameHighlightState.class);
            storedHighlightStates.removeIf(existing ->
                existing.getSeason().getId().equals(state.getSeason().getId())
                    && existing.getUser().getId().equals(state.getUser().getId())
                    && existing.getRootPositionId().equals(state.getRootPositionId())
            );
            storedHighlightStates.add(state);
            return state;
        });
        when(gameHighlightStateRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<GameHighlightState> states = invocation.getArgument(0);
            states.forEach(state -> {
                storedHighlightStates.removeIf(existing ->
                    existing.getSeason().getId().equals(state.getSeason().getId())
                        && existing.getUser().getId().equals(state.getUser().getId())
                        && existing.getRootPositionId().equals(state.getRootPositionId())
                );
                storedHighlightStates.add(state);
            });
            return states;
        });
        when(gameScheduledSellOrderRepository.findByPositionIdsAndStatus(any(), any())).thenReturn(List.of());
        when(gameNotificationService.syncAndListSeasonNotifications(any(GameSeason.class), any(), any()))
            .thenAnswer(invocation -> invocation.getArgument(2));
        when(gameTierService.resolveEffectiveTiers(any(GameSeason.class), any()))
            .thenAnswer(invocation -> invocation.getArgument(1));
        when(achievementTitleService.grantTitlesForHighlight(any(), any())).thenReturn(List.of());
        when(achievementTitleService.grantTitlesForHighlights(any(), any(), any(), any())).thenReturn(List.of());
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
        assertThat(response.maxOpenPositions()).isEqualTo(5);
        assertThat(response.inventorySlots().baseSlots()).isEqualTo(5);
        assertThat(response.inventorySlots().totalSlots()).isEqualTo(5);
        assertThat(response.inventorySlots().maxSlots()).isEqualTo(5);
        assertThat(response.inventorySlots().currentTier()).isNull();
        assertThat(response.inventorySlots().tiers()).isEmpty();
        assertThat(response.notifications()).isEmpty();
        verify(gameLedgerRepository).save(any(GameLedger.class));
    }

    @Test
    void getMySeasonResultsIncludesPastSeasonHighlightSummaries() {
        GameSeason season = activeSeason();
        season.setStatus(SeasonStatus.ENDED);
        AppUser appUser = user(7L);
        GameSeasonResult result = seasonResult(season, appUser);
        GamePosition topRankRiser = closedPosition(
            season,
            appUser,
            301L,
            GamePointCalculator.calculatePricePoints(190),
            190,
            70,
            Instant.parse("2026-04-01T01:00:00Z"),
            Instant.parse("2026-04-01T08:00:00Z")
        );
        GamePosition mostTagged = closedPosition(
            season,
            appUser,
            302L,
            GamePointCalculator.calculatePricePoints(100),
            100,
            1,
            Instant.parse("2026-04-01T02:00:00Z"),
            Instant.parse("2026-04-01T09:00:00Z")
        );
        GamePosition mostTaggedHigherScore = closedPosition(
            season,
            appUser,
            304L,
            GamePointCalculator.calculatePricePoints(150),
            100,
            1,
            Instant.parse("2026-04-01T02:30:00Z"),
            Instant.parse("2026-04-01T09:30:00Z")
        );
        GamePosition longestHeld = closedPosition(
            season,
            appUser,
            303L,
            GamePointCalculator.calculatePricePoints(30),
            30,
            25,
            Instant.parse("2026-04-01T03:00:00Z"),
            Instant.parse("2026-04-04T03:00:00Z")
        );

        when(gameSeasonResultRepository.findRecentByUserAndRegion(7L, "KR", PageRequest.of(0, 3)))
            .thenReturn(List.of(result));
        when(gamePositionRepository.findBySeasonIdInAndUserIdOrderByCreatedAtDesc(List.of(1L), 7L))
            .thenReturn(List.of(mostTagged, mostTaggedHigherScore, longestHeld, topRankRiser));

        var response = gameService.getMySeasonResults(authenticatedUser(), "kr", 3).getFirst();

        assertThat(response.highlights().topRankRiser().positionId()).isEqualTo(301L);
        assertThat(response.highlights().topRankRiser().highlightScore()).isPositive();
        assertThat(response.highlights().mostTaggedPositions()).hasSize(1);
        assertThat(response.highlights().mostTaggedPositions().getFirst().positionId()).isEqualTo(304L);
        assertThat(response.highlights().mostTaggedPositions().getFirst().tagCount()).isGreaterThan(1);
        assertThat(response.highlights().longestHeld().positionId()).isEqualTo(303L);
        assertThat(response.highlights().longestHeld().holdDurationSeconds()).isEqualTo(259_200L);
    }

    @Test
    void getInventorySlotsUsesCurrentTierReward() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        wallet.setManualTierScoreAdjustment(15_000L);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier silverTier = tier(season, "SILVER", "실버", 5_000L, 2);
        GameSeasonTier goldTier = tier(season, "GOLD", "골드", 15_000L, 3);
        List<GameSeasonTier> tiers = List.of(bronzeTier, silverTier, goldTier);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gameTierService.resolveTier(tiers, 15_000L)).thenReturn(goldTier);
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of());

        var response = gameService.getInventorySlots(authenticatedUser(), "KR");

        assertThat(response.baseSlots()).isEqualTo(5);
        assertThat(response.totalSlots()).isEqualTo(10);
        assertThat(response.maxSlots()).isEqualTo(10);
        assertThat(response.currentTier().tierCode()).isEqualTo("GOLD");
        assertThat(response.currentTier().inventorySlots()).isEqualTo(10);
        assertThat(response.nextTier()).isNull();
        assertThat(response.tiers()).extracting("inventorySlots").containsExactly(5, 7, 10);
    }

    @Test
    void getCurrentSeasonDoesNotRegenerateSettledHighlightNotifications() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        GamePosition position = closedPosition(
            season,
            appUser,
            300L,
            GamePointCalculator.calculatePricePoints(150),
            150,
            10,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T06:00:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of(position));

        var response = gameService.getCurrentSeason(authenticatedUser(), "KR");

        assertThat(response.notifications()).isEmpty();
    }

    @Test
    void getNotificationsReturnsStoredAlertsOnly() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition moonshotPosition = closedPosition(
            season,
            appUser,
            300L,
            GamePointCalculator.calculatePricePoints(150),
            150,
            10,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T06:00:00Z")
        );
        GamePosition cashoutPosition = closedPosition(
            season,
            appUser,
            301L,
            GamePointCalculator.calculatePricePoints(100),
            100,
            40,
            Instant.parse("2026-04-01T05:39:00Z"),
            Instant.parse("2026-04-01T06:01:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(moonshotPosition, cashoutPosition));

        var response = gameService.getNotifications(authenticatedUser(), "KR");

        assertThat(response).isEmpty();
    }

    @Test
    void getNotificationsSkipsProjectedOpenPositionAlerts() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition openPosition = openPosition(
            season,
            appUser,
            "video-1",
            150,
            GamePointCalculator.calculatePricePoints(150),
            Instant.parse("2026-04-01T05:40:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(openPosition));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.of(signal("video-1", 10, 0)));

        var response = gameService.getNotifications(authenticatedUser(), "KR");

        assertThat(response).isEmpty();
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
        assertThat(response.get(0).achievedStrategyTags()).isEmpty();
        assertThat(response.get(0).targetStrategyTags()).containsExactly(GameStrategyType.SNIPE);
        assertThat(response.get(0).projectedHighlightScore()).isZero();
        assertThat(wallet.getBalancePoints()).isEqualTo(10_000L - buyPricePoints);
        assertThat(wallet.getReservedPoints()).isEqualTo(buyPricePoints);
        verify(commentService, never()).publishTradeSystemMessage(any());
    }

    @Test
    void buyCreatesSeparateOpenPositionForAdditionalQuantityOnSameVideo() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, 30_000L, buyPricePoints, 0L);
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
        assertThat(wallet.getBalancePoints()).isEqualTo(30_000L - buyPricePoints);
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
    void buyAcceptsQuantityLargerThanIntegerMaxValue() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(200);
        long largeQuantity = 3_000_000_000L;
        long expectedStakePoints = GamePointCalculator.calculatePositionPoints(buyPricePoints, largeQuantity);
        GameWallet wallet = wallet(season, appUser, expectedStakePoints + 5_000L, 0L, 0L);
        TrendSignal signal = signal("video-1", 200, 0);

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
            ReflectionTestUtils.setField(position, "id", 999L);
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-1", buyPricePoints, largeQuantity)
        );

        assertThat(response).hasSize(1);
        assertThat(response.get(0).quantity()).isEqualTo(largeQuantity);
        assertThat(response.get(0).stakePoints()).isEqualTo(expectedStakePoints);
        assertThat(wallet.getBalancePoints()).isEqualTo(5_000L);
        assertThat(wallet.getReservedPoints()).isEqualTo(expectedStakePoints);
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
        verify(commentService, never()).publishTradeSystemMessage(any());
    }

    @Test
    void sellPublishesTierPromotionFromSettledHighlightScore() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(150);
        GameWallet wallet = wallet(season, appUser, 10_000L - buyPricePoints, buyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            150,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 10, 0);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier diamondTier = tier(season, "DIAMOND", "다이아몬드", 40_000L, 5);
        GameSeasonTier masterTier = tier(season, "MASTER", "마스터", 80_000L, 6);
        List<GameSeasonTier> tiers = List.of(bronzeTier, diamondTier, masterTier);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gameTierService.resolveTier(
            org.mockito.ArgumentMatchers.eq(tiers),
            org.mockito.ArgumentMatchers.anyLong()
        )).thenAnswer(invocation -> {
            long score = invocation.getArgument(1, Long.class);
            if (score >= 80_000L) {
                return masterTier;
            }
            return score >= 40_000L ? diamondTier : bronzeTier;
        });

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(response.highlightScore()).isPositive();
        verify(commentService).publishTierSystemMessage("User 7님이 마스터 티어로 상승했습니다.");
        verify(gameNotificationService, org.mockito.Mockito.times(2))
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.anyList()
            );
        verify(gameNotificationService)
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.argThat(notifications ->
                    notifications.stream().anyMatch(notification ->
                        notification.notificationEventType() == GameNotificationEventType.TIER_PROMOTION
                            && notification.notificationType().equals("TIER_PROMOTION")
                            && notification.title().equals("티어 승급")
                            && notification.videoTitle().equals("마스터 티어 달성")
                            && notification.message().contains("마스터 티어")
                            && notification.showModal()
                    )
                )
            );
    }

    @Test
    void getCurrentSeasonPreservesStoredNotificationEventTypes() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        GameNotificationResponse storedScoreNotification = new GameNotificationResponse(
            "42",
            GameNotificationEventType.TIER_SCORE_GAIN,
            "MOONSHOT",
            "문샷 기록",
            "150위에서 잡은 영상이 10위까지 올라왔습니다.",
            300L,
            "video-1",
            "Title video-1",
            "Channel",
            "https://example.com/video-1.jpg",
            List.of(GameStrategyType.MOONSHOT),
            20_000L,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-01T06:00:00Z"),
            true
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of());
        when(gameNotificationService.syncAndListSeasonNotifications(any(GameSeason.class), any(), any()))
            .thenReturn(List.of(storedScoreNotification));

        var response = gameService.getCurrentSeason(authenticatedUser(), "KR");

        assertThat(response.notifications())
            .singleElement()
            .extracting(com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse::notificationEventType)
            .isEqualTo(GameNotificationEventType.TIER_SCORE_GAIN);
    }

    @Test
    void sellPublishesTierPromotionNotificationWithExplicitEventType() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(150);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            150,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 10, 0);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier diamondTier = tier(season, "DIAMOND", "다이아몬드", 40_000L, 5);
        GameSeasonTier masterTier = tier(season, "MASTER", "마스터", 80_000L, 6);
        List<GameSeasonTier> tiers = List.of(bronzeTier, diamondTier, masterTier);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gameTierService.resolveTier(
            org.mockito.ArgumentMatchers.eq(tiers),
            org.mockito.ArgumentMatchers.anyLong()
        )).thenAnswer(invocation -> {
            long score = invocation.getArgument(1, Long.class);
            if (score >= 80_000L) {
                return masterTier;
            }
            return score >= 40_000L ? diamondTier : bronzeTier;
        });

        gameService.sell(authenticatedUser(), 300L);

        verify(gameNotificationService)
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.argThat(notifications ->
                    notifications.stream().anyMatch(notification ->
                        notification.notificationType().equals("TIER_PROMOTION")
                            && notification.notificationEventType() == GameNotificationEventType.TIER_PROMOTION
                    )
                )
            );
    }

    @Test
    void sellPublishesTitleUnlockNotificationsWithoutModal() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(150);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            150,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 10, 0);
        AchievementTitle atlasTitle = achievementTitle("ATLAS_SEEKER", "Atlas Seeker", AchievementTitleGrade.NORMAL, 10);
        AchievementTitle sniperTitle = achievementTitle("ATLAS_SNIPER", "Atlas Sniper", AchievementTitleGrade.SUPER, 40);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameWalletRepository.save(any(GameWallet.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(achievementTitleService.grantTitlesForHighlight(any(), any())).thenReturn(List.of(atlasTitle, sniperTitle));

        var response = gameService.sell(authenticatedUser(), 300L);

        assertThat(response.highlightScore()).isPositive();
        verify(gameNotificationService).createAndPush(
            org.mockito.ArgumentMatchers.eq(appUser),
            org.mockito.ArgumentMatchers.eq(season),
            org.mockito.ArgumentMatchers.argThat(notifications ->
                notifications.size() == 2
                    && notifications.stream().allMatch(notification ->
                        notification.notificationEventType() == GameNotificationEventType.TITLE_UNLOCK
                            && notification.notificationType().equals("TITLE_UNLOCK")
                            && !notification.showModal()
                            && notification.videoId() == null
                            && notification.titleCode() != null
                            && notification.titleGrade() != null
                    )
                    && notifications.stream().map(GameNotificationResponse::titleCode).collect(java.util.stream.Collectors.toSet())
                        .equals(java.util.Set.of("ATLAS_SEEKER", "ATLAS_SNIPER"))
            )
        );
    }

    @Test
    void sellPublishesTierPromotionWhenManualAdjustmentCausesTierUpgrade() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        wallet.setManualTierScoreAdjustment(30_000L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(150);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            150,
            buyPricePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendSignal latestSignal = signal("video-1", 10, 0);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier silverTier = tier(season, "SILVER", "실버", 35_000L, 2);
        GameSeasonTier goldTier = tier(season, "GOLD", "골드", 70_000L, 3);
        List<GameSeasonTier> tiers = List.of(bronzeTier, silverTier, goldTier);

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.save(position)).thenReturn(position);
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gameTierService.resolveTier(
            org.mockito.ArgumentMatchers.eq(tiers),
            org.mockito.ArgumentMatchers.anyLong()
        )).thenAnswer(invocation -> {
            long score = invocation.getArgument(1, Long.class);
            if (score >= 70_000L) {
                return goldTier;
            }
            return score >= 35_000L ? silverTier : bronzeTier;
        });

        gameService.sell(authenticatedUser(), 300L);

        verify(gameNotificationService)
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.argThat(notifications ->
                    notifications.stream().anyMatch(notification ->
                        notification.notificationEventType() == GameNotificationEventType.TIER_PROMOTION
                            && notification.videoTitle().endsWith("티어 달성")
                            && notification.highlightScore() != null
                    )
                )
            );
    }

    @Test
    void previewSellReturnsHighlightThresholdDetailsForPartialQuantity() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long unitStakePoints = GamePointCalculator.calculatePricePoints(150);
        long totalStakePoints = GamePointCalculator.calculatePositionPoints(unitStakePoints, TWO_SHARES);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            150,
            totalStakePoints,
            Instant.parse("2026-04-01T05:45:00Z")
        );
        position.setQuantity(TWO_SHARES);
        TrendSignal latestSignal = signal("video-1", 10, 0);
        GameHighlightState state = new GameHighlightState();
        state.setSeason(season);
        state.setUser(appUser);
        state.setRootPositionId(position.getId());
        state.setBestSettledHighlightScore(10_000L);
        storedHighlightStates.add(state);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAsc(1L, 7L, "video-1", PositionStatus.OPEN))
            .thenReturn(List.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(latestSignal));

        var response = gameService.previewSell(authenticatedUser(), new SellPositionsRequest("KR", null, "video-1", ONE_SHARE));

        assertThat(response.quantity()).isEqualTo(ONE_SHARE);
        assertThat(response.projectedHighlightScore()).isPositive();
        assertThat(response.appliedHighlightScoreDelta()).isPositive();
        assertThat(response.recordEligibleCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).bestHighlightScore()).isEqualTo(10_000L);
        assertThat(response.items().get(0).projectedHighlightScore()).isEqualTo(response.projectedHighlightScore());
        assertThat(response.items().get(0).appliedHighlightScoreDelta())
            .isEqualTo(response.projectedHighlightScore() - 10_000L);
        assertThat(response.items().get(0).willUpdateRecord()).isTrue();
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
    void partialSellKeepsOriginalPositionLineageForHighlightScoring() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long unitBuyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long totalBuyPricePoints = GamePointCalculator.calculatePositionPoints(unitBuyPricePoints, TWO_SHARES);
        GameWallet wallet = wallet(season, appUser, 20_000L - totalBuyPricePoints, totalBuyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            totalBuyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        position.setQuantity(TWO_SHARES);
        ReflectionTestUtils.setField(position, "id", 301L);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findByIdAndUserIdForUpdate(301L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.of(signal("video-1", 120, 0)));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of(position));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameService.sell(authenticatedUser(), new SellPositionsRequest("KR", 301L, null, ONE_SHARE));

        verify(gamePositionRepository).save(argThat(savedPosition ->
            savedPosition != position
                && Long.valueOf(301L).equals(savedPosition.getOriginPositionId())
                && savedPosition.getStatus() == PositionStatus.CLOSED
        ));
    }

    @Test
    void partialSellReturnsHighlightScoreForClosedSplitPosition() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long unitBuyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long totalBuyPricePoints = GamePointCalculator.calculatePositionPoints(unitBuyPricePoints, TWO_SHARES);
        GameWallet wallet = wallet(season, appUser, 20_000L - totalBuyPricePoints, totalBuyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            totalBuyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        position.setQuantity(TWO_SHARES);
        ReflectionTestUtils.setField(position, "id", 301L);

        java.util.concurrent.atomic.AtomicReference<GamePosition> closedSplitRef = new java.util.concurrent.atomic.AtomicReference<>();

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findByIdAndUserIdForUpdate(301L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.of(signal("video-1", 120, 0)));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of(position));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition savedPosition = invocation.getArgument(0, GamePosition.class);
            if (savedPosition != position && savedPosition.getId() == null) {
                ReflectionTestUtils.setField(savedPosition, "id", 302L);
                closedSplitRef.set(savedPosition);
            }
            return savedPosition;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.sell(
            authenticatedUser(),
            new SellPositionsRequest("KR", 301L, null, ONE_SHARE)
        );

        GamePosition closedSplit = closedSplitRef.get();
        assertThat(closedSplit).isNotNull();
        assertThat(response).hasSize(1);
        assertThat(response.get(0).highlightScore()).isEqualTo(highlightScore(closedSplit));
    }

    @Test
    void splitSellPushesHighlightNotificationWhenSellSetsNewRecordedHighScore() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long unitBuyPricePoints = GamePointCalculator.calculatePricePoints(170);
        long totalBuyPricePoints = GamePointCalculator.calculatePositionPoints(unitBuyPricePoints, TWO_SHARES);
        GameWallet wallet = wallet(season, appUser, 20_000L - totalBuyPricePoints, totalBuyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            totalBuyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        position.setQuantity(TWO_SHARES);
        ReflectionTestUtils.setField(position, "id", 301L);

        java.util.concurrent.atomic.AtomicReference<GamePosition> closedSplitRef = new java.util.concurrent.atomic.AtomicReference<>();

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findByIdAndUserIdForUpdate(301L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(
            Optional.of(signal("video-1", 110, 0)),
            Optional.of(signal("video-1", 120, 0))
        );
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenAnswer(invocation -> {
            GamePosition closedSplit = closedSplitRef.get();
            if (closedSplit == null) {
                return List.of(position);
            }

            if (position.getStatus() == PositionStatus.OPEN) {
                return List.of(closedSplit, position);
            }

            return List.of(position, closedSplit);
        });
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition savedPosition = invocation.getArgument(0, GamePosition.class);
            if (savedPosition != position && savedPosition.getId() == null) {
                ReflectionTestUtils.setField(savedPosition, "id", 302L);
                closedSplitRef.set(savedPosition);
            }
            return savedPosition;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameService.sell(authenticatedUser(), new SellPositionsRequest("KR", 301L, null, ONE_SHARE));
        gameService.sell(authenticatedUser(), 301L);

        verify(gameNotificationService, org.mockito.Mockito.times(1))
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.argThat(notifications ->
                    notifications.stream().anyMatch(notification ->
                        "game-302".equals(notification.id())
                    )
                )
            );
    }

    @Test
    void splitSellPushesHighlightNotificationWhenSameRootTagImprovesScore() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long unitBuyPricePoints = GamePointCalculator.calculatePricePoints(140);
        long totalBuyPricePoints = GamePointCalculator.calculatePositionPoints(unitBuyPricePoints, TWO_SHARES);
        GameWallet wallet = wallet(season, appUser, 50_000L - totalBuyPricePoints, totalBuyPricePoints, 0L);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            140,
            totalBuyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        position.setQuantity(TWO_SHARES);
        ReflectionTestUtils.setField(position, "id", 301L);

        java.util.concurrent.atomic.AtomicReference<GamePosition> closedSplitRef =
            new java.util.concurrent.atomic.AtomicReference<>();

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findByIdAndUserIdForUpdate(301L, 7L)).thenReturn(Optional.of(position));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(
            Optional.of(signal("video-1", 80, 0)),
            Optional.of(signal("video-1", 70, 0))
        );
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenAnswer(invocation -> {
            GamePosition closedSplit = closedSplitRef.get();
            if (closedSplit == null) {
                return List.of(position);
            }

            if (position.getStatus() == PositionStatus.OPEN) {
                return List.of(closedSplit, position);
            }

            return List.of(position, closedSplit);
        });
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition savedPosition = invocation.getArgument(0, GamePosition.class);
            if (savedPosition != position && savedPosition.getId() == null) {
                ReflectionTestUtils.setField(savedPosition, "id", 302L);
                closedSplitRef.set(savedPosition);
            }
            return savedPosition;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        gameService.sell(authenticatedUser(), new SellPositionsRequest("KR", 301L, null, ONE_SHARE));
        gameService.sell(authenticatedUser(), 301L);

        verify(gameNotificationService)
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.argThat(notifications ->
                    notifications.stream().anyMatch(notification ->
                        "game-302".equals(notification.id())
                    )
                )
            );
        verify(gameNotificationService)
            .createAndPush(
                org.mockito.ArgumentMatchers.eq(appUser),
                org.mockito.ArgumentMatchers.eq(season),
                org.mockito.ArgumentMatchers.argThat(notifications ->
                    notifications.stream().anyMatch(notification ->
                        "game-301".equals(notification.id())
                            && notification.highlightScore() != null
                            && notification.highlightScore() > 0L
                    )
                )
            );
    }

    @Test
    void publishProjectedHighlightNotificationsPushesWhenProjectedScoreBeatsRecordedHighScore() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition openPosition = openPosition(
            season,
            appUser,
            "video-1",
            150,
            GamePointCalculator.calculatePricePoints(150),
            Instant.parse("2026-04-01T05:40:00Z")
        );
        GamePosition settledPosition = closedPosition(
            season,
            appUser,
            301L,
            GamePointCalculator.calculatePricePoints(150),
            150,
            60,
            Instant.parse("2026-04-01T05:20:00Z"),
            Instant.parse("2026-04-01T05:30:00Z")
        );
        settledPosition.setOriginPositionId(300L);
        openPosition.setOriginPositionId(300L);
        ReflectionTestUtils.setField(openPosition, "id", 300L);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonId(1L)).thenReturn(List.of(openPosition, settledPosition));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN)).thenReturn(List.of(openPosition));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(signal("video-1", 10, 0)));

        gameService.publishProjectedHighlightNotifications("KR");

        verify(gameNotificationService).createAndPushPositionSnapshot(
            org.mockito.ArgumentMatchers.eq(openPosition),
            org.mockito.ArgumentMatchers.eq(10),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.anyMap()
        );
    }

    @Test
    void publishProjectedHighlightNotificationsPushesWhenSameRootTagImprovesScore() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(140);
        GamePosition openPosition = openPosition(
            season,
            appUser,
            "video-1",
            140,
            buyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );
        ReflectionTestUtils.setField(openPosition, "id", 300L);
        GamePosition settledPosition = closedPosition(
            season,
            appUser,
            301L,
            buyPricePoints,
            140,
            80,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T05:50:00Z")
        );
        settledPosition.setOriginPositionId(300L);
        settledPosition.setVideoId("video-1");

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndStatus(1L, PositionStatus.OPEN)).thenReturn(List.of(openPosition));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(settledPosition, openPosition));
        when(trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc("KR", "0"))
            .thenReturn(List.of(signal("video-1", 70, 0)));

        gameService.publishProjectedHighlightNotifications("KR");

        verify(gameNotificationService).createAndPushPositionSnapshot(
            org.mockito.ArgumentMatchers.eq(openPosition),
            org.mockito.ArgumentMatchers.eq(70),
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(Instant.class),
            org.mockito.ArgumentMatchers.argThat(tags -> tags.contains(GameStrategyType.SMALL_CASHOUT)),
            org.mockito.ArgumentMatchers.<java.util.Map<GameStrategyType, Long>>argThat(scoreGains ->
                scoreGains.getOrDefault(GameStrategyType.SMALL_CASHOUT, 0L) > 0L
            )
        );
    }

    @Test
    void calculateSettledUserHighlightScoreCountsSplitPositionOnlyOnce() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition firstSplit = closedPosition(
            season,
            appUser,
            301L,
            900L,
            170,
            120,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T06:00:00Z")
        );
        firstSplit.setOriginPositionId(300L);
        GamePosition secondSplit = closedPosition(
            season,
            appUser,
            302L,
            900L,
            170,
            110,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T06:05:00Z")
        );
        secondSplit.setOriginPositionId(300L);
        GamePosition standalone = closedPosition(
            season,
            appUser,
            303L,
            900L,
            160,
            100,
            Instant.parse("2026-04-01T05:30:00Z"),
            Instant.parse("2026-04-01T06:10:00Z")
        );

        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(secondSplit, firstSplit, standalone));

        long firstSplitScore = highlightScore(firstSplit);
        long secondSplitScore = highlightScore(secondSplit);
        long standaloneScore = highlightScore(standalone);

        assertThat(gameService.calculateSettledUserHighlightScore(1L, 7L))
            .isEqualTo(Math.max(firstSplitScore, secondSplitScore) + standaloneScore);
    }

    @Test
    void getHighlightsKeepsOnlyBestSplitHighlightPerOriginPosition() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition firstSplit = closedPosition(
            season,
            appUser,
            301L,
            900L,
            170,
            120,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T06:00:00Z")
        );
        firstSplit.setOriginPositionId(300L);
        GamePosition secondSplit = closedPosition(
            season,
            appUser,
            302L,
            900L,
            170,
            110,
            Instant.parse("2026-04-01T05:40:00Z"),
            Instant.parse("2026-04-01T06:05:00Z")
        );
        secondSplit.setOriginPositionId(300L);
        GamePosition standalone = closedPosition(
            season,
            appUser,
            303L,
            900L,
            160,
            100,
            Instant.parse("2026-04-01T05:30:00Z"),
            Instant.parse("2026-04-01T06:10:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(secondSplit, firstSplit, standalone));

        var response = gameService.getHighlights(authenticatedUser(), "KR");

        assertThat(response).hasSize(2);
        assertThat(response).extracting(highlight -> highlight.positionId())
            .containsExactly(303L, 302L);
    }

    @Test
    void getHighlightsExcludesOpenPositions() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GamePosition openPosition = openPosition(
            season,
            appUser,
            "video-1",
            150,
            GamePointCalculator.calculatePricePoints(150),
            Instant.parse("2026-04-01T05:40:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(openPosition));

        var response = gameService.getHighlights(authenticatedUser(), "KR");

        assertThat(response).isEmpty();
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
    void sellRejectsFullPositionSellWhenPositionHasPendingScheduledSellQuantity() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GamePosition position = openPosition(
            season,
            appUser,
            "video-1",
            170,
            buyPricePoints,
            Instant.parse("2026-04-01T05:40:00Z")
        );

        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(gameScheduledSellOrderRepository.sumQuantityByPositionIdAndStatus(300L, ScheduledSellOrderStatus.PENDING))
            .thenReturn(GamePointCalculator.QUANTITY_SCALE);

        assertThatThrownBy(() -> gameService.sell(authenticatedUser(), 300L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("예약 매도 수량이 남아 있습니다. 남은 수량만 수동 매도할 수 있습니다.");
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
            Instant.parse("2026-04-01T05:59:30Z")
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
    void buyUsesExpandedInventorySlotsForDistinctVideoLimit() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        long buyPricePoints = GamePointCalculator.calculatePricePoints(170);
        GameWallet wallet = wallet(season, appUser, buyPricePoints, 0L, 0L);
        wallet.setManualTierScoreAdjustment(5_000L);
        TrendSignal signal = signal("video-6", 170, 0);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier silverTier = tier(season, "SILVER", "실버", 5_000L, 2);
        List<GameSeasonTier> tiers = List.of(bronzeTier, silverTier);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserIdForUpdate(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(1L, 7L, "video-6", PositionStatus.OPEN))
            .thenReturn(List.of());
        when(gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(1L, 7L, PositionStatus.OPEN))
            .thenReturn((long) season.getMaxOpenPositions());
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gameTierService.resolveTier(tiers, 5_000L)).thenReturn(silverTier);
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of());
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-6"))).thenReturn(Optional.of(signal));
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(appUser));
        when(gamePositionRepository.save(any(GamePosition.class))).thenAnswer(invocation -> {
            GamePosition position = invocation.getArgument(0, GamePosition.class);
            ReflectionTestUtils.setField(position, "id", 306L);
            return position;
        });
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(gameLedgerRepository.save(any(GameLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = gameService.buy(
            authenticatedUser(),
            new CreatePositionRequest("KR", "0", "video-6", buyPricePoints, ONE_SHARE)
        );

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().videoId()).isEqualTo("video-6");
        assertThat(wallet.getBalancePoints()).isZero();
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
    void calculateProfitPointsHighlightBonusUsesThresholdAndLogSoftCap() {
        assertThat(GameService.calculateProfitPointsHighlightBonus(null)).isZero();
        assertThat(GameService.calculateProfitPointsHighlightBonus(4_999L)).isZero();
        assertThat(GameService.calculateProfitPointsHighlightBonus(5_000L)).isZero();
        assertThat(GameService.calculateProfitPointsHighlightBonus(30_000L)).isEqualTo(24L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(100_000L)).isEqualTo(93L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(1_000_000L)).isEqualTo(855L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(100_000_000L)).isEqualTo(7_555L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(400_005_000L)).isEqualTo(8_914L);
        assertThat(GameService.calculateProfitPointsHighlightBonus(Long.MAX_VALUE)).isLessThan(15_000L);
    }

    @Test
    void getCurrentTierReturnsCurrentAndNextTierFromHighlightScore() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier silverTier = tier(season, "SILVER", "실버", 5_000L, 2);
        GameSeasonTier goldTier = tier(season, "GOLD", "골드", 10_000L, 3);
        GameSeasonTier platinumTier = tier(season, "PLATINUM", "플래티넘", 30_000L, 4);
        GameSeasonTier diamondTier = tier(season, "DIAMOND", "다이아몬드", 120_000L, 5);
        GameSeasonTier masterTier = tier(season, "MASTER", "마스터", 500_000L, 6);
        GameSeasonTier legendTier = tier(season, "LEGEND", "레전드", 12_600_000L, 7);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameTierService.getOrCreateTiers(season))
            .thenReturn(List.of(bronzeTier, silverTier, goldTier, platinumTier, diamondTier, masterTier, legendTier));
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L)).thenReturn(List.of());
        when(gameTierService.resolveTier(
            List.of(bronzeTier, silverTier, goldTier, platinumTier, diamondTier, masterTier, legendTier),
            0L
        ))
            .thenReturn(bronzeTier);

        var response = gameService.getCurrentTier(authenticatedUser(), "KR");

        assertThat(response.seasonId()).isEqualTo(1L);
        assertThat(response.highlightScore()).isZero();
        assertThat(response.calculatedHighlightScore()).isZero();
        assertThat(response.manualTierScoreAdjustment()).isZero();
        assertThat(response.currentTier().tierCode()).isEqualTo("BRONZE");
        assertThat(response.currentTier().displayName()).isEqualTo("브론즈");
        assertThat(response.nextTier().tierCode()).isEqualTo("SILVER");
        assertThat(response.tiers()).hasSize(7);
    }

    @Test
    void getCurrentTierIgnoresOpenPositionHighlightsUntilSold() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        GamePosition openMoonshotPosition = openPosition(
            season,
            appUser,
            "video-1",
            150,
            GamePointCalculator.calculatePricePoints(150),
            Instant.parse("2026-04-01T05:40:00Z")
        );
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier silverTier = tier(season, "SILVER", "실버", 5_000L, 2);
        List<GameSeasonTier> tiers = List.of(bronzeTier, silverTier);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(openMoonshotPosition));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.of(signal("video-1", 10, 0)));
        when(gameTierService.resolveTier(tiers, 0L)).thenReturn(bronzeTier);

        var response = gameService.getCurrentTier(authenticatedUser(), "KR");

        assertThat(response.highlightScore()).isZero();
        assertThat(response.calculatedHighlightScore()).isZero();
        assertThat(response.currentTier().tierCode()).isEqualTo("BRONZE");
    }

    @Test
    void getCurrentTierCountsHighlightScoreAfterPositionIsSold() {
        GameSeason season = activeSeason();
        AppUser appUser = user(7L);
        GameWallet wallet = wallet(season, appUser, 10_000L, 0L, 0L);
        GamePosition closedMoonshotPosition = openPosition(
            season,
            appUser,
            "video-1",
            150,
            GamePointCalculator.calculatePricePoints(150),
            Instant.parse("2026-04-01T05:40:00Z")
        );
        long sellPricePoints = GamePointCalculator.calculatePricePoints(10);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        closedMoonshotPosition.setStatus(PositionStatus.CLOSED);
        closedMoonshotPosition.setSellRunId(55L);
        closedMoonshotPosition.setSellRank(10);
        closedMoonshotPosition.setSellCapturedAt(Instant.parse("2026-04-01T06:00:00Z"));
        closedMoonshotPosition.setSettledPoints(settledPoints);
        closedMoonshotPosition.setPnlPoints(GamePointCalculator.calculateProfitPoints(
            closedMoonshotPosition.getStakePoints(),
            settledPoints
        ));
        closedMoonshotPosition.setClosedAt(Instant.parse("2026-04-01T06:01:00Z"));
        GameSeasonTier bronzeTier = tier(season, "BRONZE", "브론즈", 0L, 1);
        GameSeasonTier silverTier = tier(season, "SILVER", "실버", 5_000L, 2);
        List<GameSeasonTier> tiers = List.of(bronzeTier, silverTier);

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gameTierService.getOrCreateTiers(season)).thenReturn(tiers);
        when(gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(1L, 7L))
            .thenReturn(List.of(closedMoonshotPosition));
        when(gameTierService.resolveTier(
            org.mockito.ArgumentMatchers.eq(tiers),
            org.mockito.ArgumentMatchers.longThat(score -> score > 0L)
        ))
            .thenReturn(silverTier);

        var response = gameService.getCurrentTier(authenticatedUser(), "KR");

        assertThat(response.highlightScore()).isPositive();
        assertThat(response.calculatedHighlightScore()).isPositive();
        assertThat(response.currentTier().tierCode()).isEqualTo("SILVER");
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

    @Test
    void getLeaderboardPositionRankHistoryReturnsOtherUsersPositionInCurrentSeason() {
        GameSeason season = activeSeason();
        AppUser requester = user(7L);
        AppUser rival = user(9L);
        GameWallet wallet = wallet(season, requester, 10_000L, 0L, 0L);
        GamePosition position = openPosition(
            season,
            rival,
            "video-1",
            170,
            GamePointCalculator.calculatePricePoints(170),
            Instant.parse("2026-04-01T05:45:00Z")
        );
        TrendRun buyRun = trendRun(11L, Instant.parse("2026-04-01T05:40:00Z"));
        TrendRun latestRun = trendRun(12L, Instant.parse("2026-04-01T06:00:00Z"));
        TrendSignal latestSignal = signal("video-1", 160, 0);
        latestSignal.setCurrentRunId(12L);
        latestSignal.setCapturedAt(latestRun.getCapturedAt());

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findByIdAndUserId(300L, 9L)).thenReturn(Optional.of(position));
        when(trendSnapshotRepository.findFirstByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc("KR", "0", "video-1"))
            .thenReturn(Optional.of(snapshot(buyRun, "video-1", 170)));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1")))
            .thenReturn(Optional.of(latestSignal));
        when(trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc("KR", "0", 11L, 12L))
            .thenReturn(List.of(buyRun, latestRun));
        when(trendSnapshotRepository.findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
            "KR",
            "0",
            "video-1",
            11L,
            12L
        )).thenReturn(List.of(
            snapshot(buyRun, "video-1", 170),
            snapshot(latestRun, "video-1", 160)
        ));

        var response = gameService.getLeaderboardPositionRankHistory(authenticatedUser(), 9L, 300L, "KR");

        assertThat(response.positionId()).isEqualTo(300L);
        assertThat(response.buyRank()).isEqualTo(170);
        assertThat(response.latestRank()).isEqualTo(160);
        assertThat(response.points()).hasSize(2);
        verify(gameWalletRepository, never()).saveAndFlush(any(GameWallet.class));
    }

    @Test
    void getLeaderboardPositionRankHistoryRejectsPositionOutsideCurrentSeason() {
        GameSeason currentSeason = activeSeason();
        GameSeason oldSeason = activeSeason();
        ReflectionTestUtils.setField(oldSeason, "id", 2L);
        oldSeason.setStartAt(Instant.parse("2026-03-20T00:00:00Z"));
        oldSeason.setEndAt(Instant.parse("2026-03-27T00:00:00Z"));

        AppUser requester = user(7L);
        AppUser rival = user(9L);
        GameWallet wallet = wallet(currentSeason, requester, 10_000L, 0L, 0L);
        GamePosition position = openPosition(
            oldSeason,
            rival,
            "video-1",
            170,
            GamePointCalculator.calculatePricePoints(170),
            Instant.parse("2026-03-21T05:45:00Z")
        );

        when(gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, "KR"))
            .thenReturn(Optional.of(currentSeason));
        when(gameWalletRepository.findBySeasonIdAndUserId(1L, 7L)).thenReturn(Optional.of(wallet));
        when(gamePositionRepository.findByIdAndUserId(300L, 9L)).thenReturn(Optional.of(position));

        assertThatThrownBy(() -> gameService.getLeaderboardPositionRankHistory(authenticatedUser(), 9L, 300L, "KR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("현재 시즌 포지션만 조회할 수 있습니다.");
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
        season.setMinHoldSeconds(60);
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

    private GameSeasonResult seasonResult(GameSeason season, AppUser appUser) {
        GameSeasonResult result = new GameSeasonResult();
        ReflectionTestUtils.setField(result, "id", 900L);
        result.setSeason(season);
        result.setUser(appUser);
        result.setRegionCode(season.getRegionCode());
        result.setSeasonName(season.getName());
        result.setSeasonStartAt(season.getStartAt());
        result.setSeasonEndAt(season.getEndAt());
        result.setFinalRank(1);
        result.setFinalAssetPoints(20_000L);
        result.setFinalBalancePoints(20_000L);
        result.setRealizedPnlPoints(10_000L);
        result.setStartingBalancePoints(season.getStartingBalancePoints());
        result.setProfitRatePercent(100D);
        result.setFinalHighlightScore(0L);
        result.setPositionCount(3L);
        result.setCreatedAt(Instant.parse("2026-04-08T00:01:00Z"));
        return result;
    }

    private GameSeasonTier tier(GameSeason season, String tierCode, String displayName, long minScore, int sortOrder) {
        GameSeasonTier tier = new GameSeasonTier();
        ReflectionTestUtils.setField(tier, "id", (long) sortOrder);
        tier.setSeason(season);
        tier.setTierCode(tierCode);
        tier.setDisplayName(displayName);
        tier.setMinScore(minScore);
        tier.setBadgeCode("badge-" + tierCode.toLowerCase());
        tier.setTitleCode("title-" + tierCode.toLowerCase());
        tier.setProfileThemeCode(tierCode.toLowerCase());
        tier.setInventorySlots(inventorySlotsForTier(tierCode));
        tier.setSortOrder(sortOrder);
        tier.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return tier;
    }

    private int inventorySlotsForTier(String tierCode) {
        return switch (tierCode) {
            case "SILVER" -> 7;
            case "GOLD" -> 10;
            case "PLATINUM" -> 12;
            case "DIAMOND" -> 15;
            case "MASTER" -> 20;
            case "LEGEND" -> 20;
            default -> 5;
        };
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

    private GamePosition closedPosition(
        GameSeason season,
        AppUser appUser,
        Long positionId,
        long stakePoints,
        int buyRank,
        int sellRank,
        Instant createdAt,
        Instant closedAt
    ) {
        GamePosition position = openPosition(season, appUser, "video-" + positionId, buyRank, stakePoints, createdAt);
        ReflectionTestUtils.setField(position, "id", positionId);
        long sellPricePoints = GamePointCalculator.calculatePricePoints(sellRank);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        position.setStatus(PositionStatus.CLOSED);
        position.setSellRunId(55L);
        position.setSellRank(sellRank);
        position.setSellCapturedAt(closedAt);
        position.setRankDiff(buyRank - sellRank);
        position.setPnlPoints(GamePointCalculator.calculateProfitPoints(stakePoints, settledPoints));
        position.setSettledPoints(settledPoints);
        position.setClosedAt(closedAt);
        return position;
    }

    private long highlightScore(GamePosition position) {
        Object highlight = ReflectionTestUtils.invokeMethod(gameService, "toSettledGameHighlightResponse", position);
        return GameService.calculateHighlightScore((com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse) highlight);
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

    private AchievementTitle achievementTitle(
        String code,
        String displayName,
        AchievementTitleGrade grade,
        int sortOrder
    ) {
        AchievementTitle title = new AchievementTitle();
        ReflectionTestUtils.setField(title, "id", (long) sortOrder);
        title.setCode(code);
        title.setDisplayName(displayName);
        title.setShortName(displayName);
        title.setGrade(grade);
        title.setDescription(displayName + " description");
        title.setSortOrder(sortOrder);
        title.setEnabled(true);
        title.setCreatedAt(Instant.parse("2026-04-01T06:00:00Z"));
        title.setUpdatedAt(Instant.parse("2026-04-01T06:00:00Z"));
        return title;
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
