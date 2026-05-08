package com.yongsoo.youtubeatlasbackend.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;

class AchievementTitleServiceTest {

    private AchievementTitleRepository achievementTitleRepository;
    private UserAchievementTitleRepository userAchievementTitleRepository;
    private UserAchievementTitleSettingRepository userAchievementTitleSettingRepository;
    private GameSeasonResultRepository gameSeasonResultRepository;
    private AppUserRepository appUserRepository;
    private AchievementTitleService achievementTitleService;
    private List<AchievementTitle> titles;
    private List<UserAchievementTitle> userTitles;
    private UserAchievementTitleSetting setting;

    @BeforeEach
    void setUp() {
        achievementTitleRepository = org.mockito.Mockito.mock(AchievementTitleRepository.class);
        userAchievementTitleRepository = org.mockito.Mockito.mock(UserAchievementTitleRepository.class);
        userAchievementTitleSettingRepository = org.mockito.Mockito.mock(UserAchievementTitleSettingRepository.class);
        gameSeasonResultRepository = org.mockito.Mockito.mock(GameSeasonResultRepository.class);
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        titles = new ArrayList<>();
        userTitles = new ArrayList<>();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);

        achievementTitleService = new AchievementTitleService(
            achievementTitleRepository,
            userAchievementTitleRepository,
            userAchievementTitleSettingRepository,
            gameSeasonResultRepository,
            appUserRepository,
            fixedClock
        );

