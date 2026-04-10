package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.game.api.CoinOverviewResponse;
import com.yongsoo.youtubeatlasbackend.game.api.CoinPositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.CoinRankResponse;
import com.yongsoo.youtubeatlasbackend.game.api.CoinTierProgressResponse;
import com.yongsoo.youtubeatlasbackend.game.api.CoinTierResponse;
import com.yongsoo.youtubeatlasbackend.game.api.CreatePositionRequest;
import com.yongsoo.youtubeatlasbackend.game.api.CurrentSeasonResponse;
import com.yongsoo.youtubeatlasbackend.game.api.LeaderboardEntryResponse;
import com.yongsoo.youtubeatlasbackend.game.api.MarketVideoResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionRankHistoryPointResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionRankHistoryResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionsRequest;
import com.yongsoo.youtubeatlasbackend.game.api.SeasonCoinResultResponse;
import com.yongsoo.youtubeatlasbackend.game.api.WalletResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;

@Service
public class GameService {

    private static final String TRENDING_CATEGORY_ID = "0";
    private static final int DEFAULT_FALLBACK_RANK = 201;
    private static final int COIN_ELIGIBLE_RANK_CUTOFF = 200;
    private static final int TOP_RANK_COIN_RATE_BASIS_POINTS = 300;
    private static final int LAST_ELIGIBLE_RANK_COIN_RATE_BASIS_POINTS = 1;
    private static final double COIN_RATE_CURVE_POWER = 1.8D;

    private final GameSeasonRepository gameSeasonRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final GameSeasonCoinResultRepository gameSeasonCoinResultRepository;
    private final GameCoinTierService gameCoinTierService;
    private final AppUserRepository appUserRepository;
    private final TrendSignalRepository trendSignalRepository;
    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;
    private final Clock clock;
    private final CronExpression gameSettlementCron;
    private final int trendCaptureSlotMinutes;
    private final AtlasProperties.Game gameProperties;

    public GameService(
        GameSeasonRepository gameSeasonRepository,
        GameWalletRepository gameWalletRepository,
        GamePositionRepository gamePositionRepository,
        GameLedgerRepository gameLedgerRepository,
        GameSeasonCoinResultRepository gameSeasonCoinResultRepository,
        GameCoinTierService gameCoinTierService,
        AppUserRepository appUserRepository,
        TrendSignalRepository trendSignalRepository,
        TrendRunRepository trendRunRepository,
        TrendSnapshotRepository trendSnapshotRepository,
        Clock clock,
        AtlasProperties atlasProperties
    ) {
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameSeasonCoinResultRepository = gameSeasonCoinResultRepository;
        this.gameCoinTierService = gameCoinTierService;
        this.appUserRepository = appUserRepository;
        this.trendSignalRepository = trendSignalRepository;
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
        this.clock = clock;
        this.gameProperties = atlasProperties.getGame();
        this.gameSettlementCron = CronExpression.parse(atlasProperties.getGame().getCron());
        this.trendCaptureSlotMinutes = atlasProperties.getTrending().getCaptureSlotMinutes();
    }

    @Transactional
    public CurrentSeasonResponse getCurrentSeason(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        return toCurrentSeasonResponse(season, wallet);
    }

    @Transactional
    public WalletResponse getWallet(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        return toWalletResponse(wallet);
    }

    @Transactional
    public List<MarketVideoResponse> getMarket(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        long openDistinctVideoCount = gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(
            season.getId(),
            authenticatedUser.id(),
            PositionStatus.OPEN
        );
        List<GamePosition> openPositions = gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
            season.getId(),
            authenticatedUser.id(),
            PositionStatus.OPEN
        );
        java.util.Set<String> ownedVideoIds = openPositions.stream()
            .map(GamePosition::getVideoId)
            .collect(Collectors.toSet());
        boolean maxOpenReached = openDistinctVideoCount >= season.getMaxOpenPositions();

