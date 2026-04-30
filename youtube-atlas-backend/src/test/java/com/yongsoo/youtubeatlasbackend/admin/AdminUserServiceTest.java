package com.yongsoo.youtubeatlasbackend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.admin.api.AdminWalletUpdateRequest;
import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthSessionRepository;
import com.yongsoo.youtubeatlasbackend.favorites.FavoriteStreamerRepository;
import com.yongsoo.youtubeatlasbackend.game.GameTierService;
import com.yongsoo.youtubeatlasbackend.game.GameDividendPayoutRepository;
import com.yongsoo.youtubeatlasbackend.game.GameHighlightStateRepository;
import com.yongsoo.youtubeatlasbackend.game.GameLedgerRepository;
import com.yongsoo.youtubeatlasbackend.game.GameNotificationRepository;
import com.yongsoo.youtubeatlasbackend.game.GamePositionRepository;
import com.yongsoo.youtubeatlasbackend.game.GameScheduledSellOrderRepository;
import com.yongsoo.youtubeatlasbackend.game.GameService;
import com.yongsoo.youtubeatlasbackend.game.GameSeason;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonRepository;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonTier;
import com.yongsoo.youtubeatlasbackend.game.GameSeasonTierRepository;
import com.yongsoo.youtubeatlasbackend.game.GameWallet;
import com.yongsoo.youtubeatlasbackend.game.GameWalletRepository;
import com.yongsoo.youtubeatlasbackend.game.PositionStatus;
import com.yongsoo.youtubeatlasbackend.game.SeasonStatus;
import com.yongsoo.youtubeatlasbackend.game.UserAchievementTitleRepository;
import com.yongsoo.youtubeatlasbackend.game.UserAchievementTitleSettingRepository;
import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;
import com.yongsoo.youtubeatlasbackend.playback.PlaybackProgressRepository;
import com.yongsoo.youtubeatlasbackend.playback.PlaybackProgressService;
import com.yongsoo.youtubeatlasbackend.playback.api.PlaybackProgressResponse;

class AdminUserServiceTest {

    private AppUserRepository appUserRepository;
    private AuthSessionRepository authSessionRepository;
    private FavoriteStreamerRepository favoriteStreamerRepository;
    private PlaybackProgressRepository playbackProgressRepository;
    private PlaybackProgressService playbackProgressService;
    private GameSeasonRepository gameSeasonRepository;
    private GameWalletRepository gameWalletRepository;
    private GamePositionRepository gamePositionRepository;
    private GameLedgerRepository gameLedgerRepository;
    private GameDividendPayoutRepository gameDividendPayoutRepository;
    private GameHighlightStateRepository gameHighlightStateRepository;
    private GameNotificationRepository gameNotificationRepository;
    private GameScheduledSellOrderRepository gameScheduledSellOrderRepository;
    private UserAchievementTitleRepository userAchievementTitleRepository;
    private UserAchievementTitleSettingRepository userAchievementTitleSettingRepository;
    private GameService gameService;
    private AdminAccessService adminAccessService;
    private AdminUserService adminUserService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        authSessionRepository = org.mockito.Mockito.mock(AuthSessionRepository.class);
        favoriteStreamerRepository = org.mockito.Mockito.mock(FavoriteStreamerRepository.class);
        playbackProgressRepository = org.mockito.Mockito.mock(PlaybackProgressRepository.class);
        playbackProgressService = org.mockito.Mockito.mock(PlaybackProgressService.class);
        gameSeasonRepository = org.mockito.Mockito.mock(GameSeasonRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gamePositionRepository = org.mockito.Mockito.mock(GamePositionRepository.class);
        gameLedgerRepository = org.mockito.Mockito.mock(GameLedgerRepository.class);
        gameDividendPayoutRepository = org.mockito.Mockito.mock(GameDividendPayoutRepository.class);
        gameHighlightStateRepository = org.mockito.Mockito.mock(GameHighlightStateRepository.class);
        gameNotificationRepository = org.mockito.Mockito.mock(GameNotificationRepository.class);
        gameScheduledSellOrderRepository = org.mockito.Mockito.mock(GameScheduledSellOrderRepository.class);
        userAchievementTitleRepository = org.mockito.Mockito.mock(UserAchievementTitleRepository.class);
        userAchievementTitleSettingRepository = org.mockito.Mockito.mock(UserAchievementTitleSettingRepository.class);
        gameService = org.mockito.Mockito.mock(GameService.class);
        adminAccessService = org.mockito.Mockito.mock(AdminAccessService.class);
        clock = Clock.fixed(Instant.parse("2026-04-08T03:00:00Z"), ZoneOffset.UTC);
        GameSeasonTierRepository gameSeasonTierRepository = org.mockito.Mockito.mock(GameSeasonTierRepository.class);
        GameTierService gameTierService = new GameTierService(gameSeasonTierRepository, clock);
        adminUserService = new AdminUserService(
            appUserRepository,
            authSessionRepository,
            favoriteStreamerRepository,
            playbackProgressRepository,
            playbackProgressService,
            gameSeasonRepository,
            gameWalletRepository,
            gamePositionRepository,
            gameLedgerRepository,
            gameDividendPayoutRepository,
            gameHighlightStateRepository,
            gameNotificationRepository,
            gameScheduledSellOrderRepository,
            userAchievementTitleRepository,
            userAchievementTitleSettingRepository,
            gameTierService,
            gameService,
            adminAccessService,
            clock
        );
        when(gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(3L)).thenReturn(defaultTiers(activeSeason(3L, "Season 3")));
        when(gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(4L)).thenReturn(defaultTiers(activeSeason(4L, "Season 4")));
        when(gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(5L)).thenReturn(defaultTiers(activeSeason(5L, "Season 5")));
    }

