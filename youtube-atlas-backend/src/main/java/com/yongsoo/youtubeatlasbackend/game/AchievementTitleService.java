package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.yongsoo.youtubeatlasbackend.auth.AppUser;
import com.yongsoo.youtubeatlasbackend.auth.AppUserRepository;
import com.yongsoo.youtubeatlasbackend.auth.AuthenticatedUser;
import com.yongsoo.youtubeatlasbackend.game.api.AchievementTitleCollectionResponse;
import com.yongsoo.youtubeatlasbackend.game.api.AchievementTitleResponse;
import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

@Service
public class AchievementTitleService {

    private static final List<DefaultAchievementTitle> DEFAULT_TITLES = List.of(
        new DefaultAchievementTitle(
            "ATLAS_SEEKER",
            "Atlas Seeker",
            "A. Seeker",
            AchievementTitleGrade.NORMAL,
            "50위 밖에서 잡은 영상이 10위 안까지 올라온 아틀라스 샷 달성자입니다.",
            10
        ),
        new DefaultAchievementTitle(
            "MOON_SEEKER",
            "Moon Seeker",
            "M. Seeker",
            AchievementTitleGrade.NORMAL,
            "100위 밖에서 발견한 영상이 50위 안까지 올라온 문샷 달성자입니다.",
            20
        ),
        new DefaultAchievementTitle(
            "SNIPE_SEEKER",
            "Snipe Seeker",
            "S. Seeker",
            AchievementTitleGrade.NORMAL,
            "150위 밖에서 진입해 100위 안까지 끌어올린 스나이프 달성자입니다.",
            30
        ),
        new DefaultAchievementTitle(
            "ATLAS_FINDER",
            "Atlas Finder",
            "A. Finder",
            AchievementTitleGrade.RARE,
            "100위 밖에서 발견한 영상이 10위 안까지 올라온 아틀라스 샷 + 문샷 복합 달성자입니다.",
            35
        ),
        new DefaultAchievementTitle(
            "MOON_FINDER",
            "Moon Finder",
            "M. Finder",
            AchievementTitleGrade.RARE,
            "150위 밖에서 진입해 50위 안까지 끌어올린 문샷 + 스나이프 복합 달성자입니다.",
            36
        ),
        new DefaultAchievementTitle(
            "ATLAS_SNIPER",
            "Atlas Sniper",
            "A. Sniper",
            AchievementTitleGrade.SUPER,
            "150위 밖에서 잡은 영상이 10위 안까지 올라온 복합 하이라이트 달성자입니다.",
            40
        )
    );

    private final AchievementTitleRepository achievementTitleRepository;
    private final UserAchievementTitleRepository userAchievementTitleRepository;
    private final UserAchievementTitleSettingRepository userAchievementTitleSettingRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;

    public AchievementTitleService(
        AchievementTitleRepository achievementTitleRepository,
        UserAchievementTitleRepository userAchievementTitleRepository,
        UserAchievementTitleSettingRepository userAchievementTitleSettingRepository,
        AppUserRepository appUserRepository,
        Clock clock
    ) {
        this.achievementTitleRepository = achievementTitleRepository;
        this.userAchievementTitleRepository = userAchievementTitleRepository;
        this.userAchievementTitleSettingRepository = userAchievementTitleSettingRepository;
        this.appUserRepository = appUserRepository;
        this.clock = clock;
    }

    @Transactional
    public AchievementTitleCollectionResponse getMyTitles(AuthenticatedUser authenticatedUser) {
        AppUser user = requireUser(authenticatedUser.id());
        List<AchievementTitle> titles = getOrCreateTitles();
        Map<String, UserAchievementTitle> earnedByCode = userAchievementTitleRepository
            .findByUserIdAndRevokedAtIsNull(user.getId())
            .stream()
            .collect(Collectors.toMap(earned -> earned.getTitle().getCode(), Function.identity()));
        SelectedAchievementTitleResponse selectedTitle = resolveSelectedTitle(user.getId(), earnedByCode.values());

        List<AchievementTitleResponse> responses = titles.stream()
            .filter(title -> Boolean.TRUE.equals(title.getEnabled()))
            .map(title -> toAchievementTitleResponse(
                title,
                earnedByCode.get(title.getCode()),
                selectedTitle != null && selectedTitle.code().equals(title.getCode())
            ))
            .toList();

        return new AchievementTitleCollectionResponse(selectedTitle, responses);
    }

