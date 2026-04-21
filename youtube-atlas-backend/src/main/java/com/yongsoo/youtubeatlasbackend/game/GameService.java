package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.config.AtlasProperties;
import com.yongsoo.youtubeatlasbackend.comments.CommentService;
import com.yongsoo.youtubeatlasbackend.game.api.TierProgressResponse;
import com.yongsoo.youtubeatlasbackend.game.api.TierResponse;
import com.yongsoo.youtubeatlasbackend.game.api.CreatePositionRequest;
import com.yongsoo.youtubeatlasbackend.game.api.CurrentSeasonResponse;
import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;
import com.yongsoo.youtubeatlasbackend.game.api.LeaderboardEntryResponse;
import com.yongsoo.youtubeatlasbackend.game.api.MarketVideoResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionRankHistoryPointResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionRankHistoryResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPreviewItemResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPreviewResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionsRequest;
import com.yongsoo.youtubeatlasbackend.game.api.WalletResponse;
import com.yongsoo.youtubeatlasbackend.trending.TrendRun;
import com.yongsoo.youtubeatlasbackend.trending.TrendRunRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshot;
import com.yongsoo.youtubeatlasbackend.trending.TrendSnapshotRepository;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoCategorySectionResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse.ContentDetailsResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse.SnippetResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse.StatisticsResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse.ThumbnailResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse.ThumbnailsResponse;
import com.yongsoo.youtubeatlasbackend.youtube.api.VideoItemResponse.TrendResponse;

@Service
public class GameService {

    private static final String TRENDING_CATEGORY_ID = "0";
    private static final String BUYABLE_CHART_CATEGORY_ID = "buyable-market";
    private static final String BUYABLE_CHART_LABEL = "매수 가능";
    private static final String BUYABLE_CHART_DESCRIPTION = "현재 지갑과 보유 상태 기준으로 바로 매수 가능한 영상만 모았습니다.";
    private static final int BUYABLE_CHART_MAX_COUNT = 200;
    private static final int BUYABLE_CHART_PAGE_SIZE = 50;
    private static final int DEFAULT_FALLBACK_RANK = 201;
    private static final int MOONSHOT_BUY_RANK_MIN = 100;
    private static final int MOONSHOT_TARGET_RANK_MAX = 20;
    private static final int SNIPE_BUY_RANK_MIN = 150;
    private static final int SNIPE_TARGET_RANK_MAX = 100;
    private static final long MOONSHOT_HIGHLIGHT_BASE_SCORE = 5_000L;
    private static final long BIG_CASHOUT_HIGHLIGHT_BASE_SCORE = 5_000L;
    private static final long SMALL_CASHOUT_HIGHLIGHT_BASE_SCORE = 2_500L;
    private static final long SNIPE_HIGHLIGHT_BASE_SCORE = 2_500L;
    private static final long RANK_DIFF_HIGHLIGHT_SCORE_MULTIPLIER = 20L;
    private static final long PROFIT_RATE_HIGHLIGHT_SCORE_MULTIPLIER = 10L;
    private static final long MAX_PROFIT_RATE_HIGHLIGHT_BONUS = 5_000L;
    private static final long MIN_PROFIT_POINTS_HIGHLIGHT_BONUS = 5_000L;
    private static final double PROFIT_POINTS_HIGHLIGHT_SQRT_SCALE = 3D;
    private static final long MAX_PROFIT_POINTS_HIGHLIGHT_BONUS = 30_000L;

    private final GameSeasonRepository gameSeasonRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameHighlightStateRepository gameHighlightStateRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final GameTierService gameTierService;
    private final GameNotificationService gameNotificationService;
    private final AppUserRepository appUserRepository;
    private final TrendSignalRepository trendSignalRepository;
    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;
    private final CommentService commentService;
    private final Clock clock;
    private final int trendCaptureSlotMinutes;

    public GameService(
        GameSeasonRepository gameSeasonRepository,
        GameWalletRepository gameWalletRepository,
        GamePositionRepository gamePositionRepository,
        GameHighlightStateRepository gameHighlightStateRepository,
        GameLedgerRepository gameLedgerRepository,
        GameTierService gameTierService,
        GameNotificationService gameNotificationService,
        AppUserRepository appUserRepository,
        TrendSignalRepository trendSignalRepository,
        TrendRunRepository trendRunRepository,
        TrendSnapshotRepository trendSnapshotRepository,
        CommentService commentService,
        Clock clock,
        AtlasProperties atlasProperties
    ) {
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameHighlightStateRepository = gameHighlightStateRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameTierService = gameTierService;
        this.gameNotificationService = gameNotificationService;
        this.appUserRepository = appUserRepository;
        this.trendSignalRepository = trendSignalRepository;
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
        this.commentService = commentService;
        this.clock = clock;
        this.trendCaptureSlotMinutes = atlasProperties.getTrending().getCaptureSlotMinutes();
    }

    @Transactional
    public CurrentSeasonResponse getCurrentSeason(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        return toCurrentSeasonResponse(season, wallet, syncAndListNotifications(season, authenticatedUser.id()));
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
        return buildMarketVideos(season, authenticatedUser, wallet);
    }

