package com.yongsoo.youtubeatlasbackend.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleService;
import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

class CommentHighlightDecorationServiceTest {

    private AchievementTitleService achievementTitleService;
    private CommentHighlightDecorationService decorationService;

    @BeforeEach
    void setUp() {
        achievementTitleService = org.mockito.Mockito.mock(AchievementTitleService.class);
        decorationService = new CommentHighlightDecorationService(achievementTitleService);
    }

    @Test
    void decorateUsesRequestedTierRatioAndBetterTitlesForHigherTiers() {
        when(achievementTitleService.getPublicTitles()).thenReturn(List.of(
            title("NORMAL_TITLE", AchievementTitleGrade.NORMAL),
            title("RARE_TITLE", AchievementTitleGrade.RARE),
            title("SUPER_TITLE", AchievementTitleGrade.SUPER),
            title("ULTIMATE_TITLE", AchievementTitleGrade.ULTIMATE)
        ));
        List<CommentHighlight> comments = IntStream.range(0, 5_000)
            .mapToObj(index -> new CommentHighlight("comment-" + index, "Viewer", "댓글", 1L))
            .toList();

        List<CommentHighlightDecoration> decorations = decorationService.decorate("video-1", comments);

        Map<AchievementTitleGrade, Long> titleCounts = decorations.stream()
            .map(decoration -> decoration.selectedAchievementTitle().grade())
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(count(titleCounts, AchievementTitleGrade.NORMAL))
            .isGreaterThan(count(titleCounts, AchievementTitleGrade.RARE));
        assertThat(count(titleCounts, AchievementTitleGrade.RARE))
            .isGreaterThan(count(titleCounts, AchievementTitleGrade.SUPER));
        assertThat(count(titleCounts, AchievementTitleGrade.SUPER))
            .isGreaterThan(count(titleCounts, AchievementTitleGrade.ULTIMATE));

        Map<String, Long> tierCounts = decorations.stream()
            .map(CommentHighlightDecoration::currentTierCode)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long lowerTierCount = count(tierCounts, "BRONZE") + count(tierCounts, "SILVER") + count(tierCounts, "GOLD");
        long middleTierCount = count(tierCounts, "PLATINUM") + count(tierCounts, "DIAMOND");
        assertThat(lowerTierCount).isBetween(2_300L, 2_700L);
        assertThat(middleTierCount).isBetween(1_300L, 1_700L);
        assertThat(count(tierCounts, "MASTER")).isBetween(400L, 600L);
        assertThat(count(tierCounts, "LEGEND")).isBetween(400L, 600L);

        double lowerTitleScore = averageTitleScore(decorations, "BRONZE", "SILVER", "GOLD");
        double middleTitleScore = averageTitleScore(decorations, "PLATINUM", "DIAMOND");
        double masterTitleScore = averageTitleScore(decorations, "MASTER");
        double legendTitleScore = averageTitleScore(decorations, "LEGEND");
        assertThat(lowerTitleScore).isLessThan(middleTitleScore);
        assertThat(middleTitleScore).isLessThan(masterTitleScore);
        assertThat(masterTitleScore).isLessThan(legendTitleScore);
    }

    @Test
    void decorateIsStableForTheSameVideoAndComment() {
        when(achievementTitleService.getPublicTitles()).thenReturn(List.of(
            title("NORMAL_TITLE", AchievementTitleGrade.NORMAL),
            title("ULTIMATE_TITLE", AchievementTitleGrade.ULTIMATE)
        ));
        CommentHighlight comment = new CommentHighlight("comment-1", "Viewer", "댓글", 1L);

        List<CommentHighlightDecoration> first = decorationService.decorate("video-1", List.of(comment));
        List<CommentHighlightDecoration> second = decorationService.decorate("video-1", List.of(comment));

        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void decorateStillReturnsTierCodeWhenNoPublicTitlesExist() {
        when(achievementTitleService.getPublicTitles()).thenReturn(List.of());

        List<CommentHighlightDecoration> decorations = decorationService.decorate(
            "video-1",
            List.of(new CommentHighlight("comment-1", "Viewer", "댓글", 1L))
        );

        assertThat(decorations).singleElement().satisfies(decoration -> {
            assertThat(decoration.selectedAchievementTitle()).isNull();
            assertThat(decoration.currentTierCode()).isNotBlank();
        });
    }

    private SelectedAchievementTitleResponse title(String code, AchievementTitleGrade grade) {
        return new SelectedAchievementTitleResponse(
            code,
            code,
            code,
            grade,
            code
        );
    }

    private <T> long count(Map<T, Long> counts, T key) {
        return counts.getOrDefault(key, 0L);
    }

    private double averageTitleScore(List<CommentHighlightDecoration> decorations, String... tierCodes) {
        Set<String> selectedTierCodes = Set.of(tierCodes);
        return decorations.stream()
            .filter(decoration -> selectedTierCodes.contains(decoration.currentTierCode()))
            .map(CommentHighlightDecoration::selectedAchievementTitle)
            .mapToInt(title -> gradeScore(title.grade()))
            .average()
            .orElse(0D);
    }

    private int gradeScore(AchievementTitleGrade grade) {
        return switch (grade) {
            case NORMAL -> 1;
            case RARE -> 2;
            case SUPER -> 3;
            case ULTIMATE -> 4;
        };
    }
}