        when(achievementTitleRepository.findAllByOrderBySortOrderAsc()).thenAnswer(invocation ->
            titles.stream()
                .sorted(Comparator.comparingInt(AchievementTitle::getSortOrder))
                .toList()
        );
        when(achievementTitleRepository.saveAllAndFlush(any())).thenAnswer(invocation -> {
            Iterable<AchievementTitle> newTitles = invocation.getArgument(0);
            newTitles.forEach(title -> {
                ReflectionTestUtils.setField(title, "id", (long) titles.size() + 1L);
                titles.add(title);
            });
            return newTitles;
        });
        when(userAchievementTitleRepository.existsActiveByUserIdAndTitleCode(any(), any())).thenAnswer(invocation ->
            userTitles.stream().anyMatch(userTitle ->
                userTitle.getUser().getId().equals(invocation.getArgument(0, Long.class))
                    && userTitle.getTitle().getCode().equals(invocation.getArgument(1, String.class))
                    && userTitle.getRevokedAt() == null
            )
        );
        when(userAchievementTitleRepository.save(any(UserAchievementTitle.class))).thenAnswer(invocation -> {
            UserAchievementTitle userTitle = invocation.getArgument(0, UserAchievementTitle.class);
            ReflectionTestUtils.setField(userTitle, "id", (long) userTitles.size() + 1L);
            userTitles.add(userTitle);
            return userTitle;
        });
        when(userAchievementTitleRepository.findByUserIdAndRevokedAtIsNull(any())).thenAnswer(invocation ->
            userTitles.stream()
                .filter(userTitle -> userTitle.getUser().getId().equals(invocation.getArgument(0, Long.class)))
                .filter(userTitle -> userTitle.getRevokedAt() == null)
                .toList()
        );
        when(userAchievementTitleSettingRepository.findByUserId(any())).thenAnswer(invocation ->
            setting != null && setting.getUser().getId().equals(invocation.getArgument(0, Long.class))
                ? Optional.of(setting)
                : Optional.empty()
        );
        when(userAchievementTitleSettingRepository.save(any(UserAchievementTitleSetting.class))).thenAnswer(invocation -> {
            setting = invocation.getArgument(0, UserAchievementTitleSetting.class);
            ReflectionTestUtils.setField(setting, "id", 1L);
            return setting;
        });
    }

    @Test
    void grantTitlesForHighlightAwardsAllTitlesForAtlasSniper() {
        GameHighlightState highlight = highlightState("ATLAS_SHOT,GALAXY_SHOT,SOLAR_SHOT,MOONSHOT,SNIPE");

        List<AchievementTitle> unlockedTitles =
            achievementTitleService.grantTitlesForHighlight(highlight, AchievementTitleSourceType.HIGHLIGHT);

        assertThat(earnedCodes()).containsExactlyInAnyOrder(
            "SNIPE_SEEKER",
            "MOON_SEEKER",
            "MOON_FINDER",
            "SOLAR_SEEKER",
            "SOLAR_FINDER",
            "SOLAR_WALKER",
            "GALAXY_SEEKER",
            "GALAXY_FINDER",
            "GALAXY_WALKER",
            "ATLAS_SEEKER",
            "ATLAS_FINDER",
            "ATLAS_WALKER",
            "ATLAS_SNIPER"
        );
        assertThat(unlockedTitles)
            .extracting(AchievementTitle::getCode)
            .containsExactly(
                "SNIPE_SEEKER",
                "MOON_SEEKER",
                "SOLAR_SEEKER",
                "GALAXY_SEEKER",
                "ATLAS_SEEKER",
                "MOON_FINDER",
                "SOLAR_FINDER",
                "GALAXY_FINDER",
                "ATLAS_FINDER",
                "SOLAR_WALKER",
                "GALAXY_WALKER",
                "ATLAS_WALKER",
                "ATLAS_SNIPER"
            );
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("ATLAS_SNIPER");
        assertThat(setting.getSelectionMode()).isEqualTo(AchievementTitleSelectionMode.AUTO);
    }

    @Test
    void grantTitlesForHighlightAwardsAtlasFinderForAtlasShotGalaxyShotCombo() {
        GameHighlightState highlight = highlightState("ATLAS_SHOT,GALAXY_SHOT");

        List<AchievementTitle> unlockedTitles =
            achievementTitleService.grantTitlesForHighlight(highlight, AchievementTitleSourceType.HIGHLIGHT);

        assertThat(earnedCodes()).containsExactlyInAnyOrder("GALAXY_SEEKER", "ATLAS_SEEKER", "ATLAS_FINDER");
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly("GALAXY_SEEKER", "ATLAS_SEEKER", "ATLAS_FINDER");
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("ATLAS_FINDER");
    }

    @Test
    void grantTitlesForHighlightAwardsGalaxyFinderForSolarShotGalaxyShotCombo() {
        GameHighlightState highlight = highlightState("SOLAR_SHOT,GALAXY_SHOT");

        List<AchievementTitle> unlockedTitles =
            achievementTitleService.grantTitlesForHighlight(highlight, AchievementTitleSourceType.HIGHLIGHT);

        assertThat(earnedCodes()).containsExactlyInAnyOrder("SOLAR_SEEKER", "GALAXY_SEEKER", "GALAXY_FINDER");
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly("SOLAR_SEEKER", "GALAXY_SEEKER", "GALAXY_FINDER");
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("GALAXY_FINDER");
    }

    @Test
    void grantTitlesForHighlightAwardsSolarFinderForMoonshotSolarShotCombo() {
        GameHighlightState highlight = highlightState("MOONSHOT,SOLAR_SHOT");

        List<AchievementTitle> unlockedTitles =
            achievementTitleService.grantTitlesForHighlight(highlight, AchievementTitleSourceType.HIGHLIGHT);

        assertThat(earnedCodes()).containsExactlyInAnyOrder("MOON_SEEKER", "SOLAR_SEEKER", "SOLAR_FINDER");
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly("MOON_SEEKER", "SOLAR_SEEKER", "SOLAR_FINDER");
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("SOLAR_FINDER");
    }

    @Test
    void grantTitlesForSeasonResultAwardsCumulativeTierTitles() {
        GameSeasonResult currentResult = seasonResult("LEGEND", 100L);
        when(gameSeasonResultRepository.findByUserIdAndRegionCode(7L, "KR")).thenReturn(List.of(
            seasonResult("DIAMOND", 1L),
            seasonResult("DIAMOND", 2L),
            seasonResult("DIAMOND", 3L),
            seasonResult("DIAMOND", 4L),
            seasonResult("DIAMOND", 5L),
            seasonResult("MASTER", 6L),
            seasonResult("MASTER", 7L),
            seasonResult("MASTER", 8L),
            seasonResult("MASTER", 9L),
            seasonResult("MASTER", 10L),
            seasonResult("MASTER", 11L),
            seasonResult("MASTER", 12L),
            seasonResult("MASTER", 13L),
            seasonResult("MASTER", 14L),
            seasonResult("MASTER", 15L),
            seasonResult("LEGEND", 16L),
            seasonResult("LEGEND", 17L),
            seasonResult("LEGEND", 18L),
            seasonResult("LEGEND", 19L),
            seasonResult("LEGEND", 20L),
            seasonResult("LEGEND", 21L),
            seasonResult("LEGEND", 22L),
            seasonResult("LEGEND", 23L),
            seasonResult("LEGEND", 24L)
        ));

        List<AchievementTitle> unlockedTitles = achievementTitleService.grantTitlesForSeasonResult(currentResult);

        assertThat(earnedCodes()).containsExactlyInAnyOrder(
            "DIAMOND_SEEKER",
            "DIAMOND_FINDER",
            "MASTER_FINDER",
            "MASTER_WALKER",
            "LEGEND_WALKER",
            "LEGEND_SNIPER"
        );
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly(
                "DIAMOND_SEEKER",
                "DIAMOND_FINDER",
                "MASTER_FINDER",
                "MASTER_WALKER",
                "LEGEND_WALKER",
                "LEGEND_SNIPER"
            );
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("LEGEND_SNIPER");
    }

    @Test
    void grantTitlesForSeasonResultDoesNotAwardMasterWalkerOrLegendSniperBeforeTenFinishes() {
        GameSeasonResult currentResult = seasonResult("LEGEND", 100L);
        when(gameSeasonResultRepository.findByUserIdAndRegionCode(7L, "KR")).thenReturn(List.of(
            seasonResult("MASTER", 1L),
            seasonResult("MASTER", 2L),
            seasonResult("MASTER", 3L),
            seasonResult("MASTER", 4L),
            seasonResult("MASTER", 5L),
            seasonResult("LEGEND", 6L),
            seasonResult("LEGEND", 7L),
            seasonResult("LEGEND", 8L),
            seasonResult("LEGEND", 9L)
        ));

        List<AchievementTitle> unlockedTitles = achievementTitleService.grantTitlesForSeasonResult(currentResult);

        assertThat(earnedCodes()).containsExactlyInAnyOrder("MASTER_FINDER", "LEGEND_WALKER");
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly("MASTER_FINDER", "LEGEND_WALKER");
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("LEGEND_WALKER");
    }

    @Test
    void grantTitlesForHighlightsDoesNotCombineSeparateHighlightsIntoSolarWalker() {
        List<AchievementTitle> unlockedTitles = achievementTitleService.grantTitlesForHighlights(
            user(),
            season(),
            List.of(
                highlightState("SNIPE"),
                highlightState("MOONSHOT,SOLAR_SHOT")
            ),
            AchievementTitleSourceType.HIGHLIGHT
        );

        assertThat(earnedCodes()).containsExactlyInAnyOrder(
            "SNIPE_SEEKER",
            "MOON_SEEKER",
            "SOLAR_SEEKER",
            "SOLAR_FINDER"
        );
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly(
                "SNIPE_SEEKER",
                "MOON_SEEKER",
                "SOLAR_SEEKER",
                "SOLAR_FINDER"
            );
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("SOLAR_FINDER");
    }

    private List<String> earnedCodes() {
        return userTitles.stream()
            .map(userTitle -> userTitle.getTitle().getCode())
            .toList();
    }

    private GameHighlightState highlightState(String strategyTags) {
        GameHighlightState state = new GameHighlightState();
        ReflectionTestUtils.setField(state, "id", 300L);
        state.setUser(user());
        state.setSeason(season());
        state.setStrategyTags(strategyTags);
        return state;
    }

    private AppUser user() {
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setDisplayName("Atlas User");
        return user;
    }

    private GameSeason season() {
        GameSeason season = new GameSeason();
        ReflectionTestUtils.setField(season, "id", 1L);
        season.setRegionCode("KR");
        season.setName("Season");
        season.setStatus(SeasonStatus.ACTIVE);
        return season;
    }

    private GameSeasonResult seasonResult(String finalTierCode, Long seasonId) {
        GameSeasonResult result = new GameSeasonResult();
        result.setUser(user());
        result.setSeason(season(seasonId));
        result.setRegionCode("KR");
        result.setFinalTierCode(finalTierCode);
        return result;
    }

    private GameSeason season(Long id) {
        GameSeason season = season();
        ReflectionTestUtils.setField(season, "id", id);
        return season;
    }
}