    @Transactional
    public VideoCategorySectionResponse getBuyableMarketChart(
        AuthenticatedUser authenticatedUser,
        String regionCode,
        String pageToken
    ) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        int startIndex = parseBuyableChartPageToken(pageToken);
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
        Map<String, TrendSignal> signalsByVideoId = trendSignalRepository.findByIdRegionCodeAndIdCategoryId(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity(), (left, right) -> left));
        List<TrendSnapshot> latestSnapshots = trendSnapshotRepository
            .findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc(
                season.getRegionCode(),
                TRENDING_CATEGORY_ID
            );

        if (latestSnapshots.isEmpty()) {
            throw new IllegalArgumentException("최신 트렌드 스냅샷이 없습니다.");
        }
        PreviousSnapshotContext previousSnapshotContext = loadPreviousSnapshotContext(latestSnapshots);

        List<TrendSnapshot> buyableSnapshots = latestSnapshots.stream()
            .limit(BUYABLE_CHART_MAX_COUNT)
            .filter(snapshot -> {
                long snapshotUnitPricePoints = GamePointCalculator.calculatePricePoints(snapshot.getRank());
                boolean alreadyOwned = ownedVideoIds.contains(snapshot.getVideoId());
                return resolveBuyBlockedReason(wallet, snapshotUnitPricePoints, maxOpenReached, alreadyOwned) == null;
            })
            .toList();

        if (startIndex >= buyableSnapshots.size()) {
            return new VideoCategorySectionResponse(
                BUYABLE_CHART_CATEGORY_ID,
                BUYABLE_CHART_LABEL,
                BUYABLE_CHART_DESCRIPTION,
                List.of(),
                List.of(),
                null
            );
        }

        int endIndex = Math.min(startIndex + BUYABLE_CHART_PAGE_SIZE, buyableSnapshots.size());
        List<VideoItemResponse> items = buyableSnapshots.subList(startIndex, endIndex).stream()
            .map(snapshot -> toBuyableChartVideoResponse(
                snapshot,
                snapshot.getRun(),
                signalsByVideoId.get(snapshot.getVideoId()),
                previousSnapshotContext.snapshotsByVideoId().get(snapshot.getVideoId()),
                previousSnapshotContext.hasPreviousRun()
            ))
            .toList();

        return new VideoCategorySectionResponse(
            BUYABLE_CHART_CATEGORY_ID,
            BUYABLE_CHART_LABEL,
            BUYABLE_CHART_DESCRIPTION,
            List.of(),
            items,
            endIndex < buyableSnapshots.size() ? Integer.toString(endIndex) : null
        );
    }

    private List<MarketVideoResponse> buildMarketVideos(
        GameSeason season,
        AuthenticatedUser authenticatedUser,
        GameWallet wallet
    ) {
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
        List<TrendSignal> rankedSignals = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        );
        Map<String, TrendSignal> signalsByVideoId = rankedSignals.stream()
            .collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity(), (left, right) -> left));
        List<TrendSnapshot> latestSnapshots = trendSnapshotRepository.findLatestSnapshotRunByRegionCodeAndCategoryIdOrderByRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        );

        if (latestSnapshots.isEmpty()) {
            return rankedSignals.stream()
                .map(signal -> {
                    long basePricePoints = GamePointCalculator.calculatePricePoints(signal.getCurrentRank());
                    long currentPricePoints = GamePointCalculator.calculateMomentumAdjustedPricePoints(
                        signal.getCurrentRank(),
                        signal.getRankChange()
                    );
                    long momentumPriceDeltaPoints = currentPricePoints - basePricePoints;
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
                        basePricePoints,
                        currentPricePoints,
                        momentumPriceDeltaPoints,
                        calculateMomentumPriceDeltaPercent(basePricePoints, momentumPriceDeltaPoints),
                        resolveMomentumPriceType(momentumPriceDeltaPoints),
                        signal.getCurrentViewCount(),
                        signal.getViewCountDelta(),
                        signal.isNew(),
                        blockedReason == null,
                        blockedReason,
                        signal.getCapturedAt()
                    );
                })
                .toList();
        }

        PreviousSnapshotContext previousSnapshotContext = loadPreviousSnapshotContext(latestSnapshots);

        return latestSnapshots.stream().map(snapshot -> {
            TrendSignal signal = signalsByVideoId.get(snapshot.getVideoId());
            TrendSnapshot previousSnapshot = previousSnapshotContext.snapshotsByVideoId().get(snapshot.getVideoId());
            boolean currentSignal = isCurrentSignalForSnapshot(
                signal,
                snapshot,
                previousSnapshot,
                previousSnapshotContext.hasPreviousRun()
            );
            int currentRank = currentSignal ? signal.getCurrentRank() : snapshot.getRank();
            Integer previousRank = currentSignal ? signal.getPreviousRank() : previousSnapshot != null ? previousSnapshot.getRank() : null;
            Integer rankChange = currentSignal ? signal.getRankChange() : previousSnapshot != null ? previousSnapshot.getRank() - snapshot.getRank() : null;
            Long currentViewCount = currentSignal ? signal.getCurrentViewCount() : snapshot.getViewCount();
            Long viewCountDelta = currentSignal
                ? signal.getViewCountDelta()
                : previousSnapshot != null && previousSnapshot.getViewCount() != null && snapshot.getViewCount() != null
                    ? snapshot.getViewCount() - previousSnapshot.getViewCount()
                    : null;
            boolean isNew = currentSignal ? signal.isNew() : previousSnapshotContext.hasPreviousRun() && previousSnapshot == null;
            String videoId = currentSignal ? signal.getId().getVideoId() : snapshot.getVideoId();
            long basePricePoints = GamePointCalculator.calculatePricePoints(currentRank);
            long currentPricePoints = GamePointCalculator.calculateMomentumAdjustedPricePoints(
                currentRank,
                rankChange
            );
            long momentumPriceDeltaPoints = currentPricePoints - basePricePoints;
            boolean alreadyOwned = ownedVideoIds.contains(videoId);
            String blockedReason = resolveBuyBlockedReason(wallet, currentPricePoints, maxOpenReached, alreadyOwned);

            return new MarketVideoResponse(
                videoId,
                currentSignal ? signal.getTitle() : snapshot.getTitle(),
                currentSignal ? signal.getChannelTitle() : snapshot.getChannelTitle(),
                currentSignal ? signal.getThumbnailUrl() : snapshot.getThumbnailUrl(),
                currentRank,
                previousRank,
                rankChange,
                basePricePoints,
                currentPricePoints,
                momentumPriceDeltaPoints,
                calculateMomentumPriceDeltaPercent(basePricePoints, momentumPriceDeltaPoints),
                resolveMomentumPriceType(momentumPriceDeltaPoints),
                currentViewCount,
                viewCountDelta,
                isNew,
                blockedReason == null,
                blockedReason,
                currentSignal ? signal.getCapturedAt() : snapshot.getRun().getCapturedAt()
            );
        }).toList();
    }

    private VideoItemResponse toBuyableChartVideoResponse(
        TrendSnapshot snapshot,
        TrendRun run,
        TrendSignal signal,
        TrendSnapshot previousSnapshot,
        boolean hasPreviousRun
    ) {
        ThumbnailResponse thumbnail = new ThumbnailResponse(snapshot.getThumbnailUrl(), null, null);
        boolean currentSignal = isCurrentSignalForSnapshot(signal, snapshot, previousSnapshot, hasPreviousRun);

        return new VideoItemResponse(
            snapshot.getVideoId(),
            new ContentDetailsResponse(""),
            new SnippetResponse(
                snapshot.getTitle(),
                snapshot.getChannelTitle(),
                snapshot.getChannelId(),
                snapshot.getVideoCategoryId(),
                snapshot.getVideoCategoryLabel(),
                snapshot.getPublishedAt() != null ? snapshot.getPublishedAt().toString() : null,
                new ThumbnailsResponse(thumbnail, thumbnail, thumbnail, thumbnail, thumbnail)
            ),
            new StatisticsResponse(snapshot.getViewCount()),
            currentSignal
                ? new TrendResponse(
                    "전체",
                    signal.getCurrentRank(),
                    signal.getPreviousRank(),
                    signal.getRankChange(),
                    signal.getCurrentViewCount(),
                    signal.getPreviousViewCount(),
                    signal.getViewCountDelta(),
                    signal.isNew(),
                    signal.getCapturedAt()
                )
                : new TrendResponse(
                    "전체",
                    snapshot.getRank(),
                    previousSnapshot != null ? previousSnapshot.getRank() : null,
                    previousSnapshot != null ? previousSnapshot.getRank() - snapshot.getRank() : null,
                    snapshot.getViewCount(),
                    previousSnapshot != null ? previousSnapshot.getViewCount() : null,
                    previousSnapshot != null && previousSnapshot.getViewCount() != null && snapshot.getViewCount() != null
                        ? snapshot.getViewCount() - previousSnapshot.getViewCount()
                        : null,
                    hasPreviousRun && previousSnapshot == null,
                    run.getCapturedAt()
                )
        );
    }

    private PreviousSnapshotContext loadPreviousSnapshotContext(List<TrendSnapshot> currentSnapshots) {
        if (currentSnapshots.isEmpty()) {
            return new PreviousSnapshotContext(false, Map.of());
        }

        TrendRun currentRun = currentSnapshots.getFirst().getRun();
        TrendRun previousRun = trendRunRepository
            .findTopByRegionCodeAndCategoryIdAndSourceAndCapturedAtBeforeOrderByCapturedAtDescIdDesc(
                currentRun.getRegionCode(),
                currentRun.getCategoryId(),
                currentRun.getSource(),
                currentRun.getCapturedAt()
            )
            .orElse(null);
        if (previousRun == null) {
            return new PreviousSnapshotContext(false, Map.of());
        }

        Map<String, TrendSnapshot> snapshotsByVideoId = trendSnapshotRepository.findByRunId(previousRun.getId()).stream()
            .collect(Collectors.toMap(TrendSnapshot::getVideoId, Function.identity(), (left, right) -> left));
        return new PreviousSnapshotContext(true, snapshotsByVideoId);
    }

    private boolean isCurrentSignalForSnapshot(
        TrendSignal signal,
        TrendSnapshot snapshot,
        TrendSnapshot previousSnapshot,
        boolean hasPreviousRun
    ) {
        if (signal == null) {
            return false;
        }

        if (signal.getCurrentRunId() != null
            && signal.getPreviousRunId() != null
            && signal.getCurrentRunId().equals(signal.getPreviousRunId())) {
            return false;
        }

        boolean currentSnapshotMatches = signal != null
            && signal.getCurrentRank() != null
            && signal.getCurrentRank().equals(snapshot.getRank())
            && (
                signal.getCurrentRunId() != null && signal.getCurrentRunId().equals(snapshot.getRun().getId())
                    || signal.getCapturedAt() != null && signal.getCapturedAt().equals(snapshot.getRun().getCapturedAt())
            );

        if (!currentSnapshotMatches) {
            return false;
        }

        if (previousSnapshot == null && !hasPreviousRun) {
            return true;
        }

        Integer expectedPreviousRank = previousSnapshot != null ? previousSnapshot.getRank() : null;
        Integer expectedRankChange = previousSnapshot != null ? previousSnapshot.getRank() - snapshot.getRank() : null;
        boolean expectedIsNew = hasPreviousRun && previousSnapshot == null;

        return Objects.equals(signal.getPreviousRank(), expectedPreviousRank)
            && Objects.equals(signal.getRankChange(), expectedRankChange)
            && signal.isNew() == expectedIsNew;
    }

    private int parseBuyableChartPageToken(String pageToken) {
        if (!StringUtils.hasText(pageToken)) {
            return 0;
        }

        try {
            int parsedValue = Integer.parseInt(pageToken.trim());
            if (parsedValue < 0) {
                throw new IllegalArgumentException("pageToken은 0 이상이어야 합니다.");
            }

            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("pageToken 형식이 올바르지 않습니다.");
        }
    }

    private double calculateMomentumPriceDeltaPercent(long basePricePoints, long momentumPriceDeltaPoints) {
        if (basePricePoints <= 0L || momentumPriceDeltaPoints == 0L) {
            return 0.0D;
        }

        double percent = (double) momentumPriceDeltaPoints * 100D / basePricePoints;
        return Math.round(percent * 10D) / 10D;
    }

    private String resolveMomentumPriceType(long momentumPriceDeltaPoints) {
        if (momentumPriceDeltaPoints > 0L) {
            return "PREMIUM";
        }

        if (momentumPriceDeltaPoints < 0L) {
            return "DISCOUNT";
        }

        return "NONE";
    }

    @Transactional
    public List<LeaderboardEntryResponse> getLeaderboard(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        getOrCreateWallet(season, authenticatedUser);
        List<GameSeasonTier> tiers = gameTierService.getOrCreateTiers(season);

        Map<String, TrendSignal> signalByVideoId = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));

        Map<Long, List<GamePosition>> openPositionsByUserId = gamePositionRepository.findBySeasonIdAndStatus(
            season.getId(),
            PositionStatus.OPEN
        ).stream().collect(Collectors.groupingBy(position -> position.getUser().getId()));
        List<GameWallet> wallets = gameWalletRepository.findBySeasonId(season.getId());
        wallets.forEach(wallet -> ensureHighlightStatesBackfilled(season, wallet.getUser().getId()));
        Map<Long, List<GameHighlightResponse>> highlightsByUserId = gameHighlightStateRepository
            .findBySeasonIdAndBestSettledHighlightScoreGreaterThan(season.getId(), 0L)
            .stream()
            .collect(Collectors.groupingBy(
                state -> state.getUser().getId(),
                Collectors.mapping(this::toStoredHighlightResponse, Collectors.toList())
            ));

        List<LeaderboardSnapshot> snapshots = wallets.stream()
            .map(wallet -> toLeaderboardSnapshot(
                wallet,
                openPositionsByUserId.getOrDefault(wallet.getUser().getId(), List.of()),
                highlightsByUserId.getOrDefault(wallet.getUser().getId(), List.of()),
                signalByVideoId,
                tiers
            ))
            .sorted(
                Comparator.comparingLong(LeaderboardSnapshot::highlightScore).reversed()
                    .thenComparing(Comparator.comparingInt(LeaderboardSnapshot::highlightCount).reversed())
                    .thenComparing(Comparator.comparingLong(LeaderboardSnapshot::totalAssetPoints).reversed())
                    .thenComparing(Comparator.comparingLong(LeaderboardSnapshot::realizedPnlPoints).reversed())
                    .thenComparing(LeaderboardSnapshot::displayName, String.CASE_INSENSITIVE_ORDER)
            )
            .toList();

        return toLeaderboardResponses(snapshots, authenticatedUser.id());
    }

    @Transactional
    public TierProgressResponse getCurrentTier(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        List<GameSeasonTier> tiers = gameTierService.getOrCreateTiers(season);
        long calculatedHighlightScore = calculateUserHighlightScore(season.getId(), authenticatedUser.id());
        long highlightScore = applyManualTierScoreAdjustment(wallet, calculatedHighlightScore);
        GameSeasonTier currentTier = gameTierService.resolveTier(tiers, highlightScore);
        GameSeasonTier nextTier = tiers.stream()
            .filter(tier -> tier.getMinScore() > highlightScore)
            .min(
                Comparator.comparingLong(GameSeasonTier::getMinScore)
                    .thenComparingInt(GameSeasonTier::getSortOrder)
            )
            .orElse(null);

        return new TierProgressResponse(
            season.getId(),
            season.getName(),
            season.getRegionCode(),
            highlightScore,
            calculatedHighlightScore,
            normalizeTierScoreAdjustment(wallet.getManualTierScoreAdjustment()),
            toTierResponse(currentTier),
            nextTier != null ? toTierResponse(nextTier) : null,
            tiers.stream().map(this::toTierResponse).toList()
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

    @Transactional(readOnly = true)
    public List<GameHighlightResponse> getLeaderboardHighlights(
        AuthenticatedUser authenticatedUser,
        Long userId,
        String regionCode
    ) {
        GameSeason season = requireActiveSeason(regionCode);
        getOrCreateWallet(season, authenticatedUser);

        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }

        return buildSettledHighlights(season, userId).stream()
            .sorted(
                Comparator
                    .comparingLong(GameService::calculateHighlightScore)
                    .reversed()
                    .thenComparing(GameHighlightResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            )
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
        long currentPricePoints = GamePointCalculator.calculateMomentumAdjustedPricePoints(
            signal.getCurrentRank(),
            signal.getRankChange()
        );
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
    public PositionRankHistoryResponse getLeaderboardPositionRankHistory(
        AuthenticatedUser authenticatedUser,
        Long userId,
        Long positionId,
        String regionCode
    ) {
        GameSeason season = requireActiveSeason(regionCode);
        getOrCreateWallet(season, authenticatedUser);

        if (userId == null) {
            throw new IllegalArgumentException("userId는 필수입니다.");
        }
        if (positionId == null) {
            throw new IllegalArgumentException("positionId는 필수입니다.");
        }

        GamePosition position = gamePositionRepository.findByIdAndUserId(positionId, userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        if (!position.getSeason().getId().equals(season.getId())) {
            throw new IllegalArgumentException("현재 시즌 포지션만 조회할 수 있습니다.");
        }

        return buildPositionRankHistoryResponse(position);
    }

    @Transactional(readOnly = true)
    public PositionRankHistoryResponse getPositionRankHistory(AuthenticatedUser authenticatedUser, Long positionId) {
        GamePosition position = gamePositionRepository.findByIdAndUserId(positionId, authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        return buildPositionRankHistoryResponse(position);
    }

    private PositionRankHistoryResponse buildPositionRankHistoryResponse(GamePosition position) {
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

    @Transactional(readOnly = true)
    public List<GameHighlightResponse> getHighlights(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        return buildHighlights(season, authenticatedUser.id());
    }

    @Transactional
    public List<GameNotificationResponse> getNotifications(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        return syncAndListNotifications(season, authenticatedUser.id());
    }

    @Transactional
    public void markNotificationsRead(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        gameNotificationService.markSeasonNotificationsRead(season.getId(), authenticatedUser.id());
    }

    @Transactional
    public void deleteNotifications(AuthenticatedUser authenticatedUser, String regionCode) {
        GameSeason season = requireActiveSeason(regionCode);
        gameNotificationService.deleteSeasonNotifications(season.getId(), authenticatedUser.id());
    }

    @Transactional
    public void deleteNotification(AuthenticatedUser authenticatedUser, Long notificationId) {
        if (notificationId == null) {
            throw new IllegalArgumentException("notificationId는 필수입니다.");
        }

        gameNotificationService.deleteNotification(notificationId, authenticatedUser.id());
    }

    private List<GameHighlightResponse> buildHighlights(GameSeason season, Long userId) {
        return buildSettledHighlights(season, userId);
    }

    private List<GameHighlightResponse> buildHighlightResponses(
        List<GamePosition> positions,
        Function<GamePosition, GameHighlightResponse> highlightMapper
    ) {
        return buildHighlightResponsesByRootId(positions, highlightMapper)
            .values()
            .stream()
            .toList();
    }

    private Map<Long, GameHighlightResponse> buildHighlightResponsesByRootId(
        List<GamePosition> positions,
        Function<GamePosition, GameHighlightResponse> highlightMapper
    ) {
        return positions.stream()
            .map(position -> new PositionHighlightScore(position, highlightMapper.apply(position)))
            .filter(positionHighlight -> positionHighlight.highlight() != null)
            .collect(Collectors.toMap(
                positionHighlight -> resolvePositionScoreRootId(positionHighlight.position()),
                PositionHighlightScore::highlight,
                this::selectRepresentativeHighlight,
                LinkedHashMap::new
            ));
    }

    private GameHighlightResponse selectRepresentativeHighlight(
        GameHighlightResponse left,
        GameHighlightResponse right
    ) {
        Comparator<GameHighlightResponse> comparator = Comparator
            .comparingLong(GameService::calculateHighlightScore)
            .thenComparing(GameHighlightResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(GameHighlightResponse::positionId, Comparator.nullsLast(Comparator.naturalOrder()));

        return comparator.compare(left, right) >= 0 ? left : right;
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
        List<GamePosition> sellablePositions = resolveSellablePositionsForUpdate(authenticatedUser, request, season, now);
        ensureSellableQuantity(sellablePositions, quantity);

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

    @Transactional
    public SellPreviewResponse previewSell(AuthenticatedUser authenticatedUser, SellPositionsRequest request) {
        GameSeason season = requireActiveSeason(request.regionCode());
        int quantity = normalizeQuantity(request.quantity());
        Instant now = Instant.now(clock);
        List<GamePosition> sellablePositions = resolveSellablePositions(authenticatedUser, request, season, now);
        ensureSellableQuantity(sellablePositions, quantity);
        SellSnapshot sellSnapshot = resolveSellSnapshot(sellablePositions.get(0));

        ensureHighlightStatesBackfilled(season, authenticatedUser.id());
        Map<Long, Long> bestScoreByRootPositionId = gameHighlightStateRepository
            .findBySeasonIdAndUserId(season.getId(), authenticatedUser.id())
            .stream()
            .collect(Collectors.toMap(GameHighlightState::getRootPositionId, GameHighlightState::getBestSettledHighlightScore));

        return buildSellPreviewResponse(sellablePositions, quantity, sellSnapshot, bestScoreByRootPositionId, now);
    }

    private CurrentSeasonResponse toCurrentSeasonResponse(
        GameSeason season,
        GameWallet wallet,
        List<GameNotificationResponse> notifications
    ) {
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
            toWalletResponse(wallet),
            notifications
        );
    }

    private List<GameNotificationResponse> syncAndListNotifications(GameSeason season, Long userId) {
        return gameNotificationService.syncAndListSeasonNotifications(
            season,
            userId,
            buildGeneratedNotifications(season, userId)
        );
    }

    private List<GameNotificationResponse> buildGeneratedNotifications(GameSeason season, Long userId) {
        return List.of();
    }

    private List<GameHighlightResponse> buildSettledHighlights(GameSeason season, Long userId) {
        ensureHighlightStatesBackfilled(season, userId);
        return gameHighlightStateRepository
            .findBySeasonIdAndUserIdAndBestSettledHighlightScoreGreaterThanOrderByBestSettledCreatedAtDesc(
                season.getId(),
                userId,
                0L
            ).stream()
            .map(this::toStoredHighlightResponse)
            .sorted(
                Comparator
                    .comparing(GameHighlightResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(GameHighlightResponse::positionId, Comparator.nullsLast(Comparator.reverseOrder()))
            )
            .toList();
    }

    private GameHighlightResponse toStoredHighlightResponse(GameHighlightState state) {
        return new GameHighlightResponse(
            state.getBestSettledPositionId() + "-" + state.getBestSettledHighlightType(),
            state.getBestSettledHighlightType(),
            state.getBestSettledTitle(),
            state.getBestSettledDescription(),
            state.getBestSettledPositionId(),
            state.getVideoId(),
            state.getVideoTitle(),
            state.getChannelTitle(),
            state.getThumbnailUrl(),
            state.getBuyRank(),
            state.getHighlightRank(),
            state.getSellRank(),
            state.getRankDiff(),
            state.getQuantity(),
            state.getStakePoints(),
            state.getCurrentPricePoints(),
            state.getProfitPoints(),
            state.getProfitRatePercent(),
            parseStoredStrategyTags(state.getStrategyTags()),
            state.getBestSettledHighlightScore(),
            PositionStatus.CLOSED.name(),
            state.getBestSettledCreatedAt()
        );
    }

    private List<GameStrategyType> parseStoredStrategyTags(String strategyTags) {
        if (!StringUtils.hasText(strategyTags)) {
            return List.of();
        }

        return java.util.Arrays.stream(strategyTags.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(GameStrategyType::valueOf)
            .toList();
    }

    private String serializeStrategyTags(List<GameStrategyType> strategyTags) {
        if (strategyTags == null || strategyTags.isEmpty()) {
            return null;
        }

        return strategyTags.stream()
            .map(Enum::name)
            .collect(Collectors.joining(","));
    }

    private void ensureHighlightStatesBackfilled(GameSeason season, Long userId) {
        ensureHighlightStatesBackfilled(
            season,
            userId,
            gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(season.getId(), userId)
        );
    }

    private void ensureHighlightStatesBackfilled(GameSeason season, Long userId, List<GamePosition> positions) {
        List<GamePosition> closedPositions = positions.stream()
            .filter(position -> position.getStatus() == PositionStatus.CLOSED)
            .toList();
        if (closedPositions.isEmpty()) {
            return;
        }

        Set<Long> existingRootIds = gameHighlightStateRepository.findBySeasonIdAndUserId(season.getId(), userId).stream()
            .map(GameHighlightState::getRootPositionId)
            .collect(Collectors.toCollection(HashSet::new));

        AppUser user = closedPositions.get(0).getUser();
        List<GameHighlightState> missingStates = buildHighlightResponsesByRootId(closedPositions, this::toSettledGameHighlightResponse)
            .entrySet()
            .stream()
            .filter(entry -> !existingRootIds.contains(entry.getKey()))
            .map(entry -> createStoredHighlightState(season, user, entry.getKey(), entry.getValue()))
            .toList();

        if (!missingStates.isEmpty()) {
            gameHighlightStateRepository.saveAll(missingStates);
        }
    }

    private GameHighlightState createStoredHighlightState(
        GameSeason season,
        AppUser user,
        Long rootPositionId,
        GameHighlightResponse highlight
    ) {
        Instant now = Instant.now(clock);
        GameHighlightState state = new GameHighlightState();
        state.setSeason(season);
        state.setUser(user);
        state.setRootPositionId(rootPositionId);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        applyStoredHighlight(state, highlight);
        return state;
    }

    private void applyStoredHighlight(GameHighlightState state, GameHighlightResponse highlight) {
        state.setBestSettledPositionId(highlight.positionId());
        state.setBestSettledHighlightType(highlight.highlightType());
        state.setBestSettledTitle(highlight.title());
        state.setBestSettledDescription(highlight.description());
        state.setVideoId(highlight.videoId());
        state.setVideoTitle(highlight.videoTitle());
        state.setChannelTitle(highlight.channelTitle());
        state.setThumbnailUrl(highlight.thumbnailUrl());
        state.setBuyRank(highlight.buyRank());
        state.setHighlightRank(highlight.highlightRank());
        state.setSellRank(highlight.sellRank());
        state.setRankDiff(highlight.rankDiff());
        state.setQuantity(highlight.quantity());
        state.setStakePoints(highlight.stakePoints());
        state.setCurrentPricePoints(highlight.currentPricePoints());
        state.setProfitPoints(highlight.profitPoints());
        state.setProfitRatePercent(highlight.profitRatePercent());
        state.setStrategyTags(serializeStrategyTags(highlight.strategyTags()));
        state.setBestSettledHighlightScore(calculateHighlightScore(highlight));
        state.setBestSettledCreatedAt(highlight.createdAt());
        state.setUpdatedAt(Instant.now(clock));
    }

    private WalletResponse toWalletResponse(GameWallet wallet) {
        return new WalletResponse(
            wallet.getSeason().getId(),
            wallet.getBalancePoints(),
            wallet.getReservedPoints(),
            wallet.getRealizedPnlPoints(),
            wallet.getBalancePoints() + wallet.getReservedPoints()
        );
    }

    private TierResponse toTierResponse(GameSeasonTier tier) {
        return new TierResponse(
            tier.getTierCode(),
            tier.getDisplayName(),
            tier.getMinScore(),
            tier.getBadgeCode(),
            tier.getTitleCode(),
            tier.getProfileThemeCode()
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
            List.of(GameStrategyType.SMALL_CASHOUT),
            List.of(GameStrategyType.SMALL_CASHOUT),
            List.of(),
            calculateProjectedPositionHighlightScore(
                position.getBuyRank(),
                position.getRankDiff(),
                GameStrategyResolver.calculateProfitRatePercent(position.getStakePoints(), position.getSettledPoints()),
                position.getPnlPoints(),
                List.of(GameStrategyType.SMALL_CASHOUT)
            ),
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
            new OpenPositionSnapshot(signal.getCurrentRank(), signal.getRankChange(), signal.getCapturedAt(), false)
        );
    }

    private PositionResponse toOpenPositionResponse(GamePosition position, OpenPositionSnapshot snapshot) {
        int rankDiff = position.getBuyRank() - snapshot.currentRank();
        long currentPricePoints = GamePointCalculator.calculatePositionPoints(
            resolveOpenPositionUnitPricePoints(snapshot),
            getPositionQuantity(position)
        );
        long profitPoints = GamePointCalculator.calculateProfitPoints(position.getStakePoints(), currentPricePoints);
        List<GameStrategyType> strategyTags = GameStrategyResolver.resolvePositionStrategyTags(
            position,
            snapshot.currentRank(),
            currentPricePoints,
            snapshot.chartOut(),
            Instant.now(clock)
        );
        List<GameStrategyType> achievedStrategyTags = GameStrategyResolver.resolveAchievedPositionStrategyTags(
            position,
            snapshot.currentRank(),
            currentPricePoints
        );
        List<GameStrategyType> targetStrategyTags = GameStrategyResolver.resolveTargetPositionStrategyTags(
            strategyTags,
            achievedStrategyTags
        );
        long projectedHighlightScore = calculateProjectedPositionHighlightScore(
            position.getBuyRank(),
            rankDiff,
            GameStrategyResolver.calculateProfitRatePercent(position.getStakePoints(), currentPricePoints),
            profitPoints,
            achievedStrategyTags
        );

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
            strategyTags,
            achievedStrategyTags,
            targetStrategyTags,
            projectedHighlightScore,
            snapshot.chartOut(),
            position.getStatus().name(),
            position.getBuyCapturedAt(),
            position.getCreatedAt(),
            position.getClosedAt()
        );
    }

    static long calculateProjectedPositionHighlightScore(
        Integer buyRank,
        Integer rankDiff,
        Double profitRatePercent,
        Long profitPoints,
        List<GameStrategyType> achievedStrategyTags
    ) {
        if (achievedStrategyTags == null || achievedStrategyTags.isEmpty()) {
            return 0L;
        }

        GameHighlightResponse projectedHighlight = new GameHighlightResponse(
            "projected",
            achievedStrategyTags.get(0).name(),
            "Projected highlight",
            "Projected highlight",
            0L,
            "projected",
            "Projected highlight",
            "Projected channel",
            "",
            buyRank != null ? buyRank : 0,
            buyRank != null && rankDiff != null ? buyRank - rankDiff : null,
            null,
            rankDiff,
            GamePointCalculator.QUANTITY_SCALE,
            0L,
            null,
            profitPoints,
            profitRatePercent,
            achievedStrategyTags,
            0L,
            PositionStatus.OPEN.name(),
            Instant.EPOCH
        );

        return calculateHighlightScore(projectedHighlight);
    }

    private GameHighlightResponse toGameHighlightResponse(GamePosition position) {
        HighlightSnapshot snapshot = resolveHighlightSnapshot(position);
        if (snapshot == null || snapshot.highlightRank() == null) {
            return null;
        }

        int rankDiff = position.getBuyRank() - snapshot.highlightRank();
        Double profitRatePercent = calculateProfitRatePercent(snapshot.profitPoints(), position.getStakePoints());
        List<GameStrategyType> strategyTags = GameStrategyResolver.resolveHighlightStrategyTags(
            position,
            snapshot.highlightRank(),
            profitRatePercent
        );
        HighlightDefinition definition = resolveHighlightDefinition(position, snapshot, rankDiff, profitRatePercent, strategyTags);
        if (definition == null) {
            return null;
        }

        GameHighlightResponse highlight = new GameHighlightResponse(
            position.getId() + "-" + definition.highlightType(),
            definition.highlightType(),
            definition.title(),
            definition.description(),
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            position.getBuyRank(),
            snapshot.highlightRank(),
            position.getSellRank(),
            rankDiff,
            getPositionQuantity(position),
            position.getStakePoints(),
            snapshot.currentPricePoints(),
            snapshot.profitPoints(),
            profitRatePercent,
            strategyTags,
            0L,
            position.getStatus().name(),
            snapshot.createdAt()
        );

        return new GameHighlightResponse(
            highlight.id(),
            highlight.highlightType(),
            highlight.title(),
            highlight.description(),
            highlight.positionId(),
            highlight.videoId(),
            highlight.videoTitle(),
            highlight.channelTitle(),
            highlight.thumbnailUrl(),
            highlight.buyRank(),
            highlight.highlightRank(),
            highlight.sellRank(),
            highlight.rankDiff(),
            highlight.quantity(),
            highlight.stakePoints(),
            highlight.currentPricePoints(),
            highlight.profitPoints(),
            highlight.profitRatePercent(),
            highlight.strategyTags(),
            calculateHighlightScore(highlight),
            highlight.status(),
            highlight.createdAt()
        );
    }

    private GameHighlightResponse toSettledGameHighlightResponse(GamePosition position) {
        if (position.getStatus() != PositionStatus.CLOSED) {
            return null;
        }

        return toGameHighlightResponse(position);
    }

    private HighlightSnapshot resolveHighlightSnapshot(GamePosition position) {
        if (position.getStatus() == PositionStatus.OPEN) {
            OpenPositionSnapshot snapshot = resolveOpenPositionSnapshot(position);
            if (snapshot == null || snapshot.chartOut()) {
                return null;
            }

            long currentPricePoints = GamePointCalculator.calculatePositionPoints(
                resolveOpenPositionUnitPricePoints(snapshot),
                getPositionQuantity(position)
            );
            return new HighlightSnapshot(
                snapshot.currentRank(),
                currentPricePoints,
                GamePointCalculator.calculateProfitPoints(position.getStakePoints(), currentPricePoints),
                snapshot.capturedAt()
            );
        }

        if (position.getSellRank() == null || position.getSettledPoints() == null || position.getPnlPoints() == null) {
            return null;
        }

        return new HighlightSnapshot(
            position.getSellRank(),
            position.getSettledPoints(),
            position.getPnlPoints(),
            position.getClosedAt() != null ? position.getClosedAt() : position.getSellCapturedAt()
        );
    }

    private HighlightDefinition resolveHighlightDefinition(
        GamePosition position,
        HighlightSnapshot snapshot,
        int rankDiff,
        Double profitRatePercent,
        List<GameStrategyType> strategyTags
    ) {
        if (strategyTags.contains(GameStrategyType.MOONSHOT)) {
            return new HighlightDefinition(
                "MOONSHOT",
                "문샷 적중",
                position.getBuyRank() + "위에서 잡은 영상이 " + snapshot.highlightRank() + "위까지 올라왔습니다."
            );
        }

        if (strategyTags.contains(GameStrategyType.BIG_CASHOUT)) {
            return new HighlightDefinition(
                "BIG_CASHOUT",
                "빅 캐시아웃",
                "수익률 " + profitRatePercent + "% 플레이가 기록됐습니다."
            );
        }

        if (strategyTags.contains(GameStrategyType.SMALL_CASHOUT)) {
            return new HighlightDefinition(
                "SMALL_CASHOUT",
                "스몰 캐시아웃",
                "수익률 " + profitRatePercent + "% 플레이가 기록됐습니다."
            );
        }

        if (strategyTags.contains(GameStrategyType.SNIPE)) {
            return new HighlightDefinition(
                "SNIPE",
                "스나이프 성공",
                position.getBuyRank() + "위에서 진입해 " + rankDiff + "계단을 앞질렀습니다."
            );
        }

        return null;
    }

    static long calculateHighlightScore(GameHighlightResponse highlight) {
        if (highlight.strategyTags() == null || highlight.strategyTags().isEmpty()) {
            return 0L;
        }

        return highlight.strategyTags().stream()
            .mapToLong(strategyType -> calculateStrategyHighlightScore(strategyType, highlight))
            .sum();
    }

    static long calculateStrategyHighlightScore(GameStrategyType strategyType, GameHighlightResponse highlight) {
        long baseScore = switch (strategyType) {
            case MOONSHOT -> MOONSHOT_HIGHLIGHT_BASE_SCORE;
            case BIG_CASHOUT -> BIG_CASHOUT_HIGHLIGHT_BASE_SCORE;
            case SMALL_CASHOUT -> SMALL_CASHOUT_HIGHLIGHT_BASE_SCORE;
            case SNIPE -> SNIPE_HIGHLIGHT_BASE_SCORE;
        };
        long rankDiffBonus = Math.max(0, highlight.rankDiff() != null ? highlight.rankDiff() : 0)
            * RANK_DIFF_HIGHLIGHT_SCORE_MULTIPLIER;
        long profitRateBonus = highlight.profitRatePercent() != null
            ? Math.min(
                MAX_PROFIT_RATE_HIGHLIGHT_BONUS,
                Math.max(0L, Math.round(highlight.profitRatePercent() * PROFIT_RATE_HIGHLIGHT_SCORE_MULTIPLIER))
            )
            : 0L;
        long profitPointsBonus = calculateProfitPointsHighlightBonus(highlight.profitPoints());

        return baseScore + rankDiffBonus + profitRateBonus + profitPointsBonus;
    }

    static long calculateProfitPointsHighlightBonus(Long profitPoints) {
        if (profitPoints == null || profitPoints < MIN_PROFIT_POINTS_HIGHLIGHT_BONUS) {
            return 0L;
        }

        double normalizedProfitPoints = Math.max(0D, profitPoints - MIN_PROFIT_POINTS_HIGHLIGHT_BONUS);

        return Math.min(
            MAX_PROFIT_POINTS_HIGHLIGHT_BONUS,
            Math.max(0L, Math.round(Math.sqrt(normalizedProfitPoints) * PROFIT_POINTS_HIGHLIGHT_SQRT_SCALE))
        );
    }

    @Transactional(readOnly = true)
    public long calculateSettledUserHighlightScore(Long seasonId, Long userId) {
        return calculateUserHighlightScore(seasonId, userId);
    }

    @Transactional(readOnly = true)
    public List<GameHighlightResponse> getSettledUserHighlights(Long seasonId, Long userId) {
        GameSeason season = gameSeasonRepository.findById(seasonId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 시즌입니다."));

        return buildSettledHighlights(season, userId).stream()
            .sorted(
                Comparator
                    .comparingLong(GameService::calculateHighlightScore)
                    .reversed()
                    .thenComparing(GameHighlightResponse::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
            )
            .toList();
    }

    private long calculateUserHighlightScore(Long seasonId, Long userId) {
        List<GamePosition> positions = gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(seasonId, userId);
        if (!positions.isEmpty()) {
            ensureHighlightStatesBackfilled(positions.get(0).getSeason(), userId, positions);
        }

        return gameHighlightStateRepository
            .findBySeasonIdAndUserIdAndBestSettledHighlightScoreGreaterThanOrderByBestSettledCreatedAtDesc(
                seasonId,
                userId,
                0L
            ).stream()
            .mapToLong(GameHighlightState::getBestSettledHighlightScore)
            .sum();
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
            GamePointCalculator.ORDER_QUANTITY_STEP
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
        List<GameHighlightResponse> highlights,
        Map<String, TrendSignal> signalByVideoId,
        List<GameSeasonTier> tiers
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
                    GamePointCalculator.calculateMomentumAdjustedPricePoints(signal.getCurrentRank(), signal.getRankChange()),
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
        GameHighlightResponse topHighlight = highlights.stream()
            .max(Comparator.comparingLong(GameService::calculateHighlightScore))
            .orElse(null);
        long calculatedHighlightScore = highlights.stream().mapToLong(GameService::calculateHighlightScore).sum();
        long highlightScore = applyManualTierScoreAdjustment(wallet, calculatedHighlightScore);

        return new LeaderboardSnapshot(
            wallet.getUser().getId(),
            wallet.getUser().getDisplayName(),
            wallet.getUser().getPictureUrl(),
            toTierResponse(gameTierService.resolveTier(tiers, highlightScore)),
            highlightScore,
            highlights.size(),
            topHighlight != null ? topHighlight.highlightType() : null,
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
                    snapshot.highlightScore(),
                    snapshot.highlightCount(),
                    snapshot.topHighlightType(),
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
        wallet.setManualTierScoreAdjustment(0L);
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
            return new SellSnapshot(
                signal.getCurrentRunId(),
                signal.getCurrentRank(),
                signal.getRankChange(),
                signal.getCapturedAt(),
                false
            );
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
            return new SellSnapshot(
                latestTrendRun.run().getId(),
                latestSnapshot.getRank(),
                null,
                latestTrendRun.run().getCapturedAt(),
                false
            );
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
        return new SellSnapshot(latestTrendRun.run().getId(), fallbackRank, null, latestTrendRun.run().getCapturedAt(), true);
    }

    private OpenPositionSnapshot resolveOpenPositionSnapshot(GamePosition position) {
        TrendSignalId signalId = new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId());
        TrendSignal signal = trendSignalRepository.findById(signalId).orElse(null);
        if (signal != null) {
            return new OpenPositionSnapshot(signal.getCurrentRank(), signal.getRankChange(), signal.getCapturedAt(), false);
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
            return new OpenPositionSnapshot(latestSnapshot.getRank(), null, latestTrendRun.run().getCapturedAt(), false);
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
        return new OpenPositionSnapshot(fallbackRank, null, latestTrendRun.run().getCapturedAt(), true);
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
        ensureHighlightStatesBackfilled(position.getSeason(), position.getUser().getId());
        long previousHighlightScore = calculateUserHighlightScore(position.getSeason().getId(), position.getUser().getId());
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
        long highlightScore = resolveSettledHighlightScore(settledPosition);
        publishHighScoreHighlightNotification(settledPosition);
        publishTierPromotionIfNeeded(settledPosition, previousHighlightScore);

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
            highlightScore,
            wallet.getBalancePoints(),
            settledPosition.getClosedAt()
        );
    }

    private long resolveSettledHighlightScore(GamePosition position) {
        GameHighlightResponse highlight = toSettledGameHighlightResponse(position);
        return highlight != null ? highlight.highlightScore() : 0L;
    }

    private SellPreviewResponse buildSellPreviewResponse(
        List<GamePosition> sellablePositions,
        int quantity,
        SellSnapshot sellSnapshot,
        Map<Long, Long> bestScoreByRootPositionId,
        Instant now
    ) {
        List<SellPreviewItemResponse> items = new ArrayList<>();
        long totalStakePoints = 0L;
        long totalSellPricePoints = 0L;
        long totalPnlPoints = 0L;
        long totalSettledPoints = 0L;
        long totalProjectedHighlightScore = 0L;
        long totalAppliedHighlightScoreDelta = 0L;
        int recordEligibleCount = 0;
        int remainingQuantity = quantity;

        for (GamePosition position : sellablePositions) {
            if (remainingQuantity <= 0) {
                break;
            }

            int sellQuantity = Math.min(remainingQuantity, getPositionQuantity(position));
            int rankDiff = position.getBuyRank() - sellSnapshot.rank();
            long unitStakePoints = resolveUnitStakePoints(position);
            long soldStakePoints = GamePointCalculator.calculatePositionPoints(unitStakePoints, sellQuantity);
            long sellPricePoints = GamePointCalculator.calculatePositionPoints(
                resolveSellUnitPricePoints(sellSnapshot),
                sellQuantity
            );
            long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);
            long pnlPoints = GamePointCalculator.calculateProfitPoints(soldStakePoints, settledPoints);
            GamePosition previewPosition = createClosedPosition(
                position,
                sellQuantity,
                soldStakePoints,
                sellSnapshot,
                rankDiff,
                pnlPoints,
                settledPoints,
                now
            );
            long projectedHighlightScore = resolveSettledHighlightScore(previewPosition);
            long bestHighlightScore = bestScoreByRootPositionId.getOrDefault(resolvePositionScoreRootId(position), 0L);
            long appliedHighlightScoreDelta = Math.max(0L, projectedHighlightScore - bestHighlightScore);
            boolean willUpdateRecord = appliedHighlightScoreDelta > 0L;

            items.add(new SellPreviewItemResponse(
                position.getId(),
                position.getBuyRank(),
                sellQuantity,
                soldStakePoints,
                sellPricePoints,
                pnlPoints,
                settledPoints,
                projectedHighlightScore,
                bestHighlightScore,
                appliedHighlightScoreDelta,
                willUpdateRecord
            ));

            totalStakePoints += soldStakePoints;
            totalSellPricePoints += sellPricePoints;
            totalPnlPoints += pnlPoints;
            totalSettledPoints += settledPoints;
            totalProjectedHighlightScore += projectedHighlightScore;
            totalAppliedHighlightScoreDelta += appliedHighlightScoreDelta;
            if (willUpdateRecord) {
                recordEligibleCount += 1;
            }
            remainingQuantity -= sellQuantity;
        }

        return new SellPreviewResponse(
            quantity,
            sellSnapshot.rank(),
            totalStakePoints,
            totalSellPricePoints,
            totalPnlPoints,
            totalSettledPoints,
            totalProjectedHighlightScore,
            totalAppliedHighlightScoreDelta,
            recordEligibleCount,
            items
        );
    }

    private void publishHighScoreHighlightNotification(GamePosition settledPosition) {
        GameHighlightResponse currentHighlight = toSettledGameHighlightResponse(settledPosition);
        if (currentHighlight == null) {
            return;
        }

        GameHighlightState state = findOrCreateHighlightStateForUpdate(
            settledPosition.getSeason(),
            settledPosition.getUser(),
            resolvePositionScoreRootId(settledPosition)
        );
        long previousBestScore = state.getBestSettledHighlightScore();
        long currentScore = calculateHighlightScore(currentHighlight);
        if (currentScore <= previousBestScore) {
            return;
        }

        applyStoredHighlight(state, currentHighlight);
        gameHighlightStateRepository.save(state);

        gameNotificationService.createAndPush(
            settledPosition.getUser(),
            settledPosition.getSeason(),
            GameNotificationFactory.fromHighlight(currentHighlight)
        );
    }

    @Transactional
    public void publishProjectedHighlightNotifications(String regionCode) {
        GameSeason season = gameSeasonRepository.findTopByStatusAndRegionCodeOrderByStartAtDesc(
            SeasonStatus.ACTIVE,
            regionCode
        ).orElse(null);
        if (season == null) {
            return;
        }

        Map<String, TrendSignal> signalsByVideoId = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));

        gamePositionRepository.findBySeasonIdAndStatus(season.getId(), PositionStatus.OPEN).forEach(position ->
            publishProjectedHighlightNotification(position, signalsByVideoId)
        );
    }

    private void publishProjectedHighlightNotification(
        GamePosition position,
        Map<String, TrendSignal> signalsByVideoId
    ) {
        TrendSignal signal = signalsByVideoId.get(position.getVideoId());
        OpenPositionSnapshot snapshot = signal != null
            ? new OpenPositionSnapshot(signal.getCurrentRank(), signal.getRankChange(), signal.getCapturedAt(), false)
            : resolveOpenPositionSnapshot(position);
        if (snapshot == null || snapshot.chartOut()) {
            return;
        }

        long currentPricePoints = GamePointCalculator.calculatePositionPoints(
            resolveOpenPositionUnitPricePoints(snapshot),
            getPositionQuantity(position)
        );
        long projectedScore = calculateProjectedPositionHighlightScore(
            position.getBuyRank(),
            position.getBuyRank() - snapshot.currentRank(),
            GameStrategyResolver.calculateProfitRatePercent(position.getStakePoints(), currentPricePoints),
            GamePointCalculator.calculateProfitPoints(position.getStakePoints(), currentPricePoints),
            GameStrategyResolver.resolveAchievedPositionStrategyTags(position, snapshot.currentRank(), currentPricePoints)
        );
        if (projectedScore <= 0L) {
            return;
        }

        ensureHighlightStatesBackfilled(position.getSeason(), position.getUser().getId());
        GameHighlightState state = findOrCreateHighlightStateForUpdate(
            position.getSeason(),
            position.getUser(),
            resolvePositionScoreRootId(position)
        );
        if (projectedScore <= state.getBestSettledHighlightScore()
            || projectedScore <= state.getBestProjectedNotificationScore()) {
            return;
        }

        state.setBestProjectedNotificationScore(projectedScore);
        state.setUpdatedAt(Instant.now(clock));
        gameHighlightStateRepository.save(state);

        gameNotificationService.createAndPushPositionSnapshot(
            position,
            snapshot.currentRank(),
            currentPricePoints,
            snapshot.capturedAt()
        );
    }

    private GameHighlightState findOrCreateHighlightStateForUpdate(GameSeason season, AppUser user, Long rootPositionId) {
        return gameHighlightStateRepository.findBySeasonIdAndUserIdAndRootPositionIdForUpdate(
                season.getId(),
                user.getId(),
                rootPositionId
            )
            .orElseGet(() -> createHighlightStateForUpdate(season, user, rootPositionId));
    }

    private GameHighlightState createHighlightStateForUpdate(GameSeason season, AppUser user, Long rootPositionId) {
        Instant now = Instant.now(clock);
        GameHighlightState state = new GameHighlightState();
        state.setSeason(season);
        state.setUser(user);
        state.setRootPositionId(rootPositionId);
        state.setCreatedAt(now);
        state.setUpdatedAt(now);
        try {
            return gameHighlightStateRepository.save(state);
        } catch (DataIntegrityViolationException exception) {
            return gameHighlightStateRepository.findBySeasonIdAndUserIdAndRootPositionIdForUpdate(
                season.getId(),
                user.getId(),
                rootPositionId
            ).orElseThrow(() -> exception);
        }
    }

    private void publishTierPromotionIfNeeded(GamePosition settledPosition, long previousHighlightScore) {
        GameHighlightResponse highlight = toSettledGameHighlightResponse(settledPosition);
        if (highlight == null) {
            return;
        }

        long currentHighlightScore = calculateUserHighlightScore(
            settledPosition.getSeason().getId(),
            settledPosition.getUser().getId()
        );
        List<GameSeasonTier> tiers = gameTierService.getOrCreateTiers(settledPosition.getSeason());
        if (tiers == null || tiers.isEmpty()) {
            return;
        }

        GameSeasonTier previousTier = gameTierService.resolveTier(tiers, previousHighlightScore);
        GameSeasonTier currentTier = gameTierService.resolveTier(tiers, currentHighlightScore);
        if (previousTier == null || currentTier == null || currentTier.getSortOrder() <= previousTier.getSortOrder()) {
            return;
        }

        String displayName = StringUtils.hasText(settledPosition.getUser().getDisplayName())
            ? settledPosition.getUser().getDisplayName().trim()
            : "익명";
        commentService.publishTierSystemMessage(
            displayName + "님이 " + currentTier.getDisplayName() + " 티어로 상승했습니다."
        );
        gameNotificationService.createAndPush(
            settledPosition.getUser(),
            settledPosition.getSeason(),
            GameNotificationFactory.fromTierPromotion(
                settledPosition,
                currentTier,
                currentHighlightScore,
                highlight.createdAt()
            )
        );
    }

    private String formatQuantity(int quantity) {
        int wholeQuantity = quantity / GamePointCalculator.QUANTITY_SCALE;
        return Integer.toString(wholeQuantity);
    }

    private void ensureSellableQuantity(List<GamePosition> sellablePositions, int quantity) {
        int totalSellableQuantity = sellablePositions.stream()
            .mapToInt(this::getPositionQuantity)
            .sum();
        if (totalSellableQuantity < quantity) {
            throw new IllegalArgumentException("지금 매도 가능한 포지션 수가 부족합니다.");
        }
    }

    private List<GamePosition> resolveSellablePositions(
        AuthenticatedUser authenticatedUser,
        SellPositionsRequest request,
        GameSeason season,
        Instant now
    ) {
        if (request.positionId() != null) {
            GamePosition position = gamePositionRepository.findByIdAndUserId(request.positionId(), authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));
            ensurePositionOpen(position);
            if (!position.getSeason().getId().equals(season.getId())) {
                throw new IllegalArgumentException("현재 시즌 포지션만 매도할 수 있습니다.");
            }
            return canSellPosition(position, now) ? List.of(position) : List.of();
        }

        String videoId = normalizeRequired(request.videoId(), "videoId는 필수입니다.");
        return gamePositionRepository
            .findBySeasonIdAndUserIdAndVideoIdAndStatusOrderByCreatedAtAsc(
                season.getId(),
                authenticatedUser.id(),
                videoId,
                PositionStatus.OPEN
            )
            .stream()
            .filter(position -> canSellPosition(position, now))
            .toList();
    }

    private List<GamePosition> resolveSellablePositionsForUpdate(
        AuthenticatedUser authenticatedUser,
        SellPositionsRequest request,
        GameSeason season,
        Instant now
    ) {
        if (request.positionId() != null) {
            GamePosition position = gamePositionRepository.findByIdAndUserIdForUpdate(request.positionId(), authenticatedUser.id())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));
            ensurePositionOpen(position);
            if (!position.getSeason().getId().equals(season.getId())) {
                throw new IllegalArgumentException("현재 시즌 포지션만 매도할 수 있습니다.");
            }
            return canSellPosition(position, now) ? List.of(position) : List.of();
        }

        String videoId = normalizeRequired(request.videoId(), "videoId는 필수입니다.");
        return gamePositionRepository
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

        if (quantity < GamePointCalculator.ORDER_QUANTITY_STEP) {
            throw new IllegalArgumentException("quantity는 1개 이상이어야 합니다.");
        }

        if (quantity % GamePointCalculator.ORDER_QUANTITY_STEP != 0) {
            throw new IllegalArgumentException("quantity는 1개 단위로만 주문할 수 있습니다.");
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
            : GamePointCalculator.calculateMomentumAdjustedPricePoints(snapshot.currentRank(), snapshot.rankChange());
    }

    private long resolveSellUnitPricePoints(SellSnapshot sellSnapshot) {
        return sellSnapshot.chartOut()
            ? GamePointCalculator.calculateChartOutUnitPricePoints()
            : GamePointCalculator.calculateMomentumAdjustedPricePoints(sellSnapshot.rank(), sellSnapshot.rankChange());
    }

    private Double calculateProfitRatePercent(long profitPoints, long stakePoints) {
        if (stakePoints <= 0L) {
            return null;
        }

        return Math.round((double) profitPoints * 1000D / stakePoints) / 10D;
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
        position.setOriginPositionId(resolvePositionScoreRootId(source));
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

    private Long resolvePositionScoreRootId(GamePosition position) {
        if (position.getOriginPositionId() != null) {
            return position.getOriginPositionId();
        }

        return position.getId();
    }

    private record PositionHighlightScore(GamePosition position, GameHighlightResponse highlight) {
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
        TierResponse currentTier,
        Long highlightScore,
        Integer highlightCount,
        String topHighlightType,
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

    private record SellSnapshot(Long runId, int rank, Integer rankChange, Instant capturedAt, boolean chartOut) {
    }

    private record OpenPositionSnapshot(int currentRank, Integer rankChange, Instant capturedAt, boolean chartOut) {
    }

    private record HighlightSnapshot(Integer highlightRank, long currentPricePoints, long profitPoints, Instant createdAt) {
    }

    private record HighlightDefinition(String highlightType, String title, String description) {
    }

    private record LatestTrendRun(TrendRun run, List<TrendSnapshot> snapshots) {
    }

    private record PreviousSnapshotContext(boolean hasPreviousRun, Map<String, TrendSnapshot> snapshotsByVideoId) {
    }

    private record PositionHistoryWindow(Long startRunId, Long endRunId, Integer latestRank, Instant latestCapturedAt) {
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

    private long applyManualTierScoreAdjustment(GameWallet wallet, long calculatedHighlightScore) {
        return calculatedHighlightScore + normalizeTierScoreAdjustment(wallet.getManualTierScoreAdjustment());
    }

    private long normalizeTierScoreAdjustment(Long value) {
        return value != null ? value : 0L;
    }
}
