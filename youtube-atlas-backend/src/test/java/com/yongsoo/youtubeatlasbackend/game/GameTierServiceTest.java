package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;

class GameTierServiceTest {

    private GameSeasonTierRepository gameSeasonTierRepository;
    private GameWalletRepository gameWalletRepository;
    private GameHighlightStateRepository gameHighlightStateRepository;
    private GameTierService gameTierService;

    @BeforeEach
    void setUp() {
        gameSeasonTierRepository = org.mockito.Mockito.mock(GameSeasonTierRepository.class);
        gameWalletRepository = org.mockito.Mockito.mock(GameWalletRepository.class);
        gameHighlightStateRepository = org.mockito.Mockito.mock(GameHighlightStateRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        gameTierService = new GameTierService(
            gameSeasonTierRepository,
            gameWalletRepository,
            gameHighlightStateRepository,
            clock
        );
    }

    @Test
    void resolveEffectiveTiersSetsLegendToTopTenPercentCutoffAmongEligibleUsers() {
        GameSeason season = season();
        GameSeasonTier masterTier = tier(season, "MASTER", "마스터", 500_000L, 6);
        GameSeasonTier legendTier = tier(season, "LEGEND", "레전드", 12_600_000L, 7);
        List<AppUser> users = java.util.stream.LongStream.rangeClosed(1L, 10L)
            .mapToObj(this::user)
            .toList();
        List<Long> scores = List.of(
            1_400_000L,
            1_300_000L,
            1_200_000L,
            1_100_000L,
            1_000_000L,
            900_000L,
            800_000L,
            700_000L,
            600_000L,
            500_000L
        );

        when(gameWalletRepository.findBySeasonId(1L))
            .thenReturn(users.stream().map(user -> wallet(season, user, 0L)).toList());
        when(gameHighlightStateRepository.findBySeasonIdAndBestSettledHighlightScoreGreaterThan(1L, 0L))
            .thenReturn(java.util.stream.IntStream.range(0, users.size())
                .mapToObj(index -> highlight(season, users.get(index), scores.get(index)))
                .toList());

        List<GameSeasonTier> effectiveTiers = gameTierService.resolveEffectiveTiers(
            season,
            List.of(masterTier, legendTier)
        );

        GameSeasonTier effectiveLegendTier = effectiveTiers.get(1);
        assertThat(effectiveLegendTier.getMinScore()).isEqualTo(1_400_000L);
        assertThat(gameTierService.resolveTier(effectiveTiers, 1_400_000L).getTierCode()).isEqualTo("LEGEND");
        assertThat(gameTierService.resolveTier(effectiveTiers, 1_399_999L).getTierCode()).isEqualTo("MASTER");
    }

    @Test
    void resolveEffectiveTiersCountsManualAdjustmentsTowardLegendCutoff() {
        GameSeason season = season();
        AppUser topUser = user(1L);
        AppUser secondUser = user(2L);
        GameSeasonTier masterTier = tier(season, "MASTER", "마스터", 500_000L, 6);
        GameSeasonTier legendTier = tier(season, "LEGEND", "레전드", 12_600_000L, 7);

        when(gameWalletRepository.findBySeasonId(1L))
            .thenReturn(List.of(wallet(season, topUser, 800_000L), wallet(season, secondUser, 0L)));
        when(gameHighlightStateRepository.findBySeasonIdAndBestSettledHighlightScoreGreaterThan(1L, 0L))
            .thenReturn(List.of(highlight(season, topUser, 100_000L), highlight(season, secondUser, 850_000L)));

        List<GameSeasonTier> effectiveTiers = gameTierService.resolveEffectiveTiers(
            season,
            List.of(masterTier, legendTier)
        );

        assertThat(effectiveTiers.get(1).getMinScore()).isEqualTo(900_000L);
        assertThat(gameTierService.resolveTier(effectiveTiers, 900_000L).getTierCode()).isEqualTo("LEGEND");
        assertThat(gameTierService.resolveTier(effectiveTiers, 850_000L).getTierCode()).isEqualTo("MASTER");
    }

    private GameSeason season() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        season.setName("Season 1");
        season.setRegionCode("KR");
        return season;
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", id);
        user.setEmail("user-" + id + "@example.com");
        user.setDisplayName("User " + id);
        return user;
    }

    private GameWallet wallet(GameSeason season, AppUser user, Long manualTierScoreAdjustment) {
        GameWallet wallet = new GameWallet();
        wallet.setSeason(season);
        wallet.setUser(user);
        wallet.setBalancePoints(0L);
        wallet.setReservedPoints(0L);
        wallet.setRealizedPnlPoints(0L);
        wallet.setManualTierScoreAdjustment(manualTierScoreAdjustment);
        wallet.setUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return wallet;
    }

    private GameHighlightState highlight(GameSeason season, AppUser user, Long score) {
        GameHighlightState state = new GameHighlightState();
        state.setSeason(season);
        state.setUser(user);
        state.setRootPositionId(user.getId());
        state.setBestSettledHighlightScore(score);
        state.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        state.setUpdatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return state;
    }

    private GameSeasonTier tier(
        GameSeason season,
        String tierCode,
        String displayName,
        Long minScore,
        Integer sortOrder
    ) {
        GameSeasonTier tier = new GameSeasonTier();
        tier.setSeason(season);
        tier.setTierCode(tierCode);
        tier.setDisplayName(displayName);
        tier.setMinScore(minScore);
        tier.setBadgeCode("season-" + tierCode.toLowerCase());
        tier.setTitleCode(tierCode.toLowerCase() + "-investor");
        tier.setProfileThemeCode(tierCode.toLowerCase());
        tier.setInventorySlots(20);
        tier.setSortOrder(sortOrder);
        tier.setCreatedAt(Instant.parse("2026-04-01T00:00:00Z"));
        return tier;
    }
}