    @Test
    void getUsersSearchesByQueryAndMarksAdminUsers() {
        AppUser user = user(7L, "admin@example.com", "Atlas Admin");
        when(appUserRepository.findByEmailContainingIgnoreCaseOrDisplayNameContainingIgnoreCaseOrderByCreatedAtDesc(
            "admin",
            "admin",
            PageRequest.of(0, 15)
        )).thenReturn(List.of(user));
        when(adminAccessService.isAdminEmail("admin@example.com")).thenReturn(true);

        var response = adminUserService.getUsers(" admin ", 15);

        assertThat(response.query()).isEqualTo("admin");
        assertThat(response.limit()).isEqualTo(15);
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).admin()).isTrue();
        assertThat(response.users().get(0).email()).isEqualTo("admin@example.com");
    }

    @Test
    void getUserAggregatesPlaybackFavoritesAndActiveSeasonGameSummary() {
        AppUser user = user(9L, "user@example.com", "Atlas User");
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 3L);
        season.setName("Season 3");
        season.setStatus(SeasonStatus.ACTIVE);
        season.setRegionCode("KR");

        GameSeason usSeason = new GameSeason();
        ReflectionTestUtils.setField(usSeason, "id", 5L);
        usSeason.setName("Season 5");
        usSeason.setStatus(SeasonStatus.ACTIVE);
        usSeason.setRegionCode("US");

        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(12000L);
        wallet.setReservedPoints(3000L);
        wallet.setRealizedPnlPoints(1500L);

        PlaybackProgressResponse playbackProgress = new PlaybackProgressResponse(
            "video-1",
            "Sample video",
            "Atlas TV",
            "https://example.com/thumb.jpg",
            184L,
            Instant.parse("2026-04-08T01:00:00Z")
        );

        when(appUserRepository.findById(9L)).thenReturn(Optional.of(user));
        when(adminAccessService.isAdminEmail("user@example.com")).thenReturn(false);
        when(favoriteStreamerRepository.countByUserId(9L)).thenReturn(4L);
        when(playbackProgressService.getCurrentProgressForUserId(9L)).thenReturn(Optional.of(playbackProgress));
        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of(usSeason, season));
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatus(3L, 9L, PositionStatus.OPEN)).thenReturn(2L);
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatusIn(eq(3L), eq(9L), any())).thenReturn(5L);
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatus(5L, 9L, PositionStatus.OPEN)).thenReturn(0L);
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatusIn(eq(5L), eq(9L), any())).thenReturn(0L);
        when(gameWalletRepository.findBySeasonIdAndUserId(3L, 9L)).thenReturn(Optional.of(wallet));
        when(gameWalletRepository.findBySeasonIdAndUserId(5L, 9L)).thenReturn(Optional.empty());
        when(gameService.calculateSettledUserHighlightScore(3L, 9L)).thenReturn(600_000L);
        when(gameService.calculateSettledUserHighlightScore(5L, 9L)).thenReturn(0L);

        var response = adminUserService.getUser(9L);

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.favoriteCount()).isEqualTo(4L);
        assertThat(response.lastPlaybackProgress()).isEqualTo(playbackProgress);
        assertThat(response.activeSeasonGame()).isNotNull();
        assertThat(response.activeSeasonGame().seasonId()).isEqualTo(3L);
        assertThat(response.activeSeasonGame().regionCode()).isEqualTo("KR");
        assertThat(response.activeSeasonGames()).hasSize(2);
        assertThat(response.activeSeasonGames().get(0).regionCode()).isEqualTo("KR");
        assertThat(response.activeSeasonGames().get(1).regionCode()).isEqualTo("US");
        assertThat(response.activeSeasonGame().participating()).isTrue();
        assertThat(response.activeSeasonGame().totalAssetPoints()).isEqualTo(15000L);
        assertThat(response.activeSeasonGame().tierScore()).isEqualTo(600_000L);
        assertThat(response.activeSeasonGame().currentTier()).isNotNull();
        assertThat(response.activeSeasonGame().currentTier().tierCode()).isEqualTo("DIAMOND");
        assertThat(response.activeSeasonGame().nextTier()).isNotNull();
        assertThat(response.activeSeasonGame().nextTier().tierCode()).isEqualTo("MASTER");
        assertThat(response.activeSeasonGame().openPositionCount()).isEqualTo(2L);
        assertThat(response.activeSeasonGame().closedPositionCount()).isEqualTo(5L);
    }

    @Test
    void getUsersRejectsNonPositiveLimit() {
        assertThatThrownBy(() -> adminUserService.getUsers(null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
    }

    @Test
    void updateActiveSeasonWalletOverridesWalletValues() {
        AppUser user = user(11L, "wallet@example.com", "Wallet User");
        GameSeason season = activeSeason(4L, "Season 4");
        GameWallet wallet = new GameWallet();
        wallet.setBalancePoints(1000L);
        wallet.setReservedPoints(200L);
        wallet.setRealizedPnlPoints(50L);

        when(appUserRepository.findById(11L)).thenReturn(Optional.of(user));
        when(gameSeasonRepository.findByIdAndStatus(4L, SeasonStatus.ACTIVE)).thenReturn(Optional.of(season));
        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(4L, 11L)).thenReturn(Optional.of(wallet));
        when(gameWalletRepository.save(wallet)).thenReturn(wallet);
        when(adminAccessService.isAdminEmail("wallet@example.com")).thenReturn(false);
        when(favoriteStreamerRepository.countByUserId(11L)).thenReturn(0L);
        when(playbackProgressService.getCurrentProgressForUserId(11L)).thenReturn(Optional.empty());
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatus(4L, 11L, PositionStatus.OPEN)).thenReturn(0L);
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatusIn(eq(4L), eq(11L), any())).thenReturn(0L);
        when(gameService.calculateSettledUserHighlightScore(4L, 11L)).thenReturn(250_000L);

        var response = adminUserService.updateActiveSeasonWallet(
            11L,
            new AdminWalletUpdateRequest(4L, 5000L, 1200L, 700L, 5_000L)
        );

        assertThat(wallet.getBalancePoints()).isEqualTo(5000L);
        assertThat(wallet.getReservedPoints()).isEqualTo(1200L);
        assertThat(wallet.getRealizedPnlPoints()).isEqualTo(700L);
        assertThat(wallet.getManualTierScoreAdjustment()).isEqualTo(5_000L);
        assertThat(wallet.getUpdatedAt()).isEqualTo(Instant.parse("2026-04-08T03:00:00Z"));
        assertThat(response.activeSeasonGame()).isNotNull();
        assertThat(response.activeSeasonGame().balancePoints()).isEqualTo(5000L);
        assertThat(response.activeSeasonGame().reservedPoints()).isEqualTo(1200L);
        assertThat(response.activeSeasonGame().calculatedTierScore()).isEqualTo(250_000L);
        assertThat(response.activeSeasonGame().manualTierScoreAdjustment()).isEqualTo(5_000L);
        assertThat(response.activeSeasonGame().tierScore()).isEqualTo(255_000L);
    }

    @Test
    void getUserHighlightsSummarizesScoreAndHistoryForSeason() {
        AppUser user = user(21L, "highlight@example.com", "Highlight User");
        GameSeason season = activeSeason(4L, "Season 4");
        GameWallet wallet = new GameWallet();
        wallet.setManualTierScoreAdjustment(1_500L);
        GameHighlightResponse highlight = new GameHighlightResponse(
            "position-1-MOONSHOT",
            "MOONSHOT",
            "역주행",
            "120위에서 20위까지 상승",
            31L,
            "video-1",
            "Highlight Video",
            "Atlas TV",
            "https://example.com/thumb.jpg",
            120,
            20,
            22,
            100,
            100L,
            5_000L,
            20_000L,
            15_000L,
            300D,
            List.of(),
            42_000L,
            "CLOSED",
            Instant.parse("2026-04-08T02:00:00Z")
        );

        when(appUserRepository.findById(21L)).thenReturn(Optional.of(user));
        when(gameSeasonRepository.findById(4L)).thenReturn(Optional.of(season));
        when(gameWalletRepository.findBySeasonIdAndUserId(4L, 21L)).thenReturn(Optional.of(wallet));
        when(gameService.calculateSettledUserHighlightScore(4L, 21L)).thenReturn(42_000L);
        when(gameService.getSettledUserHighlights(4L, 21L)).thenReturn(List.of(highlight));

        var response = adminUserService.getUserHighlights(21L, 4L);

        assertThat(response.userId()).isEqualTo(21L);
        assertThat(response.seasonId()).isEqualTo(4L);
        assertThat(response.calculatedHighlightScore()).isEqualTo(42_000L);
        assertThat(response.manualTierScoreAdjustment()).isEqualTo(1_500L);
        assertThat(response.tierScore()).isEqualTo(43_500L);
        assertThat(response.highlightCount()).isEqualTo(1);
        assertThat(response.highlights()).containsExactly(highlight);
    }

    @Test
    void deleteUserRemovesDependentRowsBeforeDeletingUser() {
        AppUser user = user(13L, "delete@example.com", "Delete User");
        when(appUserRepository.findById(13L)).thenReturn(Optional.of(user));

        adminUserService.deleteUser(13L);

        org.mockito.Mockito.verify(authSessionRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(playbackProgressRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(favoriteStreamerRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gameLedgerRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gameDividendPayoutRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gameHighlightStateRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gameNotificationRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gameScheduledSellOrderRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(userAchievementTitleSettingRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(userAchievementTitleRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gamePositionRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(gameWalletRepository).deleteByUserId(13L);
        org.mockito.Mockito.verify(appUserRepository).delete(user);
    }

    @Test
    void deleteUserAlsoRemovesSeasonScopedGameDataForOtherRegions() {
        AppUser user = user(17L, "global@example.com", "Global User");
        when(appUserRepository.findById(17L)).thenReturn(Optional.of(user));

        adminUserService.deleteUser(17L);

        org.mockito.Mockito.verify(gameDividendPayoutRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(gameHighlightStateRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(gameNotificationRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(gameScheduledSellOrderRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(userAchievementTitleSettingRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(userAchievementTitleRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(gamePositionRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(gameWalletRepository).deleteByUserId(17L);
        org.mockito.Mockito.verify(appUserRepository).delete(user);
    }

    @Test
    void getUserProvidesSeasonDefaultsWhenWalletDoesNotExist() {
        AppUser user = user(15L, "new@example.com", "New User");
        GameSeason season = activeSeason(4L, "Season 4");

        when(appUserRepository.findById(15L)).thenReturn(Optional.of(user));
        when(adminAccessService.isAdminEmail("new@example.com")).thenReturn(false);
        when(favoriteStreamerRepository.countByUserId(15L)).thenReturn(0L);
        when(playbackProgressService.getCurrentProgressForUserId(15L)).thenReturn(Optional.empty());
        when(gameSeasonRepository.findByStatus(SeasonStatus.ACTIVE)).thenReturn(List.of(season));
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatus(4L, 15L, PositionStatus.OPEN)).thenReturn(0L);
        when(gamePositionRepository.countBySeasonIdAndUserIdAndStatusIn(eq(4L), eq(15L), any())).thenReturn(0L);
        when(gameWalletRepository.findBySeasonIdAndUserId(4L, 15L)).thenReturn(Optional.empty());

        var response = adminUserService.getUser(15L);

        assertThat(response.activeSeasonGame()).isNotNull();
        assertThat(response.activeSeasonGame().participating()).isFalse();
        assertThat(response.activeSeasonGame().balancePoints()).isEqualTo(10_000L);
        assertThat(response.activeSeasonGame().reservedPoints()).isEqualTo(0L);
        assertThat(response.activeSeasonGame().realizedPnlPoints()).isEqualTo(0L);
        assertThat(response.activeSeasonGame().tierScore()).isEqualTo(0L);
        assertThat(response.activeSeasonGame().currentTier()).isNotNull();
        assertThat(response.activeSeasonGame().currentTier().tierCode()).isEqualTo("BRONZE");
    }

    private AppUser user(Long id, String email, String displayName) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        user.setLastLoginAt(Instant.parse("2026-04-02T00:00:00Z"));
        return user;
    }

    private GameSeason activeSeason(Long id, String name) {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", id);
        season.setName(name);
        season.setStatus(SeasonStatus.ACTIVE);
        season.setRegionCode("KR");
        season.setStartingBalancePoints(10_000L);
        season.setStartAt(Instant.parse("2026-04-01T00:00:00Z"));
        return season;
    }

    private List<GameSeasonTier> defaultTiers(GameSeason season) {
        return List.of(
            tier(season, "BRONZE", "브론즈", 0L, 1),
            tier(season, "SILVER", "실버", 5_000L, 2),
            tier(season, "GOLD", "골드", 15_000L, 3),
            tier(season, "PLATINUM", "플래티넘", 60_000L, 4),
            tier(season, "DIAMOND", "다이아몬드", 300_000L, 5),
            tier(season, "MASTER", "마스터", 1_800_000L, 6),
            tier(season, "LEGEND", "레전드", 12_600_000L, 7)
        );
    }

    private GameSeasonTier tier(GameSeason season, String code, String displayName, Long minScore, int sortOrder) {
        GameSeasonTier tier = new GameSeasonTier();
        tier.setSeason(season);
        tier.setTierCode(code);
        tier.setDisplayName(displayName);
        tier.setMinScore(minScore);
        tier.setBadgeCode(code.toLowerCase());
        tier.setTitleCode(code.toLowerCase());
        tier.setProfileThemeCode(code.toLowerCase());
        tier.setSortOrder(sortOrder);
        tier.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return tier;
    }
}
