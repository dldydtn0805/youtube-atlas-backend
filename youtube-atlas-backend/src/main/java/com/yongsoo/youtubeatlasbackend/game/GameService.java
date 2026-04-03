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
import com.yongsoo.youtubeatlasbackend.trending.TrendSignal;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalId;
import com.yongsoo.youtubeatlasbackend.trending.TrendSignalRepository;

@Service
public class GameService {

    private static final String TRENDING_CATEGORY_ID = "0";
    private static final long STAKE_UNIT_POINTS = 1_000L;

    private final GameSeasonRepository gameSeasonRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final AppUserRepository appUserRepository;
    private final TrendSignalRepository trendSignalRepository;
    private final Clock clock;

    public GameService(
        GameSeasonRepository gameSeasonRepository,
        GameWalletRepository gameWalletRepository,
        GamePositionRepository gamePositionRepository,
        GameLedgerRepository gameLedgerRepository,
        AppUserRepository appUserRepository,
        TrendSignalRepository trendSignalRepository,
        Clock clock
    ) {
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.appUserRepository = appUserRepository;
        this.trendSignalRepository = trendSignalRepository;
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
            boolean alreadyOwned = gamePositionRepository.existsBySeasonIdAndUserIdAndVideoIdAndStatus(
                season.getId(),
                authenticatedUser.id(),
                signal.getId().getVideoId(),
                PositionStatus.OPEN
            );
            String blockedReason = resolveBuyBlockedReason(wallet, signal.getId().getVideoId(), alreadyOwned, maxOpenReached);

            return new MarketVideoResponse(
                signal.getId().getVideoId(),
                signal.getTitle(),
                signal.getChannelTitle(),
                signal.getThumbnailUrl(),
                signal.getCurrentRank(),
                signal.getPreviousRank(),
                signal.getRankChange(),
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
        long stakePoints = normalizeStakePoints(request.stakePoints());
        Instant now = Instant.now(clock);

        if (!season.getRegionCode().equalsIgnoreCase(regionCode)) {
            throw new IllegalArgumentException("현재 시즌에서 지원하지 않는 regionCode입니다.");
        }

        if (wallet.getBalancePoints() < stakePoints) {
            throw new IllegalArgumentException("보유 포인트가 부족합니다.");
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
        position.setStakePoints(stakePoints);
        position.setStatus(PositionStatus.OPEN);
        position.setCreatedAt(now);
        GamePosition savedPosition = gamePositionRepository.save(position);

        wallet.setBalancePoints(wallet.getBalancePoints() - stakePoints);
        wallet.setReservedPoints(wallet.getReservedPoints() + stakePoints);
        wallet.setUpdatedAt(now);
        gameWalletRepository.save(wallet);

        saveLedger(season, user, savedPosition, LedgerType.BUY_LOCK, -stakePoints, wallet.getBalancePoints(), now);

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

        TrendSignal signal = requireTrendSignal(position.getRegionCode(), position.getCategoryId(), position.getVideoId());
        GameWallet wallet = gameWalletRepository.findBySeasonIdAndUserId(position.getSeason().getId(), authenticatedUser.id())
            .orElseThrow(() -> new IllegalArgumentException("지갑 정보를 찾을 수 없습니다."));
        int rankDiff = position.getBuyRank() - signal.getCurrentRank();
        long pnlPoints = GamePointCalculator.calculateProfitPoints(
            position.getStakePoints(),
            position.getSeason().getRankPointMultiplier(),
            rankDiff
        );
        long settledPoints = GamePointCalculator.calculateSettledPoints(position.getStakePoints(), pnlPoints);

        position.setSellRunId(signal.getCurrentRunId());
        position.setSellRank(signal.getCurrentRank());
        position.setSellCapturedAt(signal.getCapturedAt());
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
            TrendSignal signal = trendSignalRepository.findById(
                new TrendSignalId(position.getRegionCode(), position.getCategoryId(), position.getVideoId())
            ).orElse(null);

            if (signal != null) {
                return toOpenPositionResponse(position, signal);
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
            position.getPnlPoints(),
            position.getStatus().name(),
            position.getBuyCapturedAt(),
            position.getCreatedAt(),
            position.getClosedAt()
        );
    }

    private PositionResponse toOpenPositionResponse(GamePosition position, TrendSignal signal) {
        int rankDiff = position.getBuyRank() - signal.getCurrentRank();
        long profitPoints = GamePointCalculator.calculateProfitPoints(
            position.getStakePoints(),
            position.getSeason().getRankPointMultiplier(),
            rankDiff
        );

        return new PositionResponse(
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            position.getBuyRank(),
            signal.getCurrentRank(),
            rankDiff,
            position.getStakePoints(),
            profitPoints,
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

    private String resolveBuyBlockedReason(GameWallet wallet, String videoId, boolean alreadyOwned, boolean maxOpenReached) {
        if (alreadyOwned) {
            return "이미 보유 중인 영상입니다.";
        }
        if (maxOpenReached) {
            return "동시 보유 가능 포지션 수를 초과했습니다.";
        }
        if (wallet.getBalancePoints() < STAKE_UNIT_POINTS) {
            return "최소 매수 포인트가 부족합니다.";
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
                int rankDiff = position.getBuyRank() - signal.getCurrentRank();
                long profitPoints = GamePointCalculator.calculateProfitPoints(
                    position.getStakePoints(),
                    position.getSeason().getRankPointMultiplier(),
                    rankDiff
                );
                markedValue = GamePointCalculator.calculateSettledPoints(position.getStakePoints(), profitPoints);
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

    private long normalizeStakePoints(Long stakePoints) {
        if (stakePoints == null || stakePoints < STAKE_UNIT_POINTS) {
            throw new IllegalArgumentException("stakePoints는 1000 이상이어야 합니다.");
        }

        if (stakePoints % STAKE_UNIT_POINTS != 0L) {
            throw new IllegalArgumentException("stakePoints는 1000 포인트 단위여야 합니다.");
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
}