        return trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().map(signal -> {
            long currentPricePoints = GamePointCalculator.calculatePricePoints(signal.getCurrentRank());
            boolean alreadyOwned = ownedVideoIds.contains(signal.getId().getVideoId());
            String blockedReason = resolveBuyBlockedReason(wallet, currentPricePoints, maxOpenReached, alreadyOwned);

            return new MarketVideoResponse(
                signal.getId().getVideoId(),
                signal.getTitle(),
                signal.getChannelTitle(),
                signal.getThumbnailUrl(),
                signal.getCurrentRank(),
                signal.getPreviousRank(),
                signal.getRankChange(),
                currentPricePoints,
                signal.getCurrentViewCount(),
                signal.getViewCountDelta(),
                signal.isNew(),
                blockedReason == null,
                blockedReason,
                signal.getCapturedAt()
            );
        }).toList();
    }

    @Transactional
    public List<LeaderboardEntryResponse> getLeaderboard(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        getOrCreateWallet(season, authenticatedUser);
        List<GameSeasonCoinTier> tiers = gameCoinTierService.getOrCreateTiers(season);

        Map<String, TrendSignal> signalByVideoId = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));

        Map<Long, List<GamePosition>> openPositionsByUserId = gamePositionRepository.findBySeasonIdAndStatus(
            season.getId(),
            PositionStatus.OPEN
        ).stream().collect(Collectors.groupingBy(position -> position.getUser().getId()));

        List<LeaderboardSnapshot> snapshots = gameWalletRepository.findBySeasonId(season.getId()).stream()
            .map(wallet -> toLeaderboardSnapshot(
                wallet,
                openPositionsByUserId.getOrDefault(wallet.getUser().getId(), List.of()),
                signalByVideoId,
                tiers
            ))
            .sorted(
                Comparator.comparingLong(LeaderboardSnapshot::coinBalance).reversed()
                    .thenComparing(Comparator.comparingLong(LeaderboardSnapshot::totalAssetPoints).reversed())
                    .thenComparing(Comparator.comparingLong(LeaderboardSnapshot::realizedPnlPoints).reversed())
                    .thenComparing(LeaderboardSnapshot::displayName, String.CASE_INSENSITIVE_ORDER)
            )
            .toList();

        return toLeaderboardResponses(snapshots, authenticatedUser.id());
    }

    @Transactional
    public CoinOverviewResponse getCoinOverview(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);

        Map<String, TrendSignal> signalByVideoId = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));
        Instant now = Instant.now(clock);

        List<CoinPositionCandidate> candidates = gamePositionRepository.findBySeasonIdAndStatus(season.getId(), PositionStatus.OPEN)
            .stream()
            .map(position -> toCoinPositionCandidate(position, signalByVideoId.get(position.getVideoId()), now))
            .toList();

        List<CoinPositionCandidate> myCandidates = candidates.stream()
            .filter(candidate -> candidate.position().getUser().getId().equals(authenticatedUser.id()))
            .filter(candidate -> candidate.rankEligible())
            .sorted(
                Comparator.comparing(CoinPositionCandidate::productionActive).reversed()
                    .thenComparing(CoinPositionCandidate::currentRank, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing((CoinPositionCandidate candidate) -> candidate.position().getCreatedAt())
            )
            .toList();

        long myEstimatedCoinYield = myCandidates.stream()
            .mapToLong(CoinPositionCandidate::estimatedCoinYield)
            .sum();

        return new CoinOverviewResponse(
            COIN_ELIGIBLE_RANK_CUTOFF,
            season.getMinHoldSeconds(),
            wallet.getCoinBalance(),
            myEstimatedCoinYield,
            (int) myCandidates.stream().filter(CoinPositionCandidate::productionActive).count(),
            (int) myCandidates.stream().filter(candidate -> !candidate.productionActive()).count(),
            buildCoinRankResponses(),
            myCandidates.stream()
                .map(this::toCoinPositionResponse)
                .toList()
        );
    }

    @Transactional
    public CoinTierProgressResponse getCurrentCoinTier(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        List<GameSeasonCoinTier> tiers = gameCoinTierService.getOrCreateTiers(season);
        GameSeasonCoinTier currentTier = gameCoinTierService.resolveTier(tiers, wallet.getCoinBalance());
        GameSeasonCoinTier nextTier = tiers.stream()
            .filter(tier -> tier.getMinCoinBalance() > wallet.getCoinBalance())
            .min(
                Comparator.comparingLong(GameSeasonCoinTier::getMinCoinBalance)
                    .thenComparingInt(GameSeasonCoinTier::getSortOrder)
            )
            .orElse(null);

        return new CoinTierProgressResponse(
            season.getId(),
            season.getName(),
            season.getRegionCode(),
            wallet.getCoinBalance(),
            toCoinTierResponse(currentTier),
            nextTier != null ? toCoinTierResponse(nextTier) : null,
            tiers.stream().map(this::toCoinTierResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SeasonCoinResultResponse getSeasonCoinResult(AuthenticatedUser authenticatedUser, Long seasonId) {
        if (seasonId == null) {
            throw new IllegalArgumentException("seasonId는 필수입니다.");
        }

        GameSeason season = gameSeasonRepository.findById(seasonId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시즌입니다."));
        GameSeasonCoinResult result = gameSeasonCoinResultRepository.findBySeasonIdAndUserId(seasonId, authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("시즌 코인 결과가 아직 확정되지 않았습니다."));

        return new SeasonCoinResultResponse(
            season.getId(),
            season.getName(),
            season.getRegionCode(),
            result.getFinalCoinBalance(),
            toCoinTierResponse(result),
            result.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getLeaderboardPositions(
        AuthenticatedUser authenticatedUser,
        Long userId,
        String regionCode
    ) {
        GameSeason season = requireActiveSeason(regionCode);
        getOrCreateWallet(season, authenticatedUser);

        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }

        return gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
            season.getId(),
            userId,
            PositionStatus.OPEN
        ).stream()
            .map(this::toPositionResponse)
            .toList();
    }

    @Transactional
    public List<PositionResponse> buy(AuthenticatedUser authenticatedUser, CreatePositionRequest request) {
        String regionCode = normalizeRequired(request.regionCode(), "regionCode는 필수입니다.").toUpperCase();
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWalletForUpdate(season, authenticatedUser);
        String categoryId = normalizeRequired(request.categoryId(), "categoryId는 필수입니다.");
        String videoId = normalizeRequired(request.videoId(), "videoId는 필수입니다.");
        long quotedPricePoints = normalizeStakePoints(request.stakePoints());
        int quantity = normalizeQuantity(request.quantity());
        Instant now = Instant.now(clock);

        List<GamePosition> openPositionsForVideo = gamePositionRepository.findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(
            season.getId(),
            authenticatedUser.id(),
            videoId,
            PositionStatus.OPEN
        );
        long openDistinctVideoCount = gamePositionRepository.countDistinctVideoIdBySeasonIdAndUserIdAndStatus(
            season.getId(),
            authenticatedUser.id(),
            PositionStatus.OPEN
        );
        boolean alreadyOwned = !openPositionsForVideo.isEmpty();
        if (!alreadyOwned && openDistinctVideoCount >= season.getMaxOpenPositions()) {
            throw new IllegalArgumentException("동시 보유 가능 포지션 수를 초과했습니다.");
        }

        TrendSignal signal = requireTrendSignal(regionCode, categoryId, videoId);
        long currentPricePoints = GamePointCalculator.calculatePricePoints(signal.getCurrentRank());
        if (quotedPricePoints != currentPricePoints) {
            throw new IllegalArgumentException("현재 가격이 변경되었습니다. 최신 시세로 다시 시도해 주세요.");
        }

        long totalStakePoints = GamePointCalculator.calculatePositionPoints(currentPricePoints, quantity);

        if (wallet.getBalancePoints() < totalStakePoints) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
        }

        AppUser user = requireUser(authenticatedUser.id());
        long previousBalancePoints = wallet.getBalancePoints();
        GamePosition savedPosition = createOpenPosition(
            season,
            user,
            regionCode,
            categoryId,
            videoId,
            signal,
            quantity,
            totalStakePoints,
            now
        );

        wallet.setBalancePoints(previousBalancePoints - totalStakePoints);
        wallet.setReservedPoints(wallet.getReservedPoints() + totalStakePoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        saveLedger(season, user, savedPosition, LedgerType.BUY_LOCK, -totalStakePoints, previousBalancePoints - totalStakePoints, now);

        return List.of(toOpenPositionResponse(savedPosition, signal));
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getMyPositions(
        AuthenticatedUser authenticatedUser,
        String regionCode,
        String status,
        Integer limit
    ) {
        GameSeason season = requireActiveSeason(regionCode);
        PositionStatus parsedStatus = StringUtils.hasText(status) ? parsePositionStatus(status) : null;
        int normalizedLimit = normalizePositionFetchLimit(limit);
        List<GamePosition> positions;

        if (limit != null) {
            PageRequest pageRequest = PageRequest.of(0, normalizedLimit);
            positions = parsedStatus != null
                ? gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
                    season.getId(),
                    authenticatedUser.id(),
                    parsedStatus,
                    pageRequest
                )
                : gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(
                    season.getId(),
                    authenticatedUser.id(),
                    pageRequest
                );
        } else {
            positions = parsedStatus != null
                ? gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
                    season.getId(),
                    authenticatedUser.id(),
                    parsedStatus
                )
                : gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(season.getId(), authenticatedUser.id());
        }

        return positions.stream()
            .map(this::toPositionResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PositionRankHistoryResponse getPositionRankHistory(AuthenticatedUser authenticatedUser, Long positionId) {
        GamePosition position = gamePositionRepository.findByIdAndUserId(positionId, authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        PositionHistoryWindow historyWindow = resolvePositionHistoryWindow(position);
        List<TrendRun> runs = trendRunRepository.findByRegionCodeAndCategoryIdAndIdBetweenOrderByIdAsc(
            position.getRegionCode(),
            position.getCategoryId(),
            historyWindow.startRunId(),
            historyWindow.endRunId()
        );
        Map<Long, TrendSnapshot> snapshotByRunId = trendSnapshotRepository
            .findByRegionCodeAndCategoryIdAndVideoIdAndRun_IdBetweenOrderByRun_IdAsc(
                position.getRegionCode(),
                position.getCategoryId(),
                position.getVideoId(),
                historyWindow.startRunId(),
                historyWindow.endRunId()
            )
            .stream()
            .collect(Collectors.toMap(snapshot -> snapshot.getRun().getId(), Function.identity()));

        List<PositionRankHistoryPointResponse> points = runs.stream()
            .map(run -> {
                TrendSnapshot snapshot = snapshotByRunId.get(run.getId());

                return new PositionRankHistoryPointResponse(
                    run.getId(),
                    run.getCapturedAt(),
                    snapshot != null ? snapshot.getRank() : null,
                    snapshot != null ? snapshot.getViewCount() : null,
                    snapshot == null,
                    run.getId().equals(position.getBuyRunId()),
                    position.getSellRunId() != null && run.getId().equals(position.getSellRunId())
                );
            })
            .toList();
        points = collapsePositionHistoryPoints(points);

        if (points.isEmpty()) {
            points = List.of(
                new PositionRankHistoryPointResponse(
                    position.getBuyRunId(),
                    position.getBuyCapturedAt(),
                    position.getBuyRank(),
                    null,
                    false,
                    true,
                    position.getSellRunId() != null && position.getSellRunId().equals(position.getBuyRunId())
                )
            );
        }

        PositionRankHistoryPointResponse latestPoint = points.get(points.size() - 1);

        return new PositionRankHistoryResponse(
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            position.getStatus().name(),
            position.getBuyRank(),
            historyWindow.latestRank(),
            position.getSellRank(),
            latestPoint.chartOut(),
            position.getBuyCapturedAt(),
            historyWindow.latestCapturedAt(),
            position.getClosedAt(),
            points
        );
    }

    @Transactional
    public SellPositionResponse sell(AuthenticatedUser authenticatedUser, Long positionId) {
        GamePosition position = gamePositionRepository.findByIdAndUserIdForUpdate(positionId, authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        Instant now = Instant.now(clock);
        ensurePositionOpen(position);
        ensurePositionSellable(position, now);
        SellSnapshot sellSnapshot = resolveSellSnapshot(position);
        GameWallet wallet = requireWalletForUpdate(position.getSeason().getId(), authenticatedUser.id());
        return closePosition(position, getPositionQuantity(position), sellSnapshot, wallet, now);
    }

    @Transactional
    public List<SellPositionResponse> sell(AuthenticatedUser authenticatedUser, SellPositionsRequest request) {
        GameSeason season = requireActiveSeason(request.regionCode());
        int quantity = normalizeQuantity(request.quantity());
        Instant now = Instant.now(clock);
        List<GamePosition> sellablePositions;

        if (request.positionId() != null) {
            GamePosition position = gamePositionRepository.findByIdAndUserIdForUpdate(request.positionId(), authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));
            ensurePositionOpen(position);
            if (!position.getSeason().getId().equals(season.getId())) {
                throw new IllegalArgumentException("현재 시즌 포지션만 매도할 수 있습니다.");
            }
            sellablePositions = canSellPosition(position, now) ? List.of(position) : List.of();
        } else {
            String videoId = normalizeRequired(request.videoId(), "videoId는 필수입니다.");
            sellablePositions = gamePositionRepository
                .findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAscForUpdate(
                    season.getId(),
                    authenticatedUser.id(),
                    videoId,
                    PositionStatus.OPEN
                )
                .stream()
                .filter(position -> canSellPosition(position, now))
                .toList();
        }

        int totalSellableQuantity = sellablePositions.stream()
            .mapToInt(this::getPositionQuantity)
            .sum();
        if (totalSellableQuantity < quantity) {
            throw new IllegalArgumentException("지금 매도 가능한 포지션 수가 부족합니다.");
        }

        SellSnapshot sellSnapshot = resolveSellSnapshot(sellablePositions.get(0));
        GameWallet wallet = requireWalletForUpdate(season.getId(), authenticatedUser.id());

        List<SellPositionResponse> responses = new ArrayList<>();
        int remainingQuantity = quantity;

        for (GamePosition position : sellablePositions) {
            if (remainingQuantity <= 0) {
                break;
            }

            int sellQuantity = Math.min(remainingQuantity, getPositionQuantity(position));
            responses.add(closePosition(position, sellQuantity, sellSnapshot, wallet, now));
            remainingQuantity -= sellQuantity;
        }

        return responses;
    }

    private CurrentSeasonResponse toCurrentSeasonResponse(GameSeason season, GameWallet wallet) {
        return new CurrentSeasonResponse(
            season.getId(),
            season.getName(),
            season.getStatus().name(),
            season.getRegionCode(),
            season.getStartAt(),
            season.getEndAt(),
            season.getStartingBalancePoints(),
            season.getMinHoldSeconds(),
            season.getMaxOpenPositions(),
            season.getRankPointMultiplier(),
            toWalletResponse(wallet)
        );
    }

    private WalletResponse toWalletResponse(GameWallet wallet) {
        return new WalletResponse(
            wallet.getSeason().getId(),
            wallet.getBalancePoints(),
            wallet.getReservedPoints(),
            wallet.getRealizedPnlPoints(),
            wallet.getCoinBalance(),
            wallet.getBalancePoints() + wallet.getReservedPoints()
        );
    }

    private List<CoinRankResponse> buildCoinRankResponses() {
        return java.util.stream.IntStream.rangeClosed(1, COIN_ELIGIBLE_RANK_CUTOFF)
            .mapToObj(rank -> new CoinRankResponse(rank, resolveCoinRatePercent(rank)))
            .toList();
    }

    private CoinPositionCandidate toCoinPositionCandidate(GamePosition position, TrendSignal signal, Instant now) {
        OpenPositionSnapshot snapshot = signal != null
            ? new OpenPositionSnapshot(signal.getCurrentRank(), signal.getCapturedAt(), false)
            : resolveOpenPositionSnapshot(position);
        Integer currentRank = snapshot != null ? snapshot.currentRank() : null;
        boolean rankEligible = currentRank != null
            && !snapshot.chartOut()
            && currentRank >= 1
            && currentRank <= COIN_ELIGIBLE_RANK_CUTOFF;
        boolean productionActive = rankEligible && canSellPosition(position, now);
        Long currentValuePoints = snapshot != null
            ? GamePointCalculator.calculatePositionPoints(
                resolveOpenPositionUnitPricePoints(snapshot),
                getPositionQuantity(position)
            )
            : null;
        long heldSeconds = Math.max(0L, now.getEpochSecond() - position.getCreatedAt().getEpochSecond());
        int coinRateBasisPoints = rankEligible ? resolveCoinRateBasisPoints(currentRank) : 0;
        int holdBoostBasisPoints = rankEligible
            ? calculateHoldBoostBasisPoints(
                heldSeconds,
                position.getSeason().getMinHoldSeconds(),
                gameProperties.getCoinHoldBoostIntervalSeconds(),
                gameProperties.getCoinHoldBoostBasisPoints(),
                gameProperties.getCoinHoldBoostMaxBasisPoints()
            )
            : 0;
        int effectiveCoinRateBasisPoints = rankEligible
            ? calculateEffectiveCoinRateBasisPoints(coinRateBasisPoints, holdBoostBasisPoints)
            : 0;
        long estimatedCoinYield = rankEligible && productionActive && currentValuePoints != null
            ? calculateEstimatedCoinYield(currentValuePoints, effectiveCoinRateBasisPoints)
            : 0L;
        Long nextProductionInSeconds = rankEligible && !productionActive
            ? Math.max(0L, position.getSeason().getMinHoldSeconds() - heldSeconds)
            : null;
        Long nextPayoutInSeconds = rankEligible && productionActive
            ? resolveNextPayoutInSeconds(now)
            : null;

        return new CoinPositionCandidate(
            position,
            currentRank,
            currentValuePoints,
            rankEligible,
            productionActive,
            coinRateBasisPoints,
            holdBoostBasisPoints,
            effectiveCoinRateBasisPoints,
            estimatedCoinYield,
            nextProductionInSeconds,
            nextPayoutInSeconds
        );
    }

    private CoinPositionResponse toCoinPositionResponse(CoinPositionCandidate candidate) {
        return new CoinPositionResponse(
            candidate.position().getId(),
            candidate.position().getVideoId(),
            candidate.position().getTitle(),
            candidate.position().getThumbnailUrl(),
            candidate.currentRank(),
            getPositionQuantity(candidate.position()),
            candidate.currentValuePoints(),
            candidate.rankEligible(),
            candidate.productionActive(),
            basisPointsToPercent(candidate.coinRateBasisPoints()),
            basisPointsToPercent(candidate.holdBoostBasisPoints()),
            basisPointsToPercent(candidate.effectiveCoinRateBasisPoints()),
            candidate.estimatedCoinYield(),
            candidate.nextProductionInSeconds(),
            candidate.nextPayoutInSeconds()
        );
    }

    private Long resolveNextPayoutInSeconds(Instant now) {
        ZonedDateTime nextRun = gameSettlementCron.next(now.atZone(resolveClockZone()));
        if (nextRun == null) {
            return null;
        }

        return Math.max(0L, nextRun.toEpochSecond() - now.getEpochSecond());
    }

    private ZoneId resolveClockZone() {
        return clock.getZone() != null ? clock.getZone() : ZoneId.of("UTC");
    }

    private CoinTierResponse toCoinTierResponse(GameSeasonCoinTier tier) {
        return new CoinTierResponse(
            tier.getTierCode(),
            tier.getDisplayName(),
            tier.getMinCoinBalance(),
            tier.getBadgeCode(),
            tier.getTitleCode(),
            tier.getProfileThemeCode()
        );
    }

    private CoinTierResponse toCoinTierResponse(GameSeasonCoinResult result) {
        return new CoinTierResponse(
            result.getFinalTierCode(),
            result.getFinalTierDisplayName(),
            result.getFinalTierMinCoinBalance(),
            result.getBadgeCode(),
            result.getTitleCode(),
            result.getProfileThemeCode()
        );
    }

    private PositionResponse toPositionResponse(GamePosition position) {
        if (position.getStatus() == PositionStatus.OPEN) {
            OpenPositionSnapshot snapshot = resolveOpenPositionSnapshot(position);
            if (snapshot != null) {
                return toOpenPositionResponse(position, snapshot);
            }
        }

        return new PositionResponse(
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            position.getBuyRank(),
            position.getSellRank(),
            position.getRankDiff(),
            getPositionQuantity(position),
            position.getStakePoints(),
            position.getSettledPoints(),
            position.getPnlPoints(),
            false,
            position.getStatus().name(),
            position.getBuyCapturedAt(),
            position.getCreatedAt(),
            position.getClosedAt()
        );
    }

    private PositionResponse toOpenPositionResponse(GamePosition position, TrendSignal signal) {
        return toOpenPositionResponse(
            position,
            new OpenPositionSnapshot(signal.getCurrentRank(), signal.getCapturedAt(), false)
        );
    }

    private PositionResponse toOpenPositionResponse(GamePosition position, OpenPositionSnapshot snapshot) {
        int rankDiff = position.getBuyRank() - snapshot.currentRank();
        long currentPricePoints = GamePointCalculator.calculatePositionPoints(
            resolveOpenPositionUnitPricePoints(snapshot),
            getPositionQuantity(position)
        );
        long profitPoints = GamePointCalculator.calculateProfitPoints(position.getStakePoints(), currentPricePoints);

        return new PositionResponse(
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            position.getBuyRank(),
            snapshot.currentRank(),
            rankDiff,
            getPositionQuantity(position),
            position.getStakePoints(),
            currentPricePoints,
            profitPoints,
            snapshot.chartOut(),
            position.getStatus().name(),
            position.getBuyCapturedAt(),
            position.getCreatedAt(),
            position.getClosedAt()
        );
    }

    private PositionHistoryWindow resolvePositionHistoryWindow(GamePosition position) {
        long startRunId = resolvePositionHistoryStartRunId(position);

        if (position.getStatus() != PositionStatus.OPEN) {
            return new PositionHistoryWindow(
                startRunId,
                position.getSellRunId() != null ? position.getSellRunId() : position.getBuyRunId(),
                position.getSellRank(),
                position.getSellCapturedAt() != null ? position.getSellCapturedAt() : position.getBuyCapturedAt()
            );
        }

        TrendSignalId signalId = new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId());
        TrendSignal signal = trendSignalRepository.findById(signalId).orElse(null);
        if (signal != null) {
            return new PositionHistoryWindow(
                startRunId,
                signal.getCurrentRunId(),
                signal.getCurrentRank(),
                signal.getCapturedAt()
            );
        }

        LatestTrendRun latestTrendRun = getLatestTrendRun(position.getRegionCode(), position.getCategoryId());
        if (latestTrendRun == null) {
            return new PositionHistoryWindow(
                startRunId,
                position.getBuyRunId(),
                position.getBuyRank(),
                position.getBuyCapturedAt()
            );
        }

        OpenPositionSnapshot openPositionSnapshot = resolveOpenPositionSnapshot(position);
        return new PositionHistoryWindow(
            startRunId,
            latestTrendRun.run().getId(),
            openPositionSnapshot != null ? openPositionSnapshot.currentRank() : position.getBuyRank(),
            openPositionSnapshot != null ? openPositionSnapshot.capturedAt() : latestTrendRun.run().getCapturedAt()
        );
    }

    private long resolvePositionHistoryStartRunId(GamePosition position) {
        return trendSnapshotRepository.findFirstByRegionCodeAndCategoryIdAndVideoIdOrderByRun_IdAsc(
            position.getRegionCode(),
            position.getCategoryId(),
            position.getVideoId()
        ).map(snapshot -> snapshot.getRun().getId()).orElse(position.getBuyRunId());
    }

    private GameSeason requireActiveSeason(String regionCode) {
        String normalizedRegionCode = normalizeRequired(regionCode, "regionCode는 필수입니다.").toUpperCase();

        return gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(
            SeasonStatus.ACTIVE,
            normalizedRegionCode
        ).orElseThrow(() -> new IllegalArgumentException(normalizedRegionCode + " 활성 게임 시즌이 없습니다."));
    }

    private String resolveBuyBlockedReason(
        GameWallet wallet,
        long unitPricePoints,
        boolean maxOpenReached,
        boolean alreadyOwned
    ) {
        if (maxOpenReached && !alreadyOwned) {
            return "동시 보유 가능 포지션 수를 초과했습니다.";
        }
        long minimumBuyPoints = GamePointCalculator.calculatePositionPoints(
            unitPricePoints,
            GamePointCalculator.MIN_QUANTITY
        );
        if (wallet.getBalancePoints() < minimumBuyPoints) {
            return "현재 가격 기준 보유 포인트가 부족합니다.";
        }
        return null;
    }

    private GameWallet getOrCreateWallet(GameSeason season, AuthenticatedUser authenticatedUser) {
        return gameWalletRepository.findBySeasonIdAndUserId(season.getId(), authenticatedUser.id())
            .orElseGet(() -> createWallet(season, authenticatedUser.id()));
    }

    private GameWallet getOrCreateWalletForUpdate(GameSeason season, AuthenticatedUser authenticatedUser) {
        return gameWalletRepository.findBySeasonIdAndUserIdForUpdate(season.getId(), authenticatedUser.id())
            .orElseGet(() -> createWalletAndLock(season, authenticatedUser.id()));
    }

    private LeaderboardSnapshot toLeaderboardSnapshot(
        GameWallet wallet,
        List<GamePosition> openPositions,
        Map<String, TrendSignal> signalByVideoId,
        List<GameSeasonCoinTier> tiers
    ) {
        long totalStakePoints = 0L;
        long markedOpenPositionPoints = 0L;
        long unrealizedPnlPoints = 0L;

        for (GamePosition position : openPositions) {
            TrendSignal signal = signalByVideoId.get(position.getVideoId());
            long markedValue = position.getStakePoints();
            totalStakePoints += position.getStakePoints();

            if (signal != null) {
                markedValue = GamePointCalculator.calculatePositionPoints(
                    GamePointCalculator.calculatePricePoints(signal.getCurrentRank()),
                    getPositionQuantity(position)
                );
            } else {
                OpenPositionSnapshot snapshot = resolveOpenPositionSnapshot(position);
                if (snapshot != null) {
                    markedValue = GamePointCalculator.calculatePositionPoints(
                        resolveOpenPositionUnitPricePoints(snapshot),
                        getPositionQuantity(position)
                    );
                }
            }

            markedOpenPositionPoints += markedValue;
            unrealizedPnlPoints += markedValue - position.getStakePoints();
        }

        return new LeaderboardSnapshot(
            wallet.getUser().getId(),
            wallet.getUser().getDisplayName(),
            wallet.getUser().getPictureUrl(),
            toCoinTierResponse(gameCoinTierService.resolveTier(tiers, wallet.getCoinBalance())),
            wallet.getCoinBalance(),
            wallet.getBalancePoints() + markedOpenPositionPoints,
            wallet.getBalancePoints(),
            wallet.getReservedPoints(),
            totalStakePoints,
            markedOpenPositionPoints,
            calculateProfitRatePercent(unrealizedPnlPoints, totalStakePoints),
            wallet.getRealizedPnlPoints(),
            unrealizedPnlPoints,
            openPositions.stream().mapToInt(this::getPositionQuantity).sum()
        );
    }

    private List<LeaderboardEntryResponse> toLeaderboardResponses(List<LeaderboardSnapshot> snapshots, Long currentUserId) {
        return java.util.stream.IntStream.range(0, snapshots.size())
            .mapToObj(index -> {
                LeaderboardSnapshot snapshot = snapshots.get(index);
                return new LeaderboardEntryResponse(
                    index + 1,
                    snapshot.userId(),
                    snapshot.displayName(),
                    snapshot.pictureUrl(),
                    snapshot.currentTier(),
                    snapshot.coinBalance(),
                    snapshot.totalAssetPoints(),
                    snapshot.balancePoints(),
                    snapshot.reservedPoints(),
                    snapshot.totalStakePoints(),
                    snapshot.totalEvaluationPoints(),
                    snapshot.profitRatePercent(),
                    snapshot.realizedPnlPoints(),
                    snapshot.unrealizedPnlPoints(),
                    snapshot.openPositionCount(),
                    snapshot.userId().equals(currentUserId)
                );
            })
            .toList();
    }

    private GameWallet createWallet(GameSeason season, Long userId) {
        Instant now = Instant.now(clock);
        AppUser user = requireUser(userId);

        GameWallet wallet = new GameWallet();
        wallet.setSeason(season);
        wallet.setUser(user);
        wallet.setBalancePoints(season.getStartingBalancePoints());
        wallet.setReservedPoints(0L);
        wallet.setRealizedPnlPoints(0L);
        wallet.setCoinBalance(0L);
        wallet.setUpdatedAt(now);
        GameWallet savedWallet = gameWalletRepository.saveAndFlush(wallet);

        saveLedger(season, user, null, LedgerType.SEED, season.getStartingBalancePoints(), savedWallet.getBalancePoints(), now);
        return savedWallet;
    }

    private GameWallet createWalletAndLock(GameSeason season, Long userId) {
        try {
            createWallet(season, userId);
        } catch (DataIntegrityViolationException ignored) {
            // Another request created the wallet first. Fetch the canonical row with a lock below.
        }

        return requireWalletForUpdate(season.getId(), userId);
    }

    private GameWallet requireWalletForUpdate(Long seasonId, Long userId) {
        return gameWalletRepository.findBySeasonIdAndUserIdForUpdate(seasonId, userId)
            .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
    }

    private GamePosition createOpenPosition(
        GameSeason season,
        AppUser user,
        String regionCode,
        String categoryId,
        String videoId,
        TrendSignal signal,
        int quantity,
        long totalStakePoints,
        Instant now
    ) {
        GamePosition position = new GamePosition();
        position.setSeason(season);
        position.setUser(user);
        position.setRegionCode(regionCode);
        position.setCategoryId(categoryId);
        position.setVideoId(videoId);
        position.setTitle(signal.getTitle());
        position.setChannelTitle(signal.getChannelTitle());
        position.setThumbnailUrl(signal.getThumbnailUrl());
        position.setBuyRunId(signal.getCurrentRunId());
        position.setBuyRank(signal.getCurrentRank());
        position.setBuyCapturedAt(signal.getCapturedAt());
        position.setQuantity(quantity);
        position.setStakePoints(totalStakePoints);
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(now);
        return gamePositionRepository.save(position);
    }

    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private TrendSignal requireTrendSignal(String regionCode, String categoryId, String videoId) {
        return trendSignalRepository.findById(new TrendSignalId(regionCode, categoryId, videoId))
            .orElseThrow(() -> new IllegalArgumentException("현재 거래 가능한 랭킹 정보를 찾을 수 없습니다."));
    }

    private SellSnapshot resolveSellSnapshot(GamePosition position) {
        TrendSignalId signalId = new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId());
        TrendSignal signal = trendSignalRepository.findById(signalId).orElse(null);
        if (signal != null) {
            return new SellSnapshot(signal.getCurrentRunId(), signal.getCurrentRank(), signal.getCapturedAt(), false);
        }

        LatestTrendRun latestTrendRun = getLatestTrendRun(position.getRegionCode(), position.getCategoryId());
        if (latestTrendRun == null) {
            throw manualSellUnavailable();
        }

        TrendSnapshot latestSnapshot = latestTrendRun.snapshots().stream()
            .filter(snapshot -> snapshot.getVideoId().equals(position.getVideoId()))
            .findFirst()
            .orElse(null);
        if (latestSnapshot != null) {
            return new SellSnapshot(latestTrendRun.run().getId(), latestSnapshot.getRank(), latestTrendRun.run().getCapturedAt(), false);
        }

        ensureLatestRunLooksHealthy(
            position.getRegionCode(),
            position.getCategoryId(),
            latestTrendRun.run().getId(),
            latestTrendRun.snapshots().size()
        );

        int fallbackRank = latestTrendRun.snapshots().stream()
            .map(TrendSnapshot::getRank)
            .max(Integer::compareTo)
            .map(rank -> rank + 1)
            .orElse(DEFAULT_FALLBACK_RANK);
        return new SellSnapshot(latestTrendRun.run().getId(), fallbackRank, latestTrendRun.run().getCapturedAt(), true);
    }

    private OpenPositionSnapshot resolveOpenPositionSnapshot(GamePosition position) {
        TrendSignalId signalId = new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId());
        TrendSignal signal = trendSignalRepository.findById(signalId).orElse(null);
        if (signal != null) {
            return new OpenPositionSnapshot(signal.getCurrentRank(), signal.getCapturedAt(), false);
        }

        LatestTrendRun latestTrendRun = getLatestTrendRun(position.getRegionCode(), position.getCategoryId());
        if (latestTrendRun == null) {
            return null;
        }

        TrendSnapshot latestSnapshot = latestTrendRun.snapshots().stream()
            .filter(snapshot -> snapshot.getVideoId().equals(position.getVideoId()))
            .findFirst()
            .orElse(null);
        if (latestSnapshot != null) {
            return new OpenPositionSnapshot(latestSnapshot.getRank(), latestTrendRun.run().getCapturedAt(), false);
        }

        if (!latestRunLooksHealthy(
            position.getRegionCode(),
            position.getCategoryId(),
            latestTrendRun.run().getId(),
            latestTrendRun.snapshots().size()
        )) {
            return null;
        }

        int fallbackRank = latestTrendRun.snapshots().stream()
            .map(TrendSnapshot::getRank)
            .max(Integer::compareTo)
            .map(rank -> rank + 1)
            .orElse(DEFAULT_FALLBACK_RANK);
        return new OpenPositionSnapshot(fallbackRank, latestTrendRun.run().getCapturedAt(), true);
    }

    private LatestTrendRun getLatestTrendRun(String regionCode, String categoryId) {
        TrendRun latestRun = trendRunRepository.findTopByRegionCodeAndCategoryIdOrderByIdDesc(regionCode, categoryId).orElse(null);
        if (latestRun == null) {
            return null;
        }

        List<TrendSnapshot> latestSnapshots = trendSnapshotRepository.findByRunId(latestRun.getId());
        if (latestSnapshots.isEmpty()) {
            return null;
        }

        return new LatestTrendRun(latestRun, latestSnapshots);
    }

    private SellPositionResponse closePosition(
        GamePosition position,
        int sellQuantity,
        SellSnapshot sellSnapshot,
        GameWallet wallet,
        Instant now
    ) {
        int rankDiff = position.getBuyRank() - sellSnapshot.rank();
        long unitStakePoints = resolveUnitStakePoints(position);
        long soldStakePoints = GamePointCalculator.calculatePositionPoints(unitStakePoints, sellQuantity);
        long sellPricePoints = GamePointCalculator.calculatePositionPoints(
            resolveSellUnitPricePoints(sellSnapshot),
            sellQuantity
        );
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
        long pnlPoints = GamePointCalculator.calculateProfitPoints(soldStakePoints, settledPoints);
        GamePosition settledPosition;

        if (sellQuantity == getPositionQuantity(position)) {
            position.setSellRunId(sellSnapshot.runId());
            position.setSellRank(sellSnapshot.rank());
            position.setSellCapturedAt(sellSnapshot.capturedAt());
            position.setRankDiff(rankDiff);
            position.setPnlPoints(pnlPoints);
            position.setSettledPoints(settledPoints);
            position.setStatus(PositionStatus.CLOSED);
            position.setClosedAt(now);
            settledPosition = gamePositionRepository.save(position);
        } else {
            position.setQuantity(getPositionQuantity(position) - sellQuantity);
            position.setStakePoints(position.getStakePoints() - soldStakePoints);
            gamePositionRepository.save(position);
            settledPosition = gamePositionRepository.save(
                createClosedPosition(position, sellQuantity, soldStakePoints, sellSnapshot, rankDiff, pnlPoints, settledPoints, now)
            );
        }

        wallet.setReservedPoints(wallet.getReservedPoints() - soldStakePoints);
        wallet.setBalancePoints(wallet.getBalancePoints() + settledPoints);
        wallet.setRealizedPnlPoints(wallet.getRealizedPnlPoints() + pnlPoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        saveLedger(
            settledPosition.getSeason(),
            settledPosition.getUser(),
            settledPosition,
            LedgerType.SELL_SETTLE,
            settledPoints,
            wallet.getBalancePoints(),
            now
        );

        return new SellPositionResponse(
            settledPosition.getId(),
            settledPosition.getVideoId(),
            settledPosition.getBuyRank(),
            settledPosition.getSellRank(),
            settledPosition.getRankDiff(),
            sellQuantity,
            soldStakePoints,
            sellPricePoints,
            pnlPoints,
            settledPoints,
            wallet.getBalancePoints(),
            settledPosition.getClosedAt()
        );
    }

    private void ensureLatestRunLooksHealthy(String regionCode, String categoryId, Long latestRunId, int latestSnapshotCount) {
        if (!latestRunLooksHealthy(regionCode, categoryId, latestRunId, latestSnapshotCount)) {
            throw manualSellUnavailable();
        }
    }

    private void ensurePositionOpen(GamePosition position) {
        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalArgumentException("이미 종료된 포지션입니다.");
        }
    }

    private void ensurePositionSellable(GamePosition position, Instant now) {
        if (!canSellPosition(position, now)) {
            throw new IllegalArgumentException("최소 보유 시간이 지나야 매도할 수 있습니다.");
        }
    }

    private boolean canSellPosition(GamePosition position, Instant now) {
        ensurePositionOpen(position);
        long heldSeconds = now.getEpochSecond() - position.getCreatedAt().getEpochSecond();
        return heldSeconds >= position.getSeason().getMinHoldSeconds();
    }

    private boolean latestRunLooksHealthy(String regionCode, String categoryId, Long latestRunId, int latestSnapshotCount) {
        TrendRun previousRun = trendRunRepository.findTopByRegionCodeAndCategoryIdAndIdLessThanOrderByIdDesc(
            regionCode,
            categoryId,
            latestRunId
        ).orElse(null);
        if (previousRun == null) {
            return true;
        }

        int previousSnapshotCount = trendSnapshotRepository.findByRunId(previousRun.getId()).size();
        return previousSnapshotCount <= 0 || latestSnapshotCount * 2 >= previousSnapshotCount;
    }

    private IllegalArgumentException manualSellUnavailable() {
        return new IllegalArgumentException("현재 랭킹 동기화 상태를 확인할 수 없어 수동 매도할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    private long normalizeStakePoints(Long stakePoints) {
        if (stakePoints == null) {
            throw new IllegalArgumentException("stakePoints는 필수입니다.");
        }

        if (stakePoints < 0L) {
            throw new IllegalArgumentException("stakePoints는 0 이상이어야 합니다.");
        }

        return stakePoints;
    }

    private int normalizeQuantity(Integer quantity) {
        if (quantity == null) {
            throw new IllegalArgumentException("quantity는 필수입니다.");
        }

        if (quantity < GamePointCalculator.MIN_QUANTITY) {
            throw new IllegalArgumentException("quantity는 0.01개 이상이어야 합니다.");
        }

        return quantity;
    }

    private int normalizePositionFetchLimit(Integer limit) {
        if (limit == null) {
            return Integer.MAX_VALUE;
        }

        if (limit < 1) {
            throw new IllegalArgumentException("limit는 1 이상이어야 합니다.");
        }

        return Math.min(limit, 30);
    }

    private int getPositionQuantity(GamePosition position) {
        return position.getQuantity() == null || position.getQuantity() < GamePointCalculator.MIN_QUANTITY
            ? GamePointCalculator.QUANTITY_SCALE
            : position.getQuantity();
    }

    private long resolveOpenPositionUnitPricePoints(OpenPositionSnapshot snapshot) {
        return snapshot.chartOut()
            ? GamePointCalculator.calculateChartOutUnitPricePoints()
            : GamePointCalculator.calculatePricePoints(snapshot.currentRank());
    }

    private long resolveSellUnitPricePoints(SellSnapshot sellSnapshot) {
        return sellSnapshot.chartOut()
            ? GamePointCalculator.calculateChartOutUnitPricePoints()
            : GamePointCalculator.calculatePricePoints(sellSnapshot.rank());
    }

    static int resolveCoinRateBasisPoints(int rank) {
        if (rank < 1 || rank > COIN_ELIGIBLE_RANK_CUTOFF) {
            return 0;
        }

        if (rank == 1) {
            return TOP_RANK_COIN_RATE_BASIS_POINTS;
        }

        double progress = (double) (COIN_ELIGIBLE_RANK_CUTOFF - rank) / (COIN_ELIGIBLE_RANK_CUTOFF - 1);
        double curvedProgress = Math.pow(progress, COIN_RATE_CURVE_POWER);
        return (int) Math.round(
            TOP_RANK_COIN_RATE_BASIS_POINTS
                * curvedProgress
                + LAST_ELIGIBLE_RANK_COIN_RATE_BASIS_POINTS * (1D - curvedProgress)
        );
    }

    private double resolveCoinRatePercent(int rank) {
        return basisPointsToPercent(resolveCoinRateBasisPoints(rank));
    }

    private double basisPointsToPercent(int basisPoints) {
        return basisPoints / 100D;
    }

    private Double calculateProfitRatePercent(long profitPoints, long stakePoints) {
        if (stakePoints <= 0L) {
            return null;
        }

        return Math.round((double) profitPoints * 1000D / stakePoints) / 10D;
    }

    static long calculateEstimatedCoinYield(long currentValuePoints, int coinRateBasisPoints) {
        if (currentValuePoints <= 0L || coinRateBasisPoints <= 0) {
            return 0L;
        }

        return Math.round((double) currentValuePoints * coinRateBasisPoints / 10_000D);
    }

    static int calculateHoldBoostBasisPoints(
        long heldSeconds,
        int minHoldSeconds,
        int boostIntervalSeconds,
        int boostBasisPoints,
        int maxBoostBasisPoints
    ) {
        if (heldSeconds < minHoldSeconds || boostIntervalSeconds <= 0 || boostBasisPoints <= 0 || maxBoostBasisPoints <= 0) {
            return 0;
        }

        long eligibleHeldSeconds = heldSeconds - minHoldSeconds;
        long completedIntervals = eligibleHeldSeconds / boostIntervalSeconds;
        long accumulatedBoost = completedIntervals * (long) boostBasisPoints;
        return (int) Math.min(Math.max(0L, accumulatedBoost), maxBoostBasisPoints);
    }

    static int calculateEffectiveCoinRateBasisPoints(int baseCoinRateBasisPoints, int holdBoostBasisPoints) {
        if (baseCoinRateBasisPoints <= 0) {
            return 0;
        }

        if (holdBoostBasisPoints <= 0) {
            return baseCoinRateBasisPoints;
        }

        return (int) Math.round(baseCoinRateBasisPoints * (10_000D + holdBoostBasisPoints) / 10_000D);
    }

    private long resolveUnitStakePoints(GamePosition position) {
        return GamePointCalculator.estimateUnitPricePoints(position.getStakePoints(), getPositionQuantity(position));
    }

    private GamePosition createClosedPosition(
        GamePosition source,
        int quantity,
        long stakePoints,
        SellSnapshot sellSnapshot,
        int rankDiff,
        long pnlPoints,
        long settledPoints,
        Instant now
    ) {
        GamePosition position = new GamePosition();
        position.setSeason(source.getSeason());
        position.setUser(source.getUser());
        position.setRegionCode(source.getRegionCode());
        position.setCategoryId(source.getCategoryId());
        position.setVideoId(source.getVideoId());
        position.setTitle(source.getTitle());
        position.setChannelTitle(source.getChannelTitle());
        position.setThumbnailUrl(source.getThumbnailUrl());
        position.setBuyRunId(source.getBuyRunId());
        position.setBuyRank(source.getBuyRank());
        position.setBuyCapturedAt(source.getBuyCapturedAt());
        position.setQuantity(quantity);
        position.setStakePoints(stakePoints);
        position.setStatus(PositionStatus.CLOSED);
        position.setSellRunId(sellSnapshot.runId());
        position.setSellRank(sellSnapshot.rank());
        position.setSellCapturedAt(sellSnapshot.capturedAt());
        position.setRankDiff(rankDiff);
        position.setPnlPoints(pnlPoints);
        position.setSettledPoints(settledPoints);
        position.setCreatedAt(source.getCreatedAt());
        position.setClosedAt(now);
        return position;
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    private PositionStatus parsePositionStatus(String status) {
        try {
            return PositionStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 status 값입니다.");
        }
    }

    private void saveLedger(
        GameSeason season,
        AppUser user,
        GamePosition position,
        LedgerType type,
        long amountPoints,
        long balanceAfterPoints,
        Instant createdAt
    ) {
        gameLedgerRepository.save(createLedger(season, user, position, type, amountPoints, balanceAfterPoints, createdAt));
    }

    private GameLedger createLedger(
        GameSeason season,
        AppUser user,
        GamePosition position,
        LedgerType type,
        long amountPoints,
        long balanceAfterPoints,
        Instant createdAt
    ) {
        GameLedger ledger = new GameLedger();
        ledger.setSeason(season);
        ledger.setUser(user);
        ledger.setPosition(position);
        ledger.setType(type);
        ledger.setAmountPoints(amountPoints);
        ledger.setBalanceAfterPoints(balanceAfterPoints);
        ledger.setCreatedAt(createdAt);
        return ledger;
    }

    private record LeaderboardSnapshot(
        Long userId,
        String displayName,
        String pictureUrl,
        CoinTierResponse currentTier,
        Long coinBalance,
        Long totalAssetPoints,
        Long balancePoints,
        Long reservedPoints,
        Long totalStakePoints,
        Long totalEvaluationPoints,
        Double profitRatePercent,
        Long realizedPnlPoints,
        Long unrealizedPnlPoints,
        Integer openPositionCount
    ) {
    }

    private record SellSnapshot(Long runId, int rank, Instant capturedAt, boolean chartOut) {
    }

    private record OpenPositionSnapshot(int currentRank, Instant capturedAt, boolean chartOut) {
    }

    private record LatestTrendRun(TrendRun run, List<TrendSnapshot> snapshots) {
    }

    private record PositionHistoryWindow(Long startRunId, Long endRunId, Integer latestRank, Instant latestCapturedAt) {
    }

    private record CoinPositionCandidate(
        GamePosition position,
        Integer currentRank,
        Long currentValuePoints,
        boolean rankEligible,
        boolean productionActive,
        int coinRateBasisPoints,
        int holdBoostBasisPoints,
        int effectiveCoinRateBasisPoints,
        long estimatedCoinYield,
        Long nextProductionInSeconds,
        Long nextPayoutInSeconds
    ) {
    }

    private List<PositionRankHistoryPointResponse> collapsePositionHistoryPoints(List<PositionRankHistoryPointResponse> points) {
        Map<Instant, PositionRankHistoryPointResponse> pointsBySlot = new LinkedHashMap<>();

        for (PositionRankHistoryPointResponse point : points) {
            Instant captureSlotAt = resolveTrendCaptureSlot(point.capturedAt());
            PositionRankHistoryPointResponse existing = pointsBySlot.get(captureSlotAt);

            if (existing == null) {
                pointsBySlot.put(
                    captureSlotAt,
                    new PositionRankHistoryPointResponse(
                        point.runId(),
                        captureSlotAt,
                        point.rank(),
                        point.viewCount(),
                        point.chartOut(),
                        point.buyPoint(),
                        point.sellPoint()
                    )
                );
                continue;
            }

            Integer mergedRank = point.rank() != null ? point.rank() : existing.rank();
            Long mergedViewCount = point.viewCount() != null ? point.viewCount() : existing.viewCount();

            pointsBySlot.put(
                captureSlotAt,
                new PositionRankHistoryPointResponse(
                    point.runId(),
                    captureSlotAt,
                    mergedRank,
                    mergedViewCount,
                    mergedRank == null,
                    existing.buyPoint() || point.buyPoint(),
                    existing.sellPoint() || point.sellPoint()
                )
            );
        }

        return new ArrayList<>(pointsBySlot.values());
    }

    private Instant resolveTrendCaptureSlot(Instant capturedAt) {
        int slotMinutes = trendCaptureSlotMinutes;
        if (slotMinutes <= 0) {
            return capturedAt.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        }

        long slotSeconds = slotMinutes * 60L;
        long slotEpochSeconds = Math.floorDiv(capturedAt.getEpochSecond(), slotSeconds) * slotSeconds;
        return Instant.ofEpochSecond(slotEpochSeconds);
    }
}
