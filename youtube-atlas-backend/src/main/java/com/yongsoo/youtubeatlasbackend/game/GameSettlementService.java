package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;

@Service
public class GameSettlementService {

    private static final String TRENDING_CATEGORY_ID = "0";
    private static final int DEFAULT_FALLBACK_RANK = 201;

    private final AtlasProperties atlasProperties;
    private final GameSeasonRepository gameSeasonRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final GameDividendPayoutRepository gameDividendPayoutRepository;
    private final TrendSignalRepository trendSignalRepository;
    private final Clock clock;

    public GameSettlementService(
        AtlasProperties atlasProperties,
        GameSeasonRepository gameSeasonRepository,
        GamePositionRepository gamePositionRepository,
        GameWalletRepository gameWalletRepository,
        GameLedgerRepository gameLedgerRepository,
        GameDividendPayoutRepository gameDividendPayoutRepository,
        TrendSignalRepository trendSignalRepository,
        Clock clock
    ) {
        this.atlasProperties = atlasProperties;
        this.gameSeasonRepository = gameSeasonRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameDividendPayoutRepository = gameDividendPayoutRepository;
        this.trendSignalRepository = trendSignalRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${atlas.game.cron}")
    @Transactional
    public void settleEndedSeasonsScheduled() {
        if (!atlasProperties.getGame().isSchedulerEnabled()) {
            return;
        }

        distributeActiveSeasonDividends();
        settleEndedSeasons();
    }

    @Transactional
    public void distributeActiveSeasonDividends() {
        Instant now = Instant.now(clock);
        for (GameSeason season : gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)) {
            if (season.getEndAt() != null && !season.getEndAt().isAfter(now)) {
                continue;
            }

            distributeSeasonDividends(season, now);
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

    private void distributeSeasonDividends(GameSeason season, Instant now) {
        List<TrendSignal> signals = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        );
        if (signals.isEmpty()) {
            return;
        }

        Long currentRunId = signals.get(0).getCurrentRunId();
        Instant capturedAt = signals.get(0).getCapturedAt();
        Map<String, TrendSignal> signalByVideoId = signals.stream()
            .collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));
        Set<Long> paidPositionIds = new HashSet<>(
            gameDividendPayoutRepository.findPositionIdsBySeasonIdAndTrendRunId(season.getId(), currentRunId)
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
            int dividendRateBasisPoints = GameService.resolveDividendRateBasisPoints(rank);
            if (dividendRateBasisPoints <= 0) {
                continue;
            }

            long heldSeconds = capturedAt.getEpochSecond() - position.getCreatedAt().getEpochSecond();
            if (heldSeconds < position.getSeason().getMinHoldSeconds()) {
                continue;
            }

            long currentValuePoints = GamePointCalculator.calculatePositionPoints(
                GamePointCalculator.calculatePricePoints(rank),
                position.getQuantity() == null || position.getQuantity() < GamePointCalculator.MIN_QUANTITY
                    ? GamePointCalculator.QUANTITY_SCALE
                    : position.getQuantity()
            );
            long dividendPoints = GameService.calculateEstimatedDividendPoints(currentValuePoints, dividendRateBasisPoints);
            if (dividendPoints <= 0L) {
                continue;
            }

            GameWallet wallet = walletByUserId.computeIfAbsent(
                position.getUser().getId(),
                userId -> gameWalletRepository.findBySeasonIdAndUserIdForUpdate(season.getId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."))
            );

            try {
                saveDividendPayout(position, wallet, currentRunId, rank, dividendRateBasisPoints, dividendPoints, now);
                paidPositionIds.add(position.getId());
            } catch (DataIntegrityViolationException ignored) {
                // Another scheduler tick or node inserted the payout first.
            }
        }
    }

    private void saveDividendPayout(
        GamePosition position,
        GameWallet wallet,
        Long trendRunId,
        int rank,
        int dividendRateBasisPoints,
        long dividendPoints,
        Instant now
    ) {
        GameDividendPayout payout = new GameDividendPayout();
        payout.setSeason(position.getSeason());
        payout.setUser(position.getUser());
        payout.setPosition(position);
        payout.setTrendRunId(trendRunId);
        payout.setRankAtPayout(rank);
        payout.setDividendRateBasisPoints(dividendRateBasisPoints);
        payout.setAmountPoints(dividendPoints);
        payout.setCreatedAt(now);
        gameDividendPayoutRepository.saveAndFlush(payout);

        wallet.setBalancePoints(wallet.getBalancePoints() + dividendPoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        GameLedger ledger = new GameLedger();
        ledger.setSeason(position.getSeason());
        ledger.setUser(position.getUser());
        ledger.setPosition(position);
        ledger.setType(LedgerType.BONUS);
        ledger.setAmountPoints(dividendPoints);
        ledger.setBalanceAfterPoints(wallet.getBalancePoints());
        ledger.setCreatedAt(now);
        gameLedgerRepository.save(ledger);
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

        season.setStatus(SeasonStatus.ENDED);
        gameSeasonRepository.save(season);
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
        Long sellRunId = signal != null ? signal.getCurrentRunId() : fallbackRunId;
        Instant sellCapturedAt = signal != null ? signal.getCapturedAt() : fallbackCapturedAt;
        int rankDiff = position.getBuyRank() - sellRank;
        long sellPricePoints = GamePointCalculator.calculatePositionPoints(
            GamePointCalculator.calculatePricePoints(sellRank),
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
}
