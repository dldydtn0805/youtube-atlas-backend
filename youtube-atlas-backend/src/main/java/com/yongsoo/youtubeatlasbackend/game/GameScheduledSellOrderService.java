package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.game.api.CreateScheduledSellOrderRequest;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.ScheduledSellOrderResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;

@Service
public class GameScheduledSellOrderService {

    private final GameScheduledSellOrderRepository gameScheduledSellOrderRepository;
    private final GameSeasonRepository gameSeasonRepository;
    private final GamePositionRepository gamePositionRepository;
    private final TrendSignalRepository trendSignalRepository;
    private final GameService gameService;
    private final Clock clock;

    public GameScheduledSellOrderService(
        GameScheduledSellOrderRepository gameScheduledSellOrderRepository,
        GameSeasonRepository gameSeasonRepository,
        GamePositionRepository gamePositionRepository,
        TrendSignalRepository trendSignalRepository,
        GameService gameService,
        Clock clock
    ) {
        this.gameScheduledSellOrderRepository = gameScheduledSellOrderRepository;
        this.gameSeasonRepository = gameSeasonRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.trendSignalRepository = trendSignalRepository;
        this.gameService = gameService;
        this.clock = clock;
    }

    @Transactional
    public ScheduledSellOrderResponse create(
        AuthenticatedUser authenticatedUser,
        CreateScheduledSellOrderRequest request
    ) {
        long quantity = normalizeQuantity(request.quantity());
        ScheduledSellTriggerType triggerType = resolveRequestTriggerType(request);
        Integer targetRank = triggerType == ScheduledSellTriggerType.RANK
            ? normalizeTargetRank(request.targetRank())
            : null;
        Double targetProfitRatePercent = triggerType == ScheduledSellTriggerType.PROFIT_RATE
            ? normalizeTargetProfitRatePercent(request.targetProfitRatePercent())
            : null;
        ScheduledSellTriggerDirection triggerDirection = request.triggerDirection() != null
            ? request.triggerDirection()
            : ScheduledSellTriggerDirection.RANK_IMPROVES_TO;
        GamePosition position = gamePositionRepository.findByIdAndUserIdForUpdate(
            request.positionId(),
            authenticatedUser.id()
        ).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));
        ensureOpenActivePosition(position);
        ensureConditionNotAlreadyMet(position, quantity, triggerType, targetRank, targetProfitRatePercent, triggerDirection);
        long pendingQuantity = gameScheduledSellOrderRepository.sumQuantityByPositionIdAndStatus(
            position.getId(),
            ScheduledSellOrderStatus.PENDING
        );
        if (quantity > getPositionQuantity(position) - pendingQuantity) {
            throw new IllegalArgumentException("예약 매도 수량이 보유 수량을 초과했습니다.");
        }

        Instant now = Instant.now(clock);
        GameScheduledSellOrder order = new GameScheduledSellOrder();
        order.setSeason(position.getSeason());
        order.setUser(position.getUser());
        order.setPosition(position);
        order.setRegionCode(position.getRegionCode());
        order.setTriggerType(triggerType);
        order.setTargetRank(targetRank);
        order.setTargetProfitRatePercent(targetProfitRatePercent);
        order.setTriggerDirection(triggerDirection);
        order.setQuantity(quantity);
        order.setStatus(ScheduledSellOrderStatus.PENDING);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return toResponse(gameScheduledSellOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public List<ScheduledSellOrderResponse> list(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        return gameScheduledSellOrderRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(
            season.getId(),
            authenticatedUser.id()
        ).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public ScheduledSellOrderResponse cancel(AuthenticatedUser authenticatedUser, Long orderId) {
        GameScheduledSellOrder order = gameScheduledSellOrderRepository.findByIdAndUserIdForUpdate(
            orderId,
            authenticatedUser.id()
        ).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약 매도입니다."));
        if (order.getStatus() != ScheduledSellOrderStatus.PENDING) {
            throw new IllegalArgumentException("대기 중인 예약 매도만 취소할 수 있습니다.");
        }

        Instant now = Instant.now(clock);
        order.setStatus(ScheduledSellOrderStatus.CANCELED);
        order.setCanceledAt(now);
        order.setUpdatedAt(now);
        return toResponse(gameScheduledSellOrderRepository.save(order));
    }

    @Transactional
    public void executeTriggeredOrders(String regionCode) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        List<GameScheduledSellOrder> pendingOrders = gameScheduledSellOrderRepository
            .findByRegionCodeAndStatusOrderByCreatedAtAsc(normalizedRegionCode, ScheduledSellOrderStatus.PENDING);

        for (GameScheduledSellOrder candidate : pendingOrders) {
            gameScheduledSellOrderRepository.findByIdForUpdate(candidate.getId())
                .filter(order -> order.getStatus() == ScheduledSellOrderStatus.PENDING)
                .ifPresent(this::executeIfTriggered);
        }
    }

    private void executeIfTriggered(GameScheduledSellOrder order) {
        GamePosition position = order.getPosition();
        if (position.getStatus() != PositionStatus.OPEN) {
            fail(order, "이미 종료된 포지션입니다.");
            return;
        }

        TrendSignal signal = trendSignalRepository.findById(
            new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId())
        ).orElse(null);
        if (signal == null || signal.getCurrentRank() == null || !isTriggered(order, signal)) {
            return;
        }

        try {
            Instant now = Instant.now(clock);
            order.setTriggeredAt(now);
            SellPositionResponse sellResponse = gameService.sellScheduledPosition(
                position.getUser().getId(),
                position.getId(),
                order.getQuantity()
            );
            order.setSellPricePoints(sellResponse.sellPricePoints());
            order.setSettledPoints(sellResponse.settledPoints());
            order.setPnlPoints(sellResponse.pnlPoints());
            order.setStatus(ScheduledSellOrderStatus.EXECUTED);
            order.setExecutedAt(now);
            order.setUpdatedAt(now);
            gameScheduledSellOrderRepository.save(order);
        } catch (IllegalArgumentException exception) {
            if ("최소 보유 시간이 지나야 매도할 수 있습니다.".equals(exception.getMessage())) {
                order.setTriggeredAt(null);
                return;
            }
            fail(order, exception.getMessage());
        }
    }

    private void fail(GameScheduledSellOrder order, String reason) {
        Instant now = Instant.now(clock);
        order.setStatus(ScheduledSellOrderStatus.FAILED);
        order.setFailedAt(now);
        order.setFailedReason(truncateReason(reason));
        order.setUpdatedAt(now);
        gameScheduledSellOrderRepository.save(order);
    }

    private ScheduledSellOrderResponse toResponse(GameScheduledSellOrder order) {
        TrendSignal signal = trendSignalRepository.findById(new TrendSignalId(
            order.getPosition().getRegionCode(),
            order.getPosition().getCategoryId(),
            order.getPosition().getVideoId()
        )).orElse(null);
        return new ScheduledSellOrderResponse(
            order.getId(),
            order.getPosition().getId(),
            order.getPosition().getVideoId(),
            order.getPosition().getTitle(),
            order.getPosition().getChannelTitle(),
            order.getPosition().getThumbnailUrl(),
            order.getRegionCode(),
            order.getPosition().getBuyRank(),
            signal != null ? signal.getCurrentRank() : null,
            resolveTriggerType(order).name(),
            order.getTargetRank(),
            order.getTargetProfitRatePercent(),
            resolveTriggerDirection(order).name(),
            order.getQuantity(),
            order.getSellPricePoints(),
            order.getSettledPoints(),
            order.getPnlPoints(),
            order.getStatus().name(),
            order.getFailedReason(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            order.getTriggeredAt(),
            order.getExecutedAt(),
            order.getCanceledAt(),
            order.getFailedAt()
        );
    }

    private GameSeason requireActiveSeason(String regionCode) {
        String normalizedRegionCode = normalizeRegionCode(regionCode);
        return gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(
            SeasonStatus.ACTIVE,
            normalizedRegionCode
        ).orElseThrow(() -> new IllegalArgumentException("진행 중인 시즌이 없습니다."));
    }

    private boolean isTriggered(GameScheduledSellOrder order, TrendSignal signal) {
        if (resolveTriggerType(order) == ScheduledSellTriggerType.PROFIT_RATE) {
            Double profitRatePercent = calculateProfitRatePercent(order.getPosition(), order.getQuantity(), signal);
            return profitRatePercent != null
                && order.getTargetProfitRatePercent() != null
                && profitRatePercent >= order.getTargetProfitRatePercent();
        }

        int currentRank = signal.getCurrentRank();
        ScheduledSellTriggerDirection triggerDirection = resolveTriggerDirection(order);

        if (triggerDirection == ScheduledSellTriggerDirection.RANK_DROPS_TO) {
            return currentRank >= order.getTargetRank();
        }

        return currentRank <= order.getTargetRank();
    }

    private void ensureConditionNotAlreadyMet(
        GamePosition position,
        long quantity,
        ScheduledSellTriggerType triggerType,
        Integer targetRank,
        Double targetProfitRatePercent,
        ScheduledSellTriggerDirection triggerDirection
    ) {
        TrendSignal signal = trendSignalRepository.findById(
            new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId())
        ).orElse(null);
        if (signal == null || signal.getCurrentRank() == null) {
            return;
        }

        boolean alreadyMet;
        if (triggerType == ScheduledSellTriggerType.PROFIT_RATE) {
            Double profitRatePercent = calculateProfitRatePercent(position, quantity, signal);
            alreadyMet = profitRatePercent != null && profitRatePercent >= targetProfitRatePercent;
        } else {
            int currentRank = signal.getCurrentRank();
            alreadyMet = triggerDirection == ScheduledSellTriggerDirection.RANK_DROPS_TO
                ? currentRank >= targetRank
                : currentRank <= targetRank;
        }

        if (alreadyMet) {
            if (triggerType == ScheduledSellTriggerType.PROFIT_RATE) {
                throw new IllegalArgumentException("현재 수익률이 이미 예약 매도 조건을 만족합니다. 바로 매도하거나 조건 수익률을 조정해 주세요.");
            }
            throw new IllegalArgumentException("현재 순위가 이미 예약 매도 조건을 만족합니다. 바로 매도하거나 조건 순위를 조정해 주세요.");
        }
    }

    private ScheduledSellTriggerType resolveRequestTriggerType(CreateScheduledSellOrderRequest request) {
        ScheduledSellTriggerType triggerType = request.triggerType() != null
            ? request.triggerType()
            : request.targetProfitRatePercent() != null
                ? ScheduledSellTriggerType.PROFIT_RATE
                : ScheduledSellTriggerType.RANK;
        if (triggerType == ScheduledSellTriggerType.RANK && request.targetProfitRatePercent() != null) {
            throw new IllegalArgumentException("순위 예약 매도에는 targetProfitRatePercent를 함께 설정할 수 없습니다.");
        }
        if (triggerType == ScheduledSellTriggerType.PROFIT_RATE && request.targetRank() != null) {
            throw new IllegalArgumentException("수익률 예약 매도에는 targetRank를 함께 설정할 수 없습니다.");
        }
        return triggerType;
    }

    private ScheduledSellTriggerType resolveTriggerType(GameScheduledSellOrder order) {
        return order.getTriggerType() != null
            ? order.getTriggerType()
            : ScheduledSellTriggerType.RANK;
    }

    private Double calculateProfitRatePercent(GamePosition position, long quantity, TrendSignal signal) {
        if (signal.getCurrentRank() == null || quantity <= 0L) {
            return null;
        }

        long positionQuantity = getPositionQuantity(position);
        long orderQuantity = Math.min(quantity, positionQuantity);
        long unitStakePoints = GamePointCalculator.estimateUnitPricePoints(position.getStakePoints(), positionQuantity);
        long stakePoints = GamePointCalculator.calculatePositionPoints(unitStakePoints, orderQuantity);
        long currentPricePoints = GamePointCalculator.calculatePositionPoints(
            GamePointCalculator.calculateMomentumAdjustedPricePoints(signal.getCurrentRank(), signal.getRankChange()),
            orderQuantity
        );
        return GameStrategyResolver.calculateProfitRatePercent(stakePoints, currentPricePoints);
    }

    private Double normalizeTargetProfitRatePercent(Double targetProfitRatePercent) {
        if (
            targetProfitRatePercent == null
                || !Double.isFinite(targetProfitRatePercent)
                || targetProfitRatePercent < 0D
        ) {
            throw new IllegalArgumentException("targetProfitRatePercent는 0 이상의 수익률이어야 합니다.");
        }

        return targetProfitRatePercent;
    }

    private ScheduledSellTriggerDirection resolveTriggerDirection(GameScheduledSellOrder order) {
        return order.getTriggerDirection() != null
            ? order.getTriggerDirection()
            : ScheduledSellTriggerDirection.RANK_IMPROVES_TO;
    }

    private void ensureOpenActivePosition(GamePosition position) {
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalArgumentException("열린 포지션만 예약 매도할 수 있습니다.");
        }
        if (position.getSeason().getStatus() != SeasonStatus.ACTIVE) {
            throw new IllegalArgumentException("진행 중인 시즌 포지션만 예약 매도할 수 있습니다.");
        }
    }

    private long normalizeQuantity(Long quantity) {
        if (quantity == null || quantity < GamePointCalculator.ORDER_QUANTITY_STEP) {
            throw new IllegalArgumentException("quantity는 1개 이상이어야 합니다.");
        }
        if (quantity % GamePointCalculator.ORDER_QUANTITY_STEP != 0) {
            throw new IllegalArgumentException("quantity는 1개 단위로만 주문할 수 있습니다.");
        }
        return quantity;
    }

    private int normalizeTargetRank(Integer targetRank) {
        if (targetRank == null || targetRank < 1 || targetRank > GamePointCalculator.MAX_TRACKED_RANK) {
            throw new IllegalArgumentException("targetRank는 1위부터 200위 사이여야 합니다.");
        }
        return targetRank;
    }

    private long getPositionQuantity(GamePosition position) {
        return position.getQuantity() == null || position.getQuantity() < GamePointCalculator.MIN_QUANTITY
            ? GamePointCalculator.QUANTITY_SCALE
            : position.getQuantity();
    }

    private String normalizeRegionCode(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            throw new IllegalArgumentException("regionCode는 필수입니다.");
        }
        return regionCode.trim().toUpperCase();
    }

    private String truncateReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "예약 매도를 실행할 수 없습니다.";
        }
        return reason.length() <= 500 ? reason : reason.substring(0, 500);
    }
}
