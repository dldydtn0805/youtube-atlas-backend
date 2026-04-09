package com.yongsoo.youtubeatlasbackend.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminUserDetailResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminCoinTierSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminUserGameSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminUserListResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminUserSummaryResponse;
import com.yongsoo.youtubeatlasbackend.admin.api.AdminWalletUpdateRequest;
import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthSessionRepository;
import com.yongsoo.youtubeatlasbackend.favorites.FavoriteStreamerRepository;
import com.yongsoo.youtubeatlasbackend.game.GameLedgerRepository;
import com.yongsoo.youtubeatlasbackend.game.GameCoinPayoutRepository;
import com.yongsoo.youtubeatlasbackend.game.GameCoinTierService;
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonCoinResultRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonCoinTier;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.GameWallet;
import com.yongsoo.youtubeatlasbackend.game.GameWalletRepository;
import com.yongsoo.youtubeatlasbackend.game.PositionStatus;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;
import com.yongsoo.youtubeatlasbackend.playback.PlaybackProgressRepository;
import com.yongsoo.youtubeatlasbackend.playback.PlaybackProgressService;
import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;

@Service
public class AdminUserService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AppUserRepository appUserRepository;
    private final AuthSessionRepository authSessionRepository;
    private final FavoriteStreamerRepository favoriteStreamerRepository;
    private final PlaybackProgressRepository playbackProgressRepository;
    private final PlaybackProgressService playbackProgressService;
    private final GameSeasonRepository gameSeasonRepository;
    private final GameWalletRepository gameWalletRepository;
    private final GamePositionRepository gamePositionRepository;
    private final GameLedgerRepository gameLedgerRepository;
    private final GameCoinPayoutRepository gameCoinPayoutRepository;
    private final GameSeasonCoinResultRepository gameSeasonCoinResultRepository;
    private final GameCoinTierService gameCoinTierService;
    private final AdminAccessService adminAccessService;
    private final Clock clock;

    public AdminUserService(
        AppUserRepository appUserRepository,
        AuthSessionRepository authSessionRepository,
        FavoriteStreamerRepository favoriteStreamerRepository,
        PlaybackProgressRepository playbackProgressRepository,
        PlaybackProgressService playbackProgressService,
        GameSeasonRepository gameSeasonRepository,
        GameWalletRepository gameWalletRepository,
        GamePositionRepository gamePositionRepository,
        GameLedgerRepository gameLedgerRepository,
        GameCoinPayoutRepository gameCoinPayoutRepository,
        GameSeasonCoinResultRepository gameSeasonCoinResultRepository,
        GameCoinTierService gameCoinTierService,
        AdminAccessService adminAccessService,
        Clock clock
    ) {
        this.appUserRepository = appUserRepository;
        this.authSessionRepository = authSessionRepository;
        this.favoriteStreamerRepository = favoriteStreamerRepository;
        this.playbackProgressRepository = playbackProgressRepository;
        this.playbackProgressService = playbackProgressService;
        this.gameSeasonRepository = gameSeasonRepository;
        this.gameWalletRepository = gameWalletRepository;
        this.gamePositionRepository = gamePositionRepository;
        this.gameLedgerRepository = gameLedgerRepository;
        this.gameCoinPayoutRepository = gameCoinPayoutRepository;
        this.gameSeasonCoinResultRepository = gameSeasonCoinResultRepository;
        this.gameCoinTierService = gameCoinTierService;
        this.adminAccessService = adminAccessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AdminUserListResponse getUsers(String query, Integer limit) {
        String normalizedQuery = normalizeQuery(query);
        int normalizedLimit = normalizeLimit(limit);
        PageRequest pageRequest = PageRequest.of(0, normalizedLimit);

        List<AdminUserSummaryResponse> users = loadUsers(normalizedQuery, pageRequest).stream()
            .map(this::toSummaryResponse)
            .toList();

        return new AdminUserListResponse(
            normalizedQuery,
            normalizedLimit,
            users.size(),
            users
        );
    }

    @Transactional
    public AdminUserDetailResponse getUser(Long userId) {
        return toDetailResponse(requireUser(userId));
    }

    @Transactional
    public AdminUserDetailResponse updateActiveSeasonWallet(Long userId, AdminWalletUpdateRequest request) {
        AppUser user = requireUser(userId);
        GameSeason activeSeason = gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("현재 활성 시즌이 없습니다."));
        validateWalletUpdateRequest(request);

        GameWallet wallet = gameWalletRepository.findBySeasonIdAndUserId(activeSeason.getId(), userId)
            .orElseGet(() -> createWallet(activeSeason, user));

        wallet.setBalancePoints(request.balancePoints());
        wallet.setReservedPoints(request.reservedPoints());
        wallet.setRealizedPnlPoints(request.realizedPnlPoints());
        wallet.setCoinBalance(request.coinBalance());
        wallet.setUpdatedAt(Instant.now(clock));
        gameWalletRepository.save(wallet);

        return toDetailResponse(user);
    }

    @Transactional
    public void deleteUser(Long userId) {
        AppUser user = requireUser(userId);

        authSessionRepository.deleteByUserId(userId);
        playbackProgressRepository.deleteByUserId(userId);
        favoriteStreamerRepository.deleteByUserId(userId);
        gameLedgerRepository.deleteByUserId(userId);
        gameCoinPayoutRepository.deleteByUserId(userId);
        gamePositionRepository.deleteByUserId(userId);
        gameSeasonCoinResultRepository.deleteByUserId(userId);
        gameWalletRepository.deleteByUserId(userId);
        appUserRepository.delete(user);
    }

    private List<AppUser> loadUsers(String normalizedQuery, PageRequest pageRequest) {
        if (normalizedQuery == null) {
            return appUserRepository.findAllByOrderByCreatedAtDesc(pageRequest);
        }

        return appUserRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(
            normalizedQuery,
            normalizedQuery,
            pageRequest
        );
    }

    private AdminUserSummaryResponse toSummaryResponse(AppUser user) {
        return new AdminUserSummaryResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getPictureUrl(),
            adminAccessService.isAdminEmail(user.getEmail()),
            user.getCreatedAt(),
            user.getLastLoginAt()
        );
    }

    private AdminUserDetailResponse toDetailResponse(AppUser user) {
        PlaybackProgressResponse lastPlaybackProgress = playbackProgressService.getCurrentProgressForUserId(user.getId())
            .orElse(null);
        long favoriteCount = favoriteStreamerRepository.countByUserId(user.getId());
        AdminUserGameSummaryResponse activeSeasonGame = gameSeasonRepository.findTopByStatusOrderByStartAtDesc(SeasonStatus.ACTIVE)
            .map(season -> toGameSummary(user.getId(), season))
            .orElse(null);

        return new AdminUserDetailResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getPictureUrl(),
            adminAccessService.isAdminEmail(user.getEmail()),
            user.getCreatedAt(),
            user.getLastLoginAt(),
            favoriteCount,
            lastPlaybackProgress,
            activeSeasonGame
        );
    }

    private AdminUserGameSummaryResponse toGameSummary(Long userId, GameSeason activeSeason) {
        List<GameSeasonCoinTier> tiers = gameCoinTierService.getOrCreateTiers(activeSeason);
        long openPositionCount = gamePositionRepository.countBySeasonIdAndUserIdAndStatus(
            activeSeason.getId(),
            userId,
            PositionStatus.OPEN
        );
        long closedPositionCount = gamePositionRepository.countBySeasonIdAndUserIdAndStatusIn(
            activeSeason.getId(),
            userId,
            List.of(PositionStatus.CLOSED, PositionStatus.AUTO_CLOSED)
        );

        return gameWalletRepository.findBySeasonIdAndUserId(activeSeason.getId(), userId)
            .map(wallet -> {
                long coinBalance = normalizeNonNegative(wallet.getCoinBalance());
                GameSeasonCoinTier currentCoinTier = resolveCurrentTier(tiers, coinBalance);
                GameSeasonCoinTier nextCoinTier = resolveNextTier(tiers, coinBalance);

                return new AdminUserGameSummaryResponse(
                    activeSeason.getId(),
                    activeSeason.getName(),
                    true,
                    wallet.getBalancePoints(),
                    wallet.getReservedPoints(),
                    wallet.getRealizedPnlPoints(),
                    coinBalance,
                    wallet.getBalancePoints() + wallet.getReservedPoints(),
                    toCoinTierSummary(currentCoinTier),
                    toCoinTierSummary(nextCoinTier),
                    openPositionCount,
                    closedPositionCount
                );
            })
            .orElseGet(() -> new AdminUserGameSummaryResponse(
                activeSeason.getId(),
                activeSeason.getName(),
                false,
                activeSeason.getStartingBalancePoints(),
                0L,
                0L,
                0L,
                activeSeason.getStartingBalancePoints(),
                toCoinTierSummary(resolveCurrentTier(tiers, 0L)),
                toCoinTierSummary(resolveNextTier(tiers, 0L)),
                openPositionCount,
                closedPositionCount
            ));
    }

    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private GameWallet createWallet(GameSeason season, AppUser user) {
        GameWallet wallet = new GameWallet();
        wallet.setSeason(season);
        wallet.setUser(user);
        wallet.setBalancePoints(season.getStartingBalancePoints());
        wallet.setReservedPoints(0L);
        wallet.setRealizedPnlPoints(0L);
        wallet.setCoinBalance(0L);
        wallet.setUpdatedAt(Instant.now(clock));
        return gameWalletRepository.save(wallet);
    }

    private void validateWalletUpdateRequest(AdminWalletUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("지갑 수정 정보가 필요합니다.");
        }

        validateNonNegative(request.balancePoints(), "balancePoints");
        validateNonNegative(request.reservedPoints(), "reservedPoints");
        validateNonNegative(request.realizedPnlPoints(), "realizedPnlPoints");
        validateNonNegative(request.coinBalance(), "coinBalance");
    }

    private void validateNonNegative(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + "는 필수입니다.");
        }

        if (value < 0) {
            throw new IllegalArgumentException(fieldName + "는 0 이상이어야 합니다.");
        }
    }

    private long normalizeNonNegative(Long value) {
        return value != null ? value : 0L;
    }

    private GameSeasonCoinTier resolveCurrentTier(List<GameSeasonCoinTier> tiers, long coinBalance) {
        return tiers.isEmpty() ? null : gameCoinTierService.resolveTier(tiers, coinBalance);
    }

    private GameSeasonCoinTier resolveNextTier(List<GameSeasonCoinTier> tiers, long coinBalance) {
        return tiers.stream()
            .filter(tier -> tier.getMinCoinBalance() > coinBalance)
            .min(
                java.util.Comparator.comparingLong(GameSeasonCoinTier::getMinCoinBalance)
                    .thenComparingInt(GameSeasonCoinTier::getSortOrder)
            )
            .orElse(null);
    }

    private AdminCoinTierSummaryResponse toCoinTierSummary(GameSeasonCoinTier tier) {
        if (tier == null) {
            return null;
        }

        return new AdminCoinTierSummaryResponse(
            tier.getTierCode(),
            tier.getDisplayName(),
            tier.getMinCoinBalance(),
            tier.getBadgeCode(),
            tier.getTitleCode(),
            tier.getProfileThemeCode()
        );
    }

    private String normalizeQuery(String query) {
        return StringUtils.hasText(query) ? query.trim() : null;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }

        if (limit < 1) {
            throw new IllegalArgumentException("limit는 1 이상이어야 합니다.");
        }

        return Math.min(limit, MAX_LIMIT);
    }
}
