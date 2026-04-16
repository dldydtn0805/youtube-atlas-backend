package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.game.api.GameRealtimeEventResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;

@Service
public class GameSettlementService {

    private static final String TRENDING_CATEGORY_ID = "0";
    private static final int DEFAULT_FALLBACK_RANK = 201;
    private static final String WALLET_UPDATED_EVENT = "wallet-updated";

    private final AtlasProperties atlasProperties;
    private final GameSeasonRepository gameSeasonRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final GameCoinPayoutRepository gameCoinPayoutRepository;
    private final GameSeasonCoinResultRepository gameSeasonCoinResultRepository;
    private final GameCoinTierService gameCoinTierService;
    private final TrendSignalRepository trendSignalRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public GameSettlementService(
        AtlasProperties atlasProperties,
        GameSeasonRepository gameSeasonRepository,
        GamePositionRepository gamePositionRepository,
        GameWalletRepository gameWalletRepository,
        GameLedgerRepository gameLedgerRepository,
        GameCoinPayoutRepository gameCoinPayoutRepository,
        GameSeasonCoinResultRepository gameSeasonCoinResultRepository,
        GameCoinTierService gameCoinTierService,
        TrendSignalRepository trendSignalRepository,
        SimpMessagingTemplate messagingTemplate,
        Clock clock
    ) {
        this.atlasProperties = atlasProperties;
        this.gameSeasonRepository = gameSeasonRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameCoinPayoutRepository = gameCoinPayoutRepository;
        this.gameSeasonCoinResultRepository = gameSeasonCoinResultRepository;
        this.gameCoinTierService = gameCoinTierService;
        this.trendSignalRepository = trendSignalRepository;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @Scheduled(cron = "${atlas.game.cron}")
    @Transactional
    public void settleEndedSeasonsScheduled() {
        if (!atlasProperties.getGame().isSchedulerEnabled()) {
            return;
        }

        distributeActiveSeasonCoins();
        settleEndedSeasons();
        ensureManagedActiveSeasons();
    }

    @Transactional
    public void distributeActiveSeasonCoins() {
        Instant now = Instant.now(clock);
        for (GameSeason season : gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)) {
            if (season.getEndAt() != null && !season.getEndAt().isAfter(now)) {
                continue;
            }

            distributeSeasonCoins(season, now);
        }
    }

    @Transactional
    public void settleEndedSeasons() {
        Instant now = Instant.now(clock);
        List<GameSeason> endedSeasons = gameSeasonRepository.findByStatusAndEndAtLessThanEqual(SeasonStatus.ACTIVE, now);

        for (GameSeason season : endedSeasons) {
            settleSeason(season, now);
        }
    }

    @Transactional
    public void ensureManagedActiveSeasons() {
        Instant now = Instant.now(clock);

        for (String regionCode : resolveManagedRegionCodes()) {
            if (gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(SeasonStatus.ACTIVE, regionCode).isPresent()) {
                continue;
            }

            GameSeason latestSeason = gameSeasonRepository.findTopByRegionCodeOrderByStartAtDesc(regionCode).orElse(null);
            GameSeason createdSeason = gameSeasonRepository.save(createNextSeason(regionCode, latestSeason, now));
            gameCoinTierService.getOrCreateTiers(createdSeason, latestSeason);
        }
    }

