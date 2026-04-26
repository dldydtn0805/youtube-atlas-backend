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
        appUserRepository = org.mockito.Mockito.mock(AppUserRepository.class);
        titles = new ArrayList<>();
        userTitles = new ArrayList<>();
        Clock fixedClock = Clock.fixed(Instant.parse("2026-04-01T06:00:00Z"), ZoneOffset.UTC);

        achievementTitleService = new AchievementTitleService(
            achievementTitleRepository,
            userAchievementTitleRepository,
            userAchievementTitleSettingRepository,
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
        GameHighlightState highlight = highlightState("ATLAS_SHOT,SOLAR_SHOT,MOONSHOT,SNIPE");

        List<AchievementTitle> unlockedTitles =
            achievementTitleService.grantTitlesForHighlight(highlight, AchievementTitleSourceType.HIGHLIGHT);

        assertThat(earnedCodes()).containsExactlyInAnyOrder(
            "SNIPE_SEEKER",
            "MOON_SEEKER",
            "MOON_FINDER",
            "SOLAR_SEEKER",
            "SOLAR_FINDER",
            "SOLAR_WALKER",
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
                "ATLAS_SEEKER",
                "MOON_FINDER",
                "SOLAR_FINDER",
                "ATLAS_FINDER",
                "SOLAR_WALKER",
                "ATLAS_WALKER",
                "ATLAS_SNIPER"
            );
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("ATLAS_SNIPER");
        assertThat(setting.getSelectionMode()).isEqualTo(AchievementTitleSelectionMode.AUTO);
    }

    @Test
    void grantTitlesForHighlightAwardsAtlasFinderForAtlasShotSolarShotCombo() {
        GameHighlightState highlight = highlightState("ATLAS_SHOT,SOLAR_SHOT");

        List<AchievementTitle> unlockedTitles =
            achievementTitleService.grantTitlesForHighlight(highlight, AchievementTitleSourceType.HIGHLIGHT);

        assertThat(earnedCodes()).containsExactlyInAnyOrder("SOLAR_SEEKER", "ATLAS_SEEKER", "ATLAS_FINDER");
        assertThat(unlockedTitles).extracting(AchievementTitle::getCode)
            .containsExactly("SOLAR_SEEKER", "ATLAS_SEEKER", "ATLAS_FINDER");
        assertThat(setting.getSelectedTitle().getCode()).isEqualTo("ATLAS_FINDER");
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
}
