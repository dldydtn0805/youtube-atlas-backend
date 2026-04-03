package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.game.api.CreatePositionRequest;
import com.yongsoo.youtubeatlasbackend.game.api.CurrentSeasonResponse;
import com.yongsoo.youtubeatlasbackend.game.api.LeaderboardEntryResponse;
import com.yongsoo.youtubeatlasbackend.game.api.MarketVideoResponse;
import com.yongsoo.youtubeatlasbackend.game.api.PositionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SellPositionResponse;
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

    private final GameSeasonRepository gameSeasonRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final AppUserRepository appUserRepository;
    private final TrendSignalRepository trendSignalRepository;
    private final TrendRunRepository trendRunRepository;
    private final TrendSnapshotRepository trendSnapshotRepository;
    private final Clock clock;

    public GameService(
        GameSeasonRepository gameSeasonRepository,
        GameWalletRepository gameWalletRepository,
        GamePositionRepository gamePositionRepository,
        GameLedgerRepository gameLedgerRepository,
        AppUserRepository appUserRepository,
        TrendSignalRepository trendSignalRepository,
        TrendRunRepository trendRunRepository,
        TrendSnapshotRepository trendSnapshotRepository,
        Clock clock
    ) {
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.appUserRepository = appUserRepository;
        this.trendSignalRepository = trendSignalRepository;
        this.trendRunRepository = trendRunRepository;
        this.trendSnapshotRepository = trendSnapshotRepository;
        this.clock = clock;
    }

    @Transactional
    public CurrentSeasonResponse getCurrentSeason(AuthenticatedUser authenticatedUser) {
        GameSeason season = requireActiveSeason();
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        return toCurrentSeasonResponse(season, wallet);
    }

    @Transactional
    public WalletResponse getWallet(AuthenticatedUser authenticatedUser) {
        GameSeason season = requireActiveSeason();
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        return toWalletResponse(wallet);
    }

    @Transactional
    public List<MarketVideoResponse> getMarket(AuthenticatedUser authenticatedUser) {
        GameSeason season = requireActiveSeason();
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        long openPositionCount = gamePositionRepository.countBySeasonIdAndUserIdAndStatus(
            season.getId(),
            authenticatedUser.id(),
            PositionStatus.OPEN
        );
        boolean maxOpenReached = openPositionCount >= season.getMaxOpenPositions();

        return trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().map(signal -> {
            long currentPricePoints = GamePointCalculator.calculatePricePoints(signal.getCurrentRank());
            boolean alreadyOwned = gamePositionRepository.existsBySeasonIdAndUserIdAndVideoIdAndStatus(
                season.getId(),
                authenticatedUser.id(),
                signal.getId().getVideoId(),
                PositionStatus.OPEN
            );
            String blockedReason = resolveBuyBlockedReason(wallet, currentPricePoints, alreadyOwned, maxOpenReached);

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
    public List<LeaderboardEntryResponse> getLeaderboard(AuthenticatedUser authenticatedUser) {
        GameSeason season = requireActiveSeason();
        getOrCreateWallet(season, authenticatedUser);

        Map<String, TrendSignal> signalByVideoId = trendSignalRepository.findByIdRegionCodeAndIdCategoryIdOrderByCurrentRankAsc(
            season.getRegionCode(),
            TRENDING_CATEGORY_ID
        ).stream().collect(Collectors.toMap(signal -> signal.getId().getVideoId(), Function.identity()));

        Map<Long, List<GamePosition>> openPositionsByUserId = gamePositionRepository.findBySeasonIdAndStatus(
            season.getId(),
            PositionStatus.OPEN
        ).stream().collect(Collectors.groupingBy(position -> position.getUser().getId()));

        List<LeaderboardSnapshot> snapshots = gameWalletRepository.findBySeasonId(season.getId()).stream()
            .map(wallet -> toLeaderboardSnapshot(wallet, openPositionsByUserId.getOrDefault(wallet.getUser().getId(), List.of()), signalByVideoId))
            .sorted(
                Comparator.comparingLong(LeaderboardSnapshot::totalAssetPoints).reversed()
                    .thenComparing(Comparator.comparingLong(LeaderboardSnapshot::realizedPnlPoints).reversed())
                    .thenComparing(LeaderboardSnapshot::displayName, String.CASE_INSENSITIVE_ORDER)
            )
            .toList();

        return toLeaderboardResponses(snapshots, authenticatedUser.id());
    }

    @Transactional
    public PositionResponse buy(AuthenticatedUser authenticatedUser, CreatePositionRequest request) {
        GameSeason season = requireActiveSeason();
        GameWallet wallet = getOrCreateWallet(season, authenticatedUser);
        String regionCode = normalizeRequired(request.regionCode(), "regionCode는 필수입니다.").toUpperCase();
        String categoryId = normalizeRequired(request.categoryId(), "categoryId는 필수입니다.");
        String videoId = normalizeRequired(request.videoId(), "videoId는 필수입니다.");
        long quotedPricePoints = normalizeStakePoints(request.stakePoints());
        Instant now = Instant.now(clock);

        if (!season.getRegionCode().equalsIgnoreCase(regionCode)) {
            throw new IllegalArgumentException("현재 시즌에서 지원하지 않는 regionCode입니다.");
        }

        long openPositionCount = gamePositionRepository.countBySeasonIdAndUserIdAndStatus(
            season.getId(),
            authenticatedUser.id(),
            PositionStatus.OPEN
        );
        if (openPositionCount >= season.getMaxOpenPositions()) {
            throw new IllegalArgumentException("동시 보유 가능 포지션 수를 초과했습니다.");
        }

        if (gamePositionRepository.existsBySeasonIdAndUserIdAndVideoIdAndStatus(
            season.getId(),
            authenticatedUser.id(),
            videoId,
            PositionStatus.OPEN
        )) {
            throw new IllegalArgumentException("이미 보유 중인 영상입니다.");
        }

        TrendSignal signal = requireTrendSignal(regionCode, categoryId, videoId);
        long currentPricePoints = GamePointCalculator.calculatePricePoints(signal.getCurrentRank());
        if (quotedPricePoints != currentPricePoints) {
            throw new IllegalArgumentException("현재 가격이 변경되었습니다. 최신 시세로 다시 시도해 주세요.");
        }

        if (wallet.getBalancePoints() < currentPricePoints) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
        }

        AppUser user = requireUser(authenticatedUser.id());

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
        position.setStakePoints(currentPricePoints);
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(now);
        GamePosition savedPosition = gamePositionRepository.save(position);

        wallet.setBalancePoints(wallet.getBalancePoints() - currentPricePoints);
        wallet.setReservedPoints(wallet.getReservedPoints() + currentPricePoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        saveLedger(season, user, savedPosition, LedgerType.BUY_LOCK, -currentPricePoints, wallet.getBalancePoints(), now);

        return toOpenPositionResponse(savedPosition, signal);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getMyPositions(AuthenticatedUser authenticatedUser, String status) {
        GameSeason season = requireActiveSeason();
        List<GamePosition> positions = StringUtils.hasText(status)
            ? gamePositionRepository.findBySeasonIdAndUserIdAndStatusOrderByCreatedAtDesc(
                season.getId(),
                authenticatedUser.id(),
                parsePositionStatus(status)
            )
            : gamePositionRepository.findBySeasonIdAndUserIdOrderByCreatedAtDesc(season.getId(), authenticatedUser.id());

        return positions.stream()
            .map(this::toPositionResponse)
            .toList();
    }

    @Transactional
    public SellPositionResponse sell(AuthenticatedUser authenticatedUser, Long positionId) {
        GamePosition position = gamePositionRepository.findByIdAndUserId(positionId, authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 포지션입니다."));

        if (position.getStatus() != PositionStatus.OPEN) {
            throw new IllegalArgumentException("이미 종료된 포지션입니다.");
        }

        Instant now = Instant.now(clock);
        long heldSeconds = now.getEpochSecond() - position.getCreatedAt().getEpochSecond();
        if (heldSeconds < position.getSeason().getMinHoldSeconds()) {
            throw new IllegalArgumentException("최소 보유 시간이 지나야 매도할 수 있습니다.");
        }

        SellSnapshot sellSnapshot = resolveSellSnapshot(position);
        GameWallet wallet = gameWalletRepository.findBySeasonIdAndUserId(position.getSeason().getId(), authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
        int rankDiff = position.getBuyRank() - sellSnapshot.rank();
        long sellPricePoints = GamePointCalculator.calculatePricePoints(sellSnapshot.rank());
        long pnlPoints = GamePointCalculator.calculateProfitPoints(position.getStakePoints(), sellPricePoints);
        long settledPoints = GamePointCalculator.calculateSettledPoints(sellPricePoints);

        position.setSellRunId(sellSnapshot.runId());
        position.setSellRank(sellSnapshot.rank());
        position.setSellCapturedAt(sellSnapshot.capturedAt());
        position.setRankDiff(rankDiff);
        position.setPnlPoints(pnlPoints);
        position.setSettledPoints(settledPoints);
        position.setStatus(PositionStatus.CLOSED);
        position.setClosedAt(now);
        gamePositionRepository.save(position);

        wallet.setReservedPoints(wallet.getReservedPoints() - position.getStakePoints());
        wallet.setBalancePoints(wallet.getBalancePoints() + settledPoints);
        wallet.setRealizedPnlPoints(wallet.getRealizedPnlPoints() + pnlPoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        saveLedger(
            position.getSeason(),
            position.getUser(),
            position,
            LedgerType.SELL_SETTLE,
            settledPoints,
            wallet.getBalancePoints(),
            now
        );

        return new SellPositionResponse(
            position.getId(),
            position.getVideoId(),
            position.getBuyRank(),
            position.getSellRank(),
            position.getRankDiff(),
            position.getStakePoints(),
            settledPoints,
            position.getPnlPoints(),
            position.getSettledPoints(),
            wallet.getBalancePoints(),
            position.getClosedAt()
        );
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
            wallet.getBalancePoints() + wallet.getReservedPoints()
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
        long currentPricePoints = GamePointCalculator.calculatePricePoints(snapshot.currentRank());
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

    private GameSeason requireActiveSeason() {
        return gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("활성화된 게임 시즌이 없습니다."));
    }

    private String resolveBuyBlockedReason(GameWallet wallet, long currentPricePoints, boolean alreadyOwned, boolean maxOpenReached) {
        if (alreadyOwned) {
            return "이미 보유 중인 영상입니다.";
        }
        if (maxOpenReached) {
            return "동시 보유 가능 포지션 수를 초과했습니다.";
        }
        if (wallet.getBalancePoints() < currentPricePoints) {
            return "현재 가격 기준 보유 포인트가 부족합니다.";
        }
        return null;
    }

    private GameWallet getOrCreateWallet(GameSeason season, AuthenticatedUser authenticatedUser) {
        return gameWalletRepository.findBySeasonIdAndUserId(season.getId(), authenticatedUser.id())
            .orElseGet(() -> createWallet(season, authenticatedUser.id()));
    }

    private LeaderboardSnapshot toLeaderboardSnapshot(
        GameWallet wallet,
        List<GamePosition> openPositions,
        Map<String, TrendSignal> signalByVideoId
    ) {
        long markedOpenPositionPoints = 0L;
        long unrealizedPnlPoints = 0L;

        for (GamePosition position : openPositions) {
            TrendSignal signal = signalByVideoId.get(position.getVideoId());
            long markedValue = position.getStakePoints();

            if (signal != null) {
                markedValue = GamePointCalculator.calculatePricePoints(signal.getCurrentRank());
            } else {
                OpenPositionSnapshot snapshot = resolveOpenPositionSnapshot(position);
                if (snapshot != null) {
                    markedValue = GamePointCalculator.calculatePricePoints(snapshot.currentRank());
                }
            }

            markedOpenPositionPoints += markedValue;
            unrealizedPnlPoints += markedValue - position.getStakePoints();
        }

        return new LeaderboardSnapshot(
            wallet.getUser().getId(),
            wallet.getUser().getDisplayName(),
            wallet.getUser().getPictureUrl(),
            wallet.getBalancePoints() + markedOpenPositionPoints,
            wallet.getBalancePoints(),
            wallet.getReservedPoints(),
            wallet.getRealizedPnlPoints(),
            unrealizedPnlPoints,
            openPositions.size()
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
                    snapshot.totalAssetPoints(),
                    snapshot.balancePoints(),
                    snapshot.reservedPoints(),
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
        wallet.setUpdatedAt(now);
        GameWallet savedWallet = gameWalletRepository.save(wallet);

        saveLedger(season, user, null, LedgerType.SEED, season.getStartingBalancePoints(), savedWallet.getBalancePoints(), now);
        return savedWallet;
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
            return new SellSnapshot(signal.getCurrentRunId(), signal.getCurrentRank(), signal.getCapturedAt());
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
            return new SellSnapshot(latestTrendRun.run().getId(), latestSnapshot.getRank(), latestTrendRun.run().getCapturedAt());
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
        return new SellSnapshot(latestTrendRun.run().getId(), fallbackRank, latestTrendRun.run().getCapturedAt());
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

    private void ensureLatestRunLooksHealthy(String regionCode, String categoryId, Long latestRunId, int latestSnapshotCount) {
        if (!latestRunLooksHealthy(regionCode, categoryId, latestRunId, latestSnapshotCount)) {
            throw manualSellUnavailable();
        }
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
        GameLedger ledger = new GameLedger();
        ledger.setSeason(season);
        ledger.setUser(user);
        ledger.setPosition(position);
        ledger.setType(type);
        ledger.setAmountPoints(amountPoints);
        ledger.setBalanceAfterPoints(balanceAfterPoints);
        ledger.setCreatedAt(createdAt);
        gameLedgerRepository.save(ledger);
    }

    private record LeaderboardSnapshot(
        Long userId,
        String displayName,
        String pictureUrl,
        Long totalAssetPoints,
        Long balancePoints,
        Long reservedPoints,
        Long realizedPnlPoints,
        Long unrealizedPnlPoints,
        Integer openPositionCount
    ) {
    }

    private record SellSnapshot(Long runId, int rank, Instant capturedAt) {
    }

    private record OpenPositionSnapshot(int currentRank, Instant capturedAt, boolean chartOut) {
    }

    private record LatestTrendRun(TrendRun run, List<TrendSnapshot> snapshots) {
    }
}
