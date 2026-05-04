package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    private final GameSeasonResultRepository gameSeasonResultRepository;
    private final GameHighlightStateRepository gameHighlightStateRepository;
    private final GameTierService gameTierService;
    private final TrendSignalRepository trendSignalRepository;
    private final Clock clock;

    public GameSettlementService(
        AtlasProperties atlasProperties,
        GameSeasonRepository gameSeasonRepository,
        GamePositionRepository gamePositionRepository,
        GameWalletRepository gameWalletRepository,
        GameLedgerRepository gameLedgerRepository,
        GameSeasonResultRepository gameSeasonResultRepository,
        GameHighlightStateRepository gameHighlightStateRepository,
        GameTierService gameTierService,
        TrendSignalRepository trendSignalRepository,
        Clock clock
    ) {
        this.atlasProperties = atlasProperties;
        this.gameSeasonRepository = gameSeasonRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameSeasonResultRepository = gameSeasonResultRepository;
        this.gameHighlightStateRepository = gameHighlightStateRepository;
        this.gameTierService = gameTierService;
        this.trendSignalRepository = trendSignalRepository;
        this.clock = clock;
    }

    @Scheduled(cron = "${atlas.game.cron}")
    @Transactional
    public void settleEndedSeasonsScheduled() {
        if (!atlasProperties.getGame().isSchedulerEnabled()) {
            return;
        }

        settleEndedSeasons();
        ensureManagedActiveSeasons();
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
            gameTierService.getOrCreateTiers(createdSeason, latestSeason);
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
        if (season.getEndAt() == null || season.getEndAt().isAfter(now)) {
            season.setEndAt(now);
        }
        settleSeason(season, now);
        ensureManagedActiveSeasons();
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

        snapshotSeasonResults(season, now);
        season.setStatus(SeasonStatus.ENDED);
        gameSeasonRepository.save(season);
    }

    private void snapshotSeasonResults(GameSeason season, Instant now) {
        Set<Long> existingUserIds = gameSeasonResultRepository.findBySeasonId(season.getId()).stream()
            .map(result -> result.getUser().getId())
            .collect(Collectors.toSet());
        Map<Long, List<GamePosition>> positionsByUserId = gamePositionRepository.findBySeasonId(season.getId()).stream()
            .collect(Collectors.groupingBy(position -> position.getUser().getId()));
        Map<Long, Long> highlightScoreByUserId = gameHighlightStateRepository
            .findBySeasonIdAndBestSettledHighlightScoreGreaterThan(season.getId(), 0L)
            .stream()
            .collect(Collectors.groupingBy(
                state -> state.getUser().getId(),
                Collectors.summingLong(GameHighlightState::getBestSettledHighlightScore)
            ));
        List<GameSeasonTier> tiers = gameTierService.getOrCreateTiers(season);
        List<SeasonResultCandidate> candidates = gameWalletRepository.findBySeasonId(season.getId()).stream()
            .map(wallet -> toSeasonResultCandidate(
                wallet,
                positionsByUserId.getOrDefault(wallet.getUser().getId(), List.of()),
                tiers,
                highlightScoreByUserId.getOrDefault(wallet.getUser().getId(), 0L)
            ))
            .sorted(
                java.util.Comparator.comparingLong(SeasonResultCandidate::finalAssetPoints).reversed()
                    .thenComparing(java.util.Comparator.comparingLong(SeasonResultCandidate::realizedPnlPoints).reversed())
                    .thenComparing(
                        candidate -> java.util.Objects.toString(candidate.wallet().getUser().getDisplayName(), ""),
                        String.CASE_INSENSITIVE_ORDER
                    )
            )
            .toList();

        for (int index = 0; index < candidates.size(); index += 1) {
            SeasonResultCandidate candidate = candidates.get(index);
            if (!existingUserIds.contains(candidate.wallet().getUser().getId())) {
                gameSeasonResultRepository.save(toSeasonResult(season, candidate, index + 1, now));
            }
        }
    }

    private SeasonResultCandidate toSeasonResultCandidate(
        GameWallet wallet,
        List<GamePosition> positions,
        List<GameSeasonTier> tiers,
        long calculatedHighlightScore
    ) {
        long balancePoints = valueOrZero(wallet.getBalancePoints());
        long reservedPoints = valueOrZero(wallet.getReservedPoints());
        long finalHighlightScore = calculatedHighlightScore + valueOrZero(wallet.getManualTierScoreAdjustment());
        GameSeasonTier finalTier = resolveFinalTier(tiers, finalHighlightScore);
        return new SeasonResultCandidate(
            wallet,
            positions,
            balancePoints + reservedPoints,
            valueOrZero(wallet.getRealizedPnlPoints()),
            finalHighlightScore,
            finalTier
        );
    }

    private GameSeasonResult toSeasonResult(
        GameSeason season,
        SeasonResultCandidate candidate,
        int finalRank,
        Instant now
    ) {
        GamePosition bestPosition = findBestPosition(candidate.positions());
        GameWallet wallet = candidate.wallet();

        GameSeasonResult result = new GameSeasonResult();
        result.setSeason(season);
        result.setUser(wallet.getUser());
        result.setRegionCode(season.getRegionCode());
        result.setSeasonName(season.getName());
        result.setSeasonStartAt(season.getStartAt());
        result.setSeasonEndAt(season.getEndAt());
        result.setFinalRank(finalRank);
        result.setFinalAssetPoints(candidate.finalAssetPoints());
        result.setFinalBalancePoints(valueOrZero(wallet.getBalancePoints()));
        result.setRealizedPnlPoints(candidate.realizedPnlPoints());
        result.setStartingBalancePoints(valueOrZero(season.getStartingBalancePoints()));
        result.setProfitRatePercent(calculateProfitRatePercent(
            candidate.finalAssetPoints() - valueOrZero(season.getStartingBalancePoints()),
            valueOrZero(season.getStartingBalancePoints())
        ));
        result.setFinalHighlightScore(candidate.finalHighlightScore());
        applyFinalTier(result, candidate.finalTier());
        result.setPositionCount((long) candidate.positions().size());
        applyBestPosition(result, bestPosition);
        result.setCreatedAt(now);
        return result;
    }

    private void applyFinalTier(GameSeasonResult result, GameSeasonTier finalTier) {
        if (finalTier == null) {
            return;
        }

        result.setFinalTierCode(finalTier.getTierCode());
        result.setFinalTierName(finalTier.getDisplayName());
        result.setFinalTierBadgeCode(finalTier.getBadgeCode());
        result.setFinalTierTitleCode(finalTier.getTitleCode());
    }

    private void applyBestPosition(GameSeasonResult result, GamePosition bestPosition) {
        if (bestPosition == null) {
            return;
        }

        result.setBestPositionId(bestPosition.getId());
        result.setBestPositionVideoId(bestPosition.getVideoId());
        result.setBestPositionTitle(bestPosition.getTitle());
        result.setBestPositionChannelTitle(bestPosition.getChannelTitle());
        result.setBestPositionThumbnailUrl(bestPosition.getThumbnailUrl());
        result.setBestPositionProfitPoints(valueOrZero(bestPosition.getPnlPoints()));
        result.setBestPositionProfitRatePercent(calculateProfitRatePercent(
            valueOrZero(bestPosition.getPnlPoints()),
            valueOrZero(bestPosition.getStakePoints())
        ));
        result.setBestPositionRankDiff(bestPosition.getRankDiff());
        result.setBestPositionBuyRank(bestPosition.getBuyRank());
        result.setBestPositionSellRank(bestPosition.getSellRank());
    }

    private GamePosition findBestPosition(List<GamePosition> positions) {
        return positions.stream()
            .max(
                java.util.Comparator.comparingLong((GamePosition position) -> valueOrZero(position.getPnlPoints()))
                    .thenComparing(GamePosition::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
            )
            .orElse(null);
    }

    private GameSeasonTier resolveFinalTier(List<GameSeasonTier> tiers, long finalHighlightScore) {
        if (tiers == null || tiers.isEmpty()) {
            return null;
        }

        return gameTierService.resolveTier(tiers, finalHighlightScore);
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

    private long valueOrZero(Long value) {
        return value != null ? value : 0L;
    }

    private Double calculateProfitRatePercent(long profitPoints, long stakePoints) {
        if (stakePoints <= 0L) {
            return null;
        }

        return Math.round((double) profitPoints * 1000D / stakePoints) / 10D;
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
        Duration duration = resolveSeasonDuration();

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

        return latestSeason.getStatus() != SeasonStatus.ENDED && latestSeason.getEndAt().isAfter(now)
            ? latestSeason.getEndAt()
            : now;
    }

    private Duration resolveSeasonDuration() {
        return Duration.ofDays(Math.max(1, atlasProperties.getGame().getSeasonDurationDays()));
    }

    private record SeasonResultCandidate(
        GameWallet wallet,
        List<GamePosition> positions,
        long finalAssetPoints,
        long realizedPnlPoints,
        long finalHighlightScore,
        GameSeasonTier finalTier
    ) {
    }
}