    @Transactional
    public AchievementTitleCollectionResponse updateSelectedTitle(AuthenticatedUser authenticatedUser, String titleCode) {
        AppUser user = requireUser(authenticatedUser.id());
        UserAchievementTitleSetting setting = findOrCreateSetting(user);

        if (!StringUtils.hasText(titleCode)) {
            setting.setSelectedTitle(null);
            setting.setSelectionMode(AchievementTitleSelectionMode.MANUAL);
            setting.setUpdatedAt(Instant.now(clock));
            userAchievementTitleSettingRepository.save(setting);
            return getMyTitles(authenticatedUser);
        }

        AchievementTitle title = achievementTitleRepository.findByCode(titleCode.trim())
            .filter(candidate -> Boolean.TRUE.equals(candidate.getEnabled()))
            .orElseThrow(() -> new IllegalArgumentException("선택할 수 없는 칭호입니다."));
        userAchievementTitleRepository.findActiveByUserIdAndTitleCode(user.getId(), title.getCode())
            .orElseThrow(() -> new IllegalArgumentException("획득한 칭호만 대표 칭호로 설정할 수 있습니다."));

        setting.setSelectedTitle(title);
        setting.setSelectionMode(AchievementTitleSelectionMode.MANUAL);
        setting.setUpdatedAt(Instant.now(clock));
        userAchievementTitleSettingRepository.save(setting);

        return getMyTitles(authenticatedUser);
    }

    @Transactional
    public List<AchievementTitle> grantTitlesForHighlight(GameHighlightState highlightState, AchievementTitleSourceType sourceType) {
        if (highlightState == null || highlightState.getUser() == null || highlightState.getSeason() == null) {
            return List.of();
        }

        return grantTitles(
            highlightState.getUser(),
            highlightState.getSeason(),
            highlightState.getId(),
            resolveEarnedCodes(parseStrategyTags(highlightState.getStrategyTags())),
            sourceType
        );
    }

    @Transactional
    public List<AchievementTitle> grantTitlesForHighlights(
        AppUser user,
        GameSeason season,
        List<GameHighlightState> highlightStates,
        AchievementTitleSourceType sourceType
    ) {
        if (user == null || season == null || highlightStates == null || highlightStates.isEmpty()) {
            return List.of();
        }

        Set<String> codes = highlightStates.stream()
            .flatMap(state -> resolveEarnedCodes(parseStrategyTags(state.getStrategyTags())).stream())
            .collect(Collectors.toSet());
        return grantTitles(user, season, null, codes, sourceType);
    }

    @Transactional(readOnly = true)
    public Map<Long, SelectedAchievementTitleResponse> findSelectedTitlesByUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        List<UserAchievementTitle> earnedTitles = userAchievementTitleRepository.findByUserIdInAndRevokedAtIsNull(userIds);
        Map<Long, List<UserAchievementTitle>> earnedByUserId = earnedTitles.stream()
            .collect(Collectors.groupingBy(earned -> earned.getUser().getId()));
        Map<Long, UserAchievementTitleSetting> settingsByUserId = userAchievementTitleSettingRepository
            .findByUserIdIn(userIds)
            .stream()
            .collect(Collectors.toMap(setting -> setting.getUser().getId(), Function.identity()));