    @Transactional
    public void closeSeason(Long seasonId) {
        if (seasonId == null) {
            throw new IllegalArgumentException("seasonId는 필수입니다.");
        }

        GameSeason season = gameSeasonRepository.findById(seasonId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시즌입니다."));

        if (season.getStatus() != SeasonStatus.ACTIVE) {
            throw new IllegalArgumentException("ACTIVE 상태인 시즌만 수동 종료할 수 있습니다.");
        }

        Instant now = Instant.now(clock);
        settleSeason(season, now);
        ensureManagedActiveSeasons();
    }

    private void distributeSeasonCoins(GameSeason season, Instant now) {
        List<TrendSignal> signals = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        );
        if (signals.isEmpty()) {
            return;
        }

        Long currentRunId = signals.get(0).getCurrentRunId();
        Instant capturedAt = signals.get(0).getCapturedAt();
        Instant payoutSlotAt = resolvePayoutSlotAt(now);
        Map<String, TrendSignal> signalByVideoId = signals.stream()
            .collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));
        Set<Long> paidPositionIds = new HashSet<>(
            gameCoinPayoutRepository.findPositionIdsBySeasonIdAndPayoutSlotAt(season.getId(), payoutSlotAt)
        );
        Map<Long, GameWallet> walletByUserId = new HashMap<>();

        for (GamePosition position : gamePositionRepository.findBySeasonIdAndStatus(season.getId(), PositionStatus.OPEN)) {
            if (paidPositionIds.contains(position.getId())) {
                continue;
            }

            TrendSignal signal = signalByVideoId.get(position.getVideoId());
            if (signal == null) {
                continue;
            }

            int rank = signal.getCurrentRank();
            int baseCoinRateBasisPoints = GameService.resolveCoinRateBasisPoints(rank);
            if (baseCoinRateBasisPoints <= 0) {
                continue;
            }

            long heldSeconds = now.getEpochSecond() - position.getCreatedAt().getEpochSecond();
            if (heldSeconds < position.getSeason().getMinHoldSeconds()) {
                continue;
            }

            int holdBoostBasisPoints = GameService.calculateHoldBoostBasisPoints(
                heldSeconds,
                position.getSeason().getMinHoldSeconds(),
                atlasProperties.getGame().getCoinHoldBoostIntervalSeconds(),
                atlasProperties.getGame().getCoinHoldBoostBasisPoints(),
                atlasProperties.getGame().getCoinHoldBoostMaxBasisPoints()
            );
            int effectiveCoinRateBasisPoints = GameService.calculateEffectiveCoinRateBasisPoints(
                baseCoinRateBasisPoints,
                holdBoostBasisPoints
            );

            long currentValuePoints = GamePointCalculator.calculatePositionPoints(
                GamePointCalculator.calculateMomentumAdjustedPricePoints(rank, signal.getRankChange()),
                position.getQuantity() == null || position.getQuantity() < GamePointCalculator.MIN_QUANTITY
                    ? GamePointCalculator.QUANTITY_SCALE
                    : position.getQuantity()
            );
            long producedCoins = GameService.calculateEstimatedCoinYield(currentValuePoints, effectiveCoinRateBasisPoints);
            if (producedCoins <= 0L) {
                continue;
            }

            GameWallet wallet = walletByUserId.computeIfAbsent(
                position.getUser().getId(),
                userId -> gameWalletRepository.findBySeasonIdAndUserIdForUpdate(season.getId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."))
            );

            try {
                saveCoinPayout(position, wallet, currentRunId, capturedAt, payoutSlotAt, rank, effectiveCoinRateBasisPoints, producedCoins, now);
                paidPositionIds.add(position.getId());
            } catch (DataIntegrityViolationException ignored) {
                // Another scheduler tick or node inserted the payout first.
            }
        }
    }

    private void saveCoinPayout(
        GamePosition position,
        GameWallet wallet,
        Long trendRunId,
        Instant capturedAt,
        Instant payoutSlotAt,
        int rank,
        int coinRateBasisPoints,
        long producedCoins,
        Instant now
    ) {
        GameCoinPayout payout = new GameCoinPayout();
        payout.setSeason(position.getSeason());
        payout.setUser(position.getUser());
        payout.setPosition(position);
        payout.setTrendRunId(trendRunId);
        payout.setPayoutSlotAt(payoutSlotAt);
        payout.setRankAtPayout(rank);
        payout.setCoinRateBasisPoints(coinRateBasisPoints);
        payout.setAmountCoins(producedCoins);
        payout.setCreatedAt(now);
        gameCoinPayoutRepository.saveAndFlush(payout);

        wallet.setCoinBalance(wallet.getCoinBalance() + producedCoins);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);
        publishWalletUpdated(wallet, capturedAt, now);
    }

    private void publishWalletUpdated(GameWallet wallet, Instant capturedAt, Instant occurredAt) {
        messagingTemplate.convertAndSend(
            "/topic/game/" + wallet.getSeason().getRegionCode(),
            new GameRealtimeEventResponse(
                WALLET_UPDATED_EVENT,
                wallet.getSeason().getRegionCode(),
                wallet.getSeason().getId(),
                capturedAt,
                occurredAt
            )
        );
    }

    private Instant resolvePayoutSlotAt(Instant now) {
        long slotMinutes = Math.max(1, atlasProperties.getGame().getPayoutSlotMinutes());
        long slotSeconds = slotMinutes * 60L;
        long epochSecond = now.getEpochSecond();
        return Instant.ofEpochSecond(epochSecond - (epochSecond % slotSeconds));
    }

    private void settleSeason(GameSeason season, Instant now) {
        List<TrendSignal> signals = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        );
        Map<String, TrendSignal> signalByVideoId = signals.stream()
            .collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));

        int fallbackRank = signals.stream()
            .map(TrendSignal::getCurrentRank)
            .max(Integer::compareTo)
            .map(rank -> rank + 1)
            .orElse(DEFAULT_FALLBACK_RANK);

        Long fallbackRunId = signals.isEmpty() ? null : signals.get(0).getCurrentRunId();
        Instant fallbackCapturedAt = signals.isEmpty() ? now : signals.get(0).getCapturedAt();

        for (GamePosition position : gamePositionRepository.findBySeasonIdAndStatus(season.getId(), PositionStatus.OPEN)) {
            settlePosition(position, signalByVideoId.get(position.getVideoId()), fallbackRank, fallbackRunId, fallbackCapturedAt, now);
        }

        finalizeSeasonCoinResults(season, now);
        season.setStatus(SeasonStatus.ENDED);
        gameSeasonRepository.save(season);
    }

    private void finalizeSeasonCoinResults(GameSeason season, Instant now) {
        List<GameSeasonCoinTier> tiers = gameCoinTierService.getOrCreateTiers(season);
        Set<Long> finalizedUserIds = new HashSet<>(gameSeasonCoinResultRepository.findUserIdsBySeasonId(season.getId()));

        for (GameWallet wallet : gameWalletRepository.findBySeasonId(season.getId())) {
            if (finalizedUserIds.contains(wallet.getUser().getId())) {
                continue;
            }

            GameSeasonCoinTier resolvedTier = gameCoinTierService.resolveTier(tiers, wallet.getCoinBalance());

            try {
                saveSeasonCoinResult(season, wallet, resolvedTier, now);
                finalizedUserIds.add(wallet.getUser().getId());
            } catch (DataIntegrityViolationException ignored) {
                // Another scheduler tick or node finalized the result first.
            }
        }
    }

    private void saveSeasonCoinResult(GameSeason season, GameWallet wallet, GameSeasonCoinTier resolvedTier, Instant now) {
        GameSeasonCoinResult result = new GameSeasonCoinResult();
        result.setSeason(season);
        result.setUser(wallet.getUser());
        result.setFinalCoinBalance(wallet.getCoinBalance());
        result.setFinalTierCode(resolvedTier.getTierCode());
        result.setFinalTierDisplayName(resolvedTier.getDisplayName());
        result.setFinalTierMinCoinBalance(resolvedTier.getMinCoinBalance());
        result.setBadgeCode(resolvedTier.getBadgeCode());
        result.setTitleCode(resolvedTier.getTitleCode());
        result.setProfileThemeCode(resolvedTier.getProfileThemeCode());
        result.setCreatedAt(now);
        gameSeasonCoinResultRepository.saveAndFlush(result);
    }

    private void settlePosition(
        GamePosition position,
        TrendSignal signal,
        int fallbackRank,
        Long fallbackRunId,
        Instant fallbackCapturedAt,
        Instant now
    ) {
        GameWallet wallet = gameWalletRepository.findBySeasonIdAndUserId(position.getSeason().getId(), position.getUser().getId())
            .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));

        int sellRank = signal != null ? signal.getCurrentRank() : fallbackRank;
        Integer rankChange = signal != null ? signal.getRankChange() : null;
        Long sellRunId = signal != null ? signal.getCurrentRunId() : fallbackRunId;
        Instant sellCapturedAt = signal != null ? signal.getCapturedAt() : fallbackCapturedAt;
        int rankDiff = position.getBuyRank() - sellRank;
        long sellPricePoints = GamePointCalculator.calculatePositionPoints(
            GamePointCalculator.calculateMomentumAdjustedPricePoints(sellRank, rankChange),
            position.getQuantity() == null || position.getQuantity() < GamePointCalculator.MIN_QUANTITY
                ? GamePointCalculator.QUANTITY_SCALE
                : position.getQuantity()
        );
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(position.getStakePoints(), settledPoints);

        position.setSellRunId(sellRunId);
        position.setSellRank(sellRank);
        position.setSellCapturedAt(sellCapturedAt);
        position.setRankDiff(rankDiff);
        position.setPnlPoints(pnlPoints);
        position.setSettledPoints(settledPoints);
        position.setStatus(PositionStatus.AUTO_CLOSED);
        position.setClosedAt(now);
        gamePositionRepository.save(position);

        wallet.setReservedPoints(wallet.getReservedPoints() - position.getStakePoints());
        wallet.setBalancePoints(wallet.getBalancePoints() + settledPoints);
        wallet.setRealizedPnlPoints(wallet.getRealizedPnlPoints() + pnlPoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        GameLedger ledger = new GameLedger();
        ledger.setSeason(position.getSeason());
        ledger.setUser(position.getUser());
        ledger.setPosition(position);
        ledger.setType(LedgerType.AUTO_SETTLE);
        ledger.setAmountPoints(settledPoints);
        ledger.setBalanceAfterPoints(wallet.getBalancePoints());
        ledger.setCreatedAt(now);
        gameLedgerRepository.save(ledger);
    }

    private List<String> resolveManagedRegionCodes() {
        Set<String> managedRegions = atlasProperties.getTrending().getJobs().stream()
            .map(AtlasProperties.SyncJob::getRegionCode)
            .filter(StringUtils::hasText)
            .map(regionCode -> regionCode.trim().toUpperCase())
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (managedRegions.isEmpty()) {
            managedRegions.add("KR");
        }

        return List.copyOf(managedRegions);
    }

    private GameSeason createNextSeason(String regionCode, GameSeason latestSeason, Instant now) {
        Instant startAt = resolveNextSeasonStartAt(latestSeason, now);
        Duration duration = resolveSeasonDuration(latestSeason);

        GameSeason season = new GameSeason();
        season.setName(regionCode + " Daily Season");
        season.setStatus(SeasonStatus.ACTIVE);
        season.setRegionCode(regionCode);
        season.setStartAt(startAt);
        season.setEndAt(startAt.plus(duration));
        season.setStartingBalancePoints(latestSeason != null
            ? latestSeason.getStartingBalancePoints()
            : atlasProperties.getGame().getStartingBalancePoints());
        season.setMinHoldSeconds(latestSeason != null
            ? latestSeason.getMinHoldSeconds()
            : atlasProperties.getGame().getMinHoldSeconds());
        season.setMaxOpenPositions(latestSeason != null
            ? latestSeason.getMaxOpenPositions()
            : atlasProperties.getGame().getMaxOpenPositions());
        season.setRankPointMultiplier(latestSeason != null
            ? latestSeason.getRankPointMultiplier()
            : atlasProperties.getGame().getRankPointMultiplier());
        season.setCreatedAt(now);
        return season;
    }

    private Instant resolveNextSeasonStartAt(GameSeason latestSeason, Instant now) {
        if (latestSeason == null || latestSeason.getEndAt() == null) {
            return now;
        }

        return latestSeason.getEndAt().isAfter(now) ? latestSeason.getEndAt() : now;
    }

    private Duration resolveSeasonDuration(GameSeason latestSeason) {
        if (
            latestSeason != null
            && latestSeason.getStartAt() != null
            && latestSeason.getEndAt() != null
            && latestSeason.getEndAt().isAfter(latestSeason.getStartAt())
        ) {
            return Duration.between(latestSeason.getStartAt(), latestSeason.getEndAt());
        }

        return Duration.ofDays(Math.max(1, atlasProperties.getGame().getSeasonDurationDays()));
    }
}
