package com.yongsoo.youtubeatlasbackend.comments;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import org.springframework.stereotype.Service;

import com.yongsoo.youtubeatlasbackend.game.AchievementTitleGrade;
import com.yongsoo.youtubeatlasbackend.game.AchievementTitleService;
import com.yongsoo.youtubeatlasbackend.game.api.SelectedAchievementTitleResponse;

@Service
public class CommentHighlightDecorationService {

    private static final List<WeightedTier> TIER_WEIGHTS = List.of(
        new WeightedTier("BRONZE", 20),
        new WeightedTier("SILVER", 17),
        new WeightedTier("GOLD", 13),
        new WeightedTier("PLATINUM", 18),
        new WeightedTier("DIAMOND", 12),
        new WeightedTier("MASTER", 10),
        new WeightedTier("LEGEND", 10)
    );
    private static final List<WeightedGrade> LOWER_TIER_TITLE_WEIGHTS = List.of(
        new WeightedGrade(AchievementTitleGrade.NORMAL, 75),
        new WeightedGrade(AchievementTitleGrade.RARE, 20),
        new WeightedGrade(AchievementTitleGrade.SUPER, 4),
        new WeightedGrade(AchievementTitleGrade.ULTIMATE, 1)
    );
    private static final List<WeightedGrade> MID_TIER_TITLE_WEIGHTS = List.of(
        new WeightedGrade(AchievementTitleGrade.NORMAL, 35),
        new WeightedGrade(AchievementTitleGrade.RARE, 40),
        new WeightedGrade(AchievementTitleGrade.SUPER, 20),
        new WeightedGrade(AchievementTitleGrade.ULTIMATE, 5)
    );
    private static final List<WeightedGrade> MASTER_TITLE_WEIGHTS = List.of(
        new WeightedGrade(AchievementTitleGrade.NORMAL, 15),
        new WeightedGrade(AchievementTitleGrade.RARE, 35),
        new WeightedGrade(AchievementTitleGrade.SUPER, 35),
        new WeightedGrade(AchievementTitleGrade.ULTIMATE, 15)
    );
    private static final List<WeightedGrade> LEGEND_TITLE_WEIGHTS = List.of(
        new WeightedGrade(AchievementTitleGrade.NORMAL, 5),
        new WeightedGrade(AchievementTitleGrade.RARE, 20),
        new WeightedGrade(AchievementTitleGrade.SUPER, 40),
        new WeightedGrade(AchievementTitleGrade.ULTIMATE, 35)
    );

    private final AchievementTitleService achievementTitleService;

    public CommentHighlightDecorationService(AchievementTitleService achievementTitleService) {
        this.achievementTitleService = achievementTitleService;
    }

    public List<CommentHighlightDecoration> decorate(String videoId, List<CommentHighlight> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }

        List<SelectedAchievementTitleResponse> titles = achievementTitleService.getPublicTitles();
        return comments.stream()
            .map(comment -> decorate(videoId, comment, titles))
            .toList();
    }

    private CommentHighlightDecoration decorate(
        String videoId,
        CommentHighlight comment,
        List<SelectedAchievementTitleResponse> titles
    ) {
        String commentId = comment != null ? comment.id() : null;
        String tierCode = pickTierCode(videoId, commentId);
        return new CommentHighlightDecoration(
            pickTitle(videoId, commentId, tierCode, titles),
            tierCode
        );
    }

    private SelectedAchievementTitleResponse pickTitle(
        String videoId,
        String commentId,
        String tierCode,
        List<SelectedAchievementTitleResponse> titles
    ) {
        if (titles == null || titles.isEmpty()) {
            return null;
        }

        List<SelectedAchievementTitleResponse> candidates = titles.stream()
            .filter(Objects::nonNull)
            .toList();
        WeightedGrade selectedGrade = pickWeighted(
            titleGradeWeights(tierCode).stream()
                .filter(grade -> containsGrade(candidates, grade.grade()))
                .toList(),
            stableHash("title-grade", videoId, commentId),
            WeightedGrade::weight
        );
        if (selectedGrade == null) {
            return null;
        }

        List<SelectedAchievementTitleResponse> gradeTitles = candidates.stream()
            .filter(title -> title.grade() == selectedGrade.grade())
            .toList();
        return pickUniform(
            gradeTitles,
            stableHash("title-item:" + selectedGrade.grade(), videoId, commentId)
        );
    }

    private String pickTierCode(String videoId, String commentId) {
        return pickWeighted(
            TIER_WEIGHTS,
            stableHash("tier", videoId, commentId),
            WeightedTier::weight
        ).tierCode();
    }

    private SelectedAchievementTitleResponse pickUniform(
        List<SelectedAchievementTitleResponse> titles,
        int hash
    ) {
        if (titles == null || titles.isEmpty()) {
            return null;
        }

        return titles.get(Math.floorMod(hash, titles.size()));
    }

    private <T> T pickWeighted(List<T> options, int hash, ToIntFunction<T> weightExtractor) {
        int totalWeight = options.stream()
            .mapToInt(weightExtractor)
            .filter(weight -> weight > 0)
            .sum();
        if (totalWeight <= 0) {
            return null;
        }

        int ticket = Math.floorMod(hash, totalWeight);
        for (T option : options) {
            int weight = weightExtractor.applyAsInt(option);
            if (weight <= 0) {
                continue;
            }

            ticket -= weight;
            if (ticket < 0) {
                return option;
            }
        }

        return options.getLast();
    }

    private List<WeightedGrade> titleGradeWeights(String tierCode) {
        return switch (tierCode) {
            case "LEGEND" -> LEGEND_TITLE_WEIGHTS;
            case "MASTER" -> MASTER_TITLE_WEIGHTS;
            case "PLATINUM", "DIAMOND" -> MID_TIER_TITLE_WEIGHTS;
            default -> LOWER_TIER_TITLE_WEIGHTS;
        };
    }

    private boolean containsGrade(List<SelectedAchievementTitleResponse> titles, AchievementTitleGrade grade) {
        if (grade == null) {
            return false;
        }

        return titles.stream()
            .anyMatch(title -> title.grade() == grade);
    }

    private int stableHash(String namespace, String videoId, String commentId) {
        int hash = 17;
        hash = 31 * hash + keyPart(namespace).hashCode();
        hash = 31 * hash + keyPart(videoId).hashCode();
        hash = 31 * hash + keyPart(commentId).hashCode();
        return mixHash(hash);
    }

    private int mixHash(int hash) {
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return hash;
    }

    private String keyPart(String value) {
        return value != null ? value : "";
    }

    private record WeightedGrade(
        AchievementTitleGrade grade,
        int weight
    ) {
    }

    private record WeightedTier(
        String tierCode,
        int weight
    ) {
    }
}
