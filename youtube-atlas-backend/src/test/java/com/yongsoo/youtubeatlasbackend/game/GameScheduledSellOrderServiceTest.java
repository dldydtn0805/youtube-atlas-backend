package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.game.api.CreateScheduledSellOrderRequest;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;

class GameScheduledSellOrderServiceTest {

    private static final int ONE_SHARE = GamePointCalculator.QUANTITY_SCALE;

    private GameScheduledSellOrderRepository gameScheduledSellOrderRepository;
    private GameSeasonRepository gameSeasonRepository;
    private GamePositionRepository gamePositionRepository;
    private TrendSignalRepository trendSignalRepository;
    private GameService gameService;
    private GameScheduledSellOrderService service;

    @BeforeEach
    void setUp() {
        gameScheduledSellOrderRepository = org.mockito.Mockito.mock(GameScheduledSellOrderRepository.class);
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        trendSignalRepository = org.mockito.Mockito.mock(TrendSignalRepository.class);
        gameService = org.mockito.Mockito.mock(GameService.class);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);

        service = new GameScheduledSellOrderService(
            gameScheduledSellOrderRepository,
            gameSeasonRepository,
            gamePositionRepository,
            trendSignalRepository,
            gameService,
            fixedClock
        );
        when(gameScheduledSellOrderRepository.save(org.mockito.ArgumentMatchers.any(GameScheduledSellOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createRejectsWhenPendingOrdersReserveAllQuantity() {
        GamePosition position = openPosition();
        when(gamePositionRepository.findByIdAndUserIdForUpdate(300L, 7L)).thenReturn(Optional.of(position));
        when(gameScheduledSellOrderRepository.sumQuantityByPositionIdAndStatus(300L, ScheduledSellOrderStatus.PENDING))
            .thenReturn(ONE_SHARE);

        assertThatThrownBy(() -> service.create(
            authenticatedUser(),
            new CreateScheduledSellOrderRequest(300L, 10, ONE_SHARE, ScheduledSellTriggerDirection.RANK_IMPROVES_TO)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("예약 매도 수량이 보유 수량을 초과했습니다.");
    }

    @Test
    void executeTriggeredOrdersSellsAndMarksOrderExecutedWhenRankReachesTarget() {
        GameScheduledSellOrder order = pendingOrder(openPosition(), 10);
        TrendSignal signal = signal("video-1", 10);

        when(gameScheduledSellOrderRepository.findByRegionCodeAndStatusOrderByCreatedAtAsc("KR", ScheduledSellOrderStatus.PENDING))
            .thenReturn(List.of(order));
        when(gameScheduledSellOrderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal));
        when(gameService.sellScheduledPosition(7L, 300L, ONE_SHARE))
            .thenReturn(new SellPositionResponse(
                300L,
                "video-1",
                120,
                10,
                110,
                ONE_SHARE,
                1_000L,
                2_000L,
                994L,
                1_994L,
                10_000L,
                11_994L,
                Instant.parse("2026-04-01T06:00:00Z")
            ));

        service.executeTriggeredOrders("KR");

        verify(gameService).sellScheduledPosition(7L, 300L, ONE_SHARE);
        assertThat(order.getStatus()).isEqualTo(ScheduledSellOrderStatus.EXECUTED);
        assertThat(order.getSellPricePoints()).isEqualTo(2_000L);
        assertThat(order.getSettledPoints()).isEqualTo(1_994L);
        assertThat(order.getPnlPoints()).isEqualTo(994L);
        assertThat(order.getTriggeredAt()).isEqualTo(Instant.parse("2026-04-01T06:00:00Z"));
        assertThat(order.getExecutedAt()).isEqualTo(Instant.parse("2026-04-01T06:00:00Z"));
    }

    @Test
    void executeTriggeredOrdersWaitsForDropTriggerUntilRankFallsToTarget() {
        GameScheduledSellOrder order = pendingOrder(openPosition(), 180);
        order.setTriggerDirection(ScheduledSellTriggerDirection.RANK_DROPS_TO);

        when(gameScheduledSellOrderRepository.findByRegionCodeAndStatusOrderByCreatedAtAsc("KR", ScheduledSellOrderStatus.PENDING))
            .thenReturn(List.of(order));
        when(gameScheduledSellOrderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal("video-1", 110)));

        service.executeTriggeredOrders("KR");

        org.mockito.Mockito.verifyNoInteractions(gameService);
        assertThat(order.getStatus()).isEqualTo(ScheduledSellOrderStatus.PENDING);
    }

    @Test
    void executeTriggeredOrdersSellsWhenDropTriggerRankFallsToTarget() {
        GameScheduledSellOrder order = pendingOrder(openPosition(), 180);
        order.setTriggerDirection(ScheduledSellTriggerDirection.RANK_DROPS_TO);

        when(gameScheduledSellOrderRepository.findByRegionCodeAndStatusOrderByCreatedAtAsc("KR", ScheduledSellOrderStatus.PENDING))
            .thenReturn(List.of(order));
        when(gameScheduledSellOrderRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(order));
        when(trendSignalRepository.findById(new TrendSignalId("KR", "0", "video-1"))).thenReturn(Optional.of(signal("video-1", 180)));
        when(gameService.sellScheduledPosition(7L, 300L, ONE_SHARE))
            .thenReturn(new SellPositionResponse(
                300L,
                "video-1",
                120,
                180,
                -60,
                ONE_SHARE,
                1_000L,
                700L,
                -303L,
                697L,
                0L,
                10_697L,
                Instant.parse("2026-04-01T06:00:00Z")
            ));

        service.executeTriggeredOrders("KR");

        verify(gameService).sellScheduledPosition(7L, 300L, ONE_SHARE);
        assertThat(order.getStatus()).isEqualTo(ScheduledSellOrderStatus.EXECUTED);
        assertThat(order.getSettledPoints()).isEqualTo(697L);
    }

    private AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(7L, "atlas@example.com", "Atlas User", null);
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

    private AppUser user() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setDisplayName("Atlas User");
        return user;
    }

    private GamePosition openPosition() {
        GamePosition position = new GamePosition();
        ReflectionTestUtils.setField(position, "id", 300L);
        position.setSeason(activeSeason());
        position.setUser(user());
        position.setRegionCode("KR");
        position.setCategoryId("0");
        position.setVideoId("video-1");
        position.setTitle("Title video-1");
        position.setChannelTitle("Channel");
        position.setThumbnailUrl("https://example.com/video-1.jpg");
        position.setBuyRunId(11L);
        position.setBuyRank(170);
        position.setBuyCapturedAt(Instant.parse("2026-04-01T05:40:00Z"));
        position.setQuantity(ONE_SHARE);
        position.setStakePoints(GamePointCalculator.calculatePricePoints(170));
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(Instant.parse("2026-04-01T05:40:00Z"));
        return position;
    }

    private GameScheduledSellOrder pendingOrder(GamePosition position, int targetRank) {
        GameScheduledSellOrder order = new GameScheduledSellOrder();
        ReflectionTestUtils.setField(order, "id", 500L);
        order.setSeason(position.getSeason());
        order.setUser(position.getUser());
        order.setPosition(position);
        order.setRegionCode(position.getRegionCode());
        order.setTargetRank(targetRank);
        order.setQuantity(ONE_SHARE);
        order.setStatus(ScheduledSellOrderStatus.PENDING);
        order.setCreatedAt(Instant.parse("2026-04-01T05:50:00Z"));
        order.setUpdatedAt(Instant.parse("2026-04-01T05:50:00Z"));
        return order;
    }

    private TrendSignal signal(String videoId, int currentRank) {
        TrendSignal signal = new TrendSignal();
        signal.setId(new TrendSignalId("KR", "0", videoId));
        signal.setCurrentRunId(55L);
        signal.setCurrentRank(currentRank);
        signal.setRankChange(0);
        signal.setCapturedAt(Instant.parse("2026-04-01T06:00:00Z"));
        return signal;
    }
}