        Map<Long, SelectedAchievementTitleResponse> selectedByUserId = new LinkedHashMap<>();
        for (Long userId : userIds) {
            SelectedAchievementTitleResponse selected = resolveSelectedTitle(
                settingsByUserId.get(userId),
                earnedByUserId.getOrDefault(userId, List.of())
            );
            if (selected != null) {
                selectedByUserId.put(userId, selected);
            }
        }
        return selectedByUserId;
    }

    private List<AchievementTitle> grantTitles(
        AppUser user,
        GameSeason season,
        Long sourceHighlightStateId,
        Collection<String> titleCodes,
        AchievementTitleSourceType sourceType
    ) {
        if (titleCodes == null || titleCodes.isEmpty()) {
            return List.of();
        }

        Map<String, AchievementTitle> titlesByCode = getOrCreateTitles().stream()
            .filter(title -> Boolean.TRUE.equals(title.getEnabled()))
            .filter(title -> titleCodes.contains(title.getCode()))
            .collect(Collectors.toMap(AchievementTitle::getCode, Function.identity()));
        if (titlesByCode.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now(clock);
        List<UserAchievementTitle> createdTitles = new ArrayList<>();
        for (AchievementTitle title : titlesByCode.values()) {
            if (userAchievementTitleRepository.existsActiveByUserIdAndTitleCode(user.getId(), title.getCode())) {
                continue;
            }

            UserAchievementTitle userTitle = new UserAchievementTitle();
            userTitle.setUser(user);
            userTitle.setTitle(title);
            userTitle.setSeason(season);
            userTitle.setSourceHighlightStateId(sourceHighlightStateId);
            userTitle.setSourceType(sourceType != null ? sourceType : AchievementTitleSourceType.HIGHLIGHT);
            userTitle.setEarnedAt(now);
            try {
                createdTitles.add(userAchievementTitleRepository.save(userTitle));
            } catch (DataIntegrityViolationException ignored) {
                // A concurrent grant can win the unique constraint; the title is already earned.
            }
        }

        if (!createdTitles.isEmpty()) {
            applyAutoSelection(user);
        }

        return createdTitles.stream()
            .map(UserAchievementTitle::getTitle)
            .filter(Objects::nonNull)
            .sorted(
                Comparator.comparingInt((AchievementTitle title) -> title.getSortOrder() != null ? title.getSortOrder() : 0)
                    .thenComparing(AchievementTitle::getCode, Comparator.nullsLast(String::compareTo))
            )
            .toList();
    }

    private void applyAutoSelection(AppUser user) {
        List<UserAchievementTitle> earnedTitles = userAchievementTitleRepository.findByUserIdAndRevokedAtIsNull(user.getId());
        AchievementTitle bestTitle = selectBestTitle(earnedTitles);
        if (bestTitle == null) {
            return;
        }

        UserAchievementTitleSetting setting = findOrCreateSetting(user);
        if (setting.getSelectionMode() == AchievementTitleSelectionMode.MANUAL) {
            return;
        }

        setting.setSelectedTitle(bestTitle);
        setting.setSelectionMode(AchievementTitleSelectionMode.AUTO);
        setting.setUpdatedAt(Instant.now(clock));
        userAchievementTitleSettingRepository.save(setting);
    }

    private SelectedAchievementTitleResponse resolveSelectedTitle(
        Long userId,
        Collection<UserAchievementTitle> earnedTitles
    ) {
        return resolveSelectedTitle(
            userAchievementTitleSettingRepository.findByUserId(userId).orElse(null),
            earnedTitles
        );
    }

    private SelectedAchievementTitleResponse resolveSelectedTitle(
        UserAchievementTitleSetting setting,
        Collection<UserAchievementTitle> earnedTitles
    ) {
        if (earnedTitles == null || earnedTitles.isEmpty()) {
            return null;
        }

        Map<String, UserAchievementTitle> earnedByCode = earnedTitles.stream()
            .collect(Collectors.toMap(earned -> earned.getTitle().getCode(), Function.identity(), (left, right) -> left));
        if (setting != null
            && setting.getSelectionMode() == AchievementTitleSelectionMode.MANUAL
            && setting.getSelectedTitle() == null) {
            return null;
        }

        if (setting != null && setting.getSelectedTitle() != null) {
            AchievementTitle selectedTitle = setting.getSelectedTitle();
            if (Boolean.TRUE.equals(selectedTitle.getEnabled()) && earnedByCode.containsKey(selectedTitle.getCode())) {
                return toSelectedTitleResponse(selectedTitle);
            }
        }

        AchievementTitle bestTitle = selectBestTitle(earnedTitles);
        return bestTitle != null ? toSelectedTitleResponse(bestTitle) : null;
    }

    private AchievementTitle selectBestTitle(Collection<UserAchievementTitle> earnedTitles) {
        return earnedTitles.stream()
            .map(UserAchievementTitle::getTitle)
            .filter(Objects::nonNull)
            .filter(title -> Boolean.TRUE.equals(title.getEnabled()))
            .max(
                Comparator.comparingInt((AchievementTitle title) -> gradePriority(title.getGrade()))
                    .thenComparingInt(title -> title.getSortOrder() != null ? title.getSortOrder() : 0)
            )
            .orElse(null);
    }

    private List<AchievementTitle> getOrCreateTitles() {
        Map<String, AchievementTitle> existingByCode = achievementTitleRepository.findAllByOrderBySortOrderAsc().stream()
            .collect(Collectors.toMap(AchievementTitle::getCode, Function.identity()));
        List<AchievementTitle> titlesToCreate = DEFAULT_TITLES.stream()
            .filter(definition -> !existingByCode.containsKey(definition.code()))
            .map(this::createTitle)
            .toList();

        if (!titlesToCreate.isEmpty()) {
            achievementTitleRepository.saveAllAndFlush(titlesToCreate);
        }

        return achievementTitleRepository.findAllByOrderBySortOrderAsc();
    }

    private AchievementTitle createTitle(DefaultAchievementTitle definition) {
        Instant now = Instant.now(clock);
        AchievementTitle title = new AchievementTitle();
        title.setCode(definition.code());
        title.setDisplayName(definition.displayName());
        title.setShortName(definition.shortName());
        title.setGrade(definition.grade());
        title.setDescription(definition.description());
        title.setSortOrder(definition.sortOrder());
        title.setEnabled(true);
        title.setCreatedAt(now);
        title.setUpdatedAt(now);
        return title;
    }

    private UserAchievementTitleSetting findOrCreateSetting(AppUser user) {
        return userAchievementTitleSettingRepository.findByUserId(user.getId())
            .orElseGet(() -> {
                UserAchievementTitleSetting setting = new UserAchievementTitleSetting();
                setting.setUser(user);
                setting.setSelectionMode(AchievementTitleSelectionMode.AUTO);
                setting.setUpdatedAt(Instant.now(clock));
                return userAchievementTitleSettingRepository.save(setting);
            });
    }

    private Set<GameStrategyType> parseStrategyTags(String strategyTags) {
        if (!StringUtils.hasText(strategyTags)) {
            return Set.of();
        }

        return java.util.Arrays.stream(strategyTags.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(GameStrategyType::valueOf)
            .collect(Collectors.toSet());
    }

    private Set<String> resolveEarnedCodes(Set<GameStrategyType> strategyTags) {
        if (strategyTags == null || strategyTags.isEmpty()) {
            return Set.of();
        }

        Set<String> codes = new java.util.HashSet<>();
        if (strategyTags.contains(GameStrategyType.ATLAS_SHOT)) {
            codes.add("ATLAS_SEEKER");
        }

        if (strategyTags.contains(GameStrategyType.MOONSHOT)) {
            codes.add("MOON_SEEKER");
        }

        if (strategyTags.contains(GameStrategyType.SNIPE)) {
            codes.add("SNIPE_SEEKER");
        }

        if (strategyTags.contains(GameStrategyType.ATLAS_SHOT)
            && strategyTags.contains(GameStrategyType.MOONSHOT)) {
            codes.add("ATLAS_FINDER");
        }

        if (strategyTags.contains(GameStrategyType.MOONSHOT)
            && strategyTags.contains(GameStrategyType.SNIPE)) {
            codes.add("MOON_FINDER");
        }

        if (strategyTags.contains(GameStrategyType.ATLAS_SHOT)
            && strategyTags.contains(GameStrategyType.MOONSHOT)
            && strategyTags.contains(GameStrategyType.SNIPE)) {
            codes.add("ATLAS_SNIPER");
        }

        return codes;
    }

    private AchievementTitleResponse toAchievementTitleResponse(
        AchievementTitle title,
        UserAchievementTitle earnedTitle,
        boolean selected
    ) {
        return new AchievementTitleResponse(
            title.getCode(),
            title.getDisplayName(),
            title.getShortName(),
            title.getGrade(),
            title.getDescription(),
            earnedTitle != null,
            selected,
            earnedTitle != null ? earnedTitle.getEarnedAt() : null
        );
    }

    private SelectedAchievementTitleResponse toSelectedTitleResponse(AchievementTitle title) {
        return new SelectedAchievementTitleResponse(
            title.getCode(),
            title.getDisplayName(),
            title.getShortName(),
            title.getGrade(),
            title.getDescription()
        );
    }

    private AppUser requireUser(Long userId) {
        return appUserRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private int gradePriority(AchievementTitleGrade grade) {
        return switch (grade) {
            case NORMAL -> 1;
            case RARE -> 2;
            case SUPER -> 3;
            case ULTIMATE -> 4;
        };
    }

    private record DefaultAchievementTitle(
        String code,
        String displayName,
        String shortName,
        AchievementTitleGrade grade,
        String description,
        int sortOrder
    ) {
    }
}
