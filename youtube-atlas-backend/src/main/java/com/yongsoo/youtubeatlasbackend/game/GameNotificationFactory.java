package com.yongsoo.youtubeatlasbackend.game;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;

final class GameNotificationFactory {

    private static final DecimalFormat PROFIT_RATE_FORMAT = new DecimalFormat("0.#");

    private GameNotificationFactory() {
    }

    static List<GameNotificationResponse> fromHighlight(GameHighlightResponse highlight) {
        return fromHighlight(highlight, strategyScoreMap(highlight), null);
    }

    static List<GameNotificationResponse> fromHighlight(
        GameHighlightResponse highlight,
        long scoreGain,
        List<GameStrategyType> notificationStrategyTags
    ) {
        if (scoreGain <= 0L) {
            return List.of();
        }

        List<GameStrategyType> scoreTags = notificationStrategyTags != null
            ? notificationStrategyTags
            : highlight != null ? highlight.strategyTags() : List.of();
        return fromHighlight(highlight, strategyScoreMap(scoreTags, scoreGain), notificationStrategyTags);
    }

    static List<GameNotificationResponse> fromHighlight(
        GameHighlightResponse highlight,
        Map<GameStrategyType, Long> scoreGainByStrategyTag,
        List<GameStrategyType> notificationStrategyTags
    ) {
        if (highlight == null) {
            return List.of();
        }

        if (highlight.strategyTags() == null || highlight.strategyTags().isEmpty()) {
            return List.of();
        }

        if (scoreGainByStrategyTag != null && scoreGainByStrategyTag.isEmpty()) {
            return List.of();
        }

        List<GameStrategyType> strategyTags = resolveNotificationStrategyTags(
            highlight.strategyTags(),
            notificationStrategyTags
        );
        if (strategyTags.isEmpty()) {
            return List.of();
        }

        return strategyTags.stream()
            .filter(strategyType -> resolveScoreGain(strategyType, scoreGainByStrategyTag, 0L) > 0L)
            .map(strategyType -> fromHighlight(
                highlight,
                resolveScoreGain(strategyType, scoreGainByStrategyTag, 0L),
                strategyTags,
                strategyType
            ))
            .toList();
    }

    static List<GameNotificationResponse> fromPositionSnapshot(
        GamePosition position,
        int currentRank,
        long currentPricePoints,
        Instant createdAt
    ) {
        return fromPositionSnapshot(position, currentRank, currentPricePoints, createdAt, null);
    }

    static List<GameNotificationResponse> fromPositionSnapshot(
        GamePosition position,
        int currentRank,
        long currentPricePoints,
        Instant createdAt,
        List<GameStrategyType> notificationStrategyTags
    ) {
        return fromPositionSnapshot(position, currentRank, currentPricePoints, createdAt, notificationStrategyTags, (Long) null);
    }

    static List<GameNotificationResponse> fromPositionSnapshot(
        GamePosition position,
        int currentRank,
        long currentPricePoints,
        Instant createdAt,
        List<GameStrategyType> notificationStrategyTags,
        Long notificationHighlightScore
    ) {
        return fromPositionSnapshot(
            position,
            currentRank,
            currentPricePoints,
            createdAt,
            notificationStrategyTags,
            notificationHighlightScore != null && notificationStrategyTags != null
                ? strategyScoreMap(notificationStrategyTags, notificationHighlightScore)
                : null
        );
    }

    static List<GameNotificationResponse> fromPositionSnapshot(
        GamePosition position,
        int currentRank,
        long currentPricePoints,
        Instant createdAt,
        List<GameStrategyType> notificationStrategyTags,
        Map<GameStrategyType, Long> notificationHighlightScoreByStrategyTag
    ) {
        long profitPoints = GamePointCalculator.calculateProfitPoints(position.getStakePoints(), currentPricePoints);
        Double profitRatePercent = GameStrategyResolver.calculateProfitRatePercent(
            position.getStakePoints(),
            currentPricePoints
        );
        List<GameStrategyType> strategyTags = GameStrategyResolver.resolveAchievedPositionStrategyTags(
            position,
            currentRank,
            currentPricePoints
        );

        if (strategyTags.isEmpty()) {
            return List.of();
        }

        List<GameStrategyType> strategyTagsToNotify = resolveNotificationStrategyTags(
            strategyTags,
            notificationStrategyTags
        );
        if (strategyTagsToNotify.isEmpty()) {
            return List.of();
        }

        int rankDiff = position.getBuyRank() - currentRank;
        Map<GameStrategyType, Long> highlightScoreByStrategyTag = notificationHighlightScoreByStrategyTag != null
            ? notificationHighlightScoreByStrategyTag
            : strategyScoreMap(projectedHighlight(
                position.getBuyRank(),
                rankDiff,
                profitRatePercent,
                profitPoints,
                strategyTags
            ));
        if (highlightScoreByStrategyTag.isEmpty()) {
            return List.of();
        }

        return strategyTagsToNotify.stream()
            .filter(strategyType -> resolveScoreGain(
                strategyType,
                highlightScoreByStrategyTag,
                0L
            ) > 0L)
            .map(strategyType -> fromPositionSnapshot(
                position,
                currentRank,
                rankDiff,
                profitRatePercent,
                strategyTagsToNotify,
                resolveScoreGain(strategyType, highlightScoreByStrategyTag, 0L),
                createdAt,
                strategyType
            ))
            .toList();
    }

    static List<GameNotificationResponse> fromTierPromotion(
        GamePosition position,
        GameSeasonTier tier,
        long highlightScore,
        Instant createdAt
    ) {
        if (position == null || tier == null) {
            return List.of();
        }

        return List.of(new GameNotificationResponse(
            resolveTierPromotionId(position.getSeason().getId(), tier.getTierCode(), position.getId()),
            GameNotificationEventType.TIER_PROMOTION,
            "TIER_PROMOTION",
            "티어 승급",
            tier.getDisplayName() + " 티어에 도달했습니다. 축하합니다!",
            position.getId(),
            position.getVideoId(),
            tier.getDisplayName() + " 티어 달성",
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            List.of(),
            highlightScore,
            null,
            null,
            null,
            null,
            createdAt,
            true
        ));
    }

    static List<GameNotificationResponse> fromUnlockedTitles(List<AchievementTitle> titles, Instant createdAt) {
        if (titles == null || titles.isEmpty()) {
            return List.of();
        }

        Instant notificationTime = createdAt != null ? createdAt : Instant.now();
        return titles.stream()
            .map(title -> new GameNotificationResponse(
                resolveTitleUnlockId(title.getCode()),
                GameNotificationEventType.TITLE_UNLOCK,
                "TITLE_UNLOCK",
                "새 칭호 획득",
                title.getDisplayName() + " 칭호를 획득했습니다.",
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                title.getCode(),
                title.getDisplayName(),
                title.getGrade(),
                null,
                notificationTime,
                false
            ))
            .toList();
    }

    private static GameNotificationResponse fromHighlight(
        GameHighlightResponse highlight,
        long scoreGain,
        List<GameStrategyType> strategyTags,
        GameStrategyType strategyType
    ) {
        return new GameNotificationResponse(
            resolveId(highlight.positionId(), strategyType),
            GameNotificationEventType.TIER_SCORE_GAIN,
            strategyType.name(),
            resolveTitle(strategyType),
            resolveMessage(highlight.buyRank(), highlight.highlightRank(), highlight.rankDiff(), highlight.profitRatePercent(), strategyType),
            highlight.positionId(),
            highlight.videoId(),
            highlight.videoTitle(),
            highlight.channelTitle(),
            highlight.thumbnailUrl(),
            strategyTags,
            scoreGain,
            null,
            null,
            null,
            null,
            highlight.createdAt(),
            true
        );
    }

    private static GameNotificationResponse fromPositionSnapshot(
        GamePosition position,
        int currentRank,
        int rankDiff,
        Double profitRatePercent,
        List<GameStrategyType> strategyTags,
        long projectedHighlightScore,
        Instant createdAt,
        GameStrategyType strategyType
    ) {
        return new GameNotificationResponse(
            resolveProjectedId(position.getId(), strategyType),
            GameNotificationEventType.PROJECTED_HIGHLIGHT,
            strategyType.name(),
            resolveProjectedTitle(strategyType),
            resolveProjectedMessage(position.getBuyRank(), currentRank, rankDiff, profitRatePercent, strategyType),
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            strategyTags,
            projectedHighlightScore,
            null,
            null,
            null,
            null,
            createdAt,
            false
        );
    }

    private static String resolveId(Long positionId, GameStrategyType strategyType) {
        return "game-" + positionId + "-" + strategyType.name();
    }

    private static String resolveTierPromotionId(Long seasonId, String tierCode, Long positionId) {
        return "tier-promotion-" + seasonId + "-" + tierCode + "-" + positionId;
    }

    private static String resolveProjectedId(Long positionId, GameStrategyType strategyType) {
        return "projected-game-" + positionId + "-" + strategyType.name();
    }

    private static String resolveTitleUnlockId(String titleCode) {
        return "title-unlock-" + titleCode;
    }

    private static List<GameStrategyType> resolveNotificationStrategyTags(
        List<GameStrategyType> strategyTags,
        List<GameStrategyType> notificationStrategyTags
    ) {
        if (notificationStrategyTags == null) {
            return strategyTags;
        }

        if (notificationStrategyTags.isEmpty()) {
            return List.of();
        }

        return strategyTags.stream()
            .filter(notificationStrategyTags::contains)
            .toList();
    }

    private static long resolveScoreGain(
        GameStrategyType strategyType,
        Map<GameStrategyType, Long> scoreGainByStrategyTag,
        Long fallbackScore
    ) {
        if (scoreGainByStrategyTag == null) {
            return fallbackScore != null ? fallbackScore : 0L;
        }

        return scoreGainByStrategyTag.getOrDefault(strategyType, 0L);
    }

    private static Map<GameStrategyType, Long> strategyScoreMap(
        List<GameStrategyType> strategyTags,
        long score
    ) {
        if (strategyTags == null || strategyTags.isEmpty()) {
            return Map.of();
        }

        return strategyTags.stream()
            .collect(java.util.stream.Collectors.toMap(
                strategyType -> strategyType,
                strategyType -> score,
                (left, right) -> left
            ));
    }

    private static Map<GameStrategyType, Long> strategyScoreMap(GameHighlightResponse highlight) {
        if (highlight == null || highlight.strategyTags() == null || highlight.strategyTags().isEmpty()) {
            return Map.of();
        }

        return highlight.strategyTags().stream()
            .collect(java.util.stream.Collectors.toMap(
                strategyType -> strategyType,
                strategyType -> GameService.calculateStrategyHighlightScore(strategyType, highlight),
                (left, right) -> left
            ));
    }

    private static GameHighlightResponse projectedHighlight(
        Integer buyRank,
        Integer rankDiff,
        Double profitRatePercent,
        Long profitPoints,
        List<GameStrategyType> achievedStrategyTags
    ) {
        return new GameHighlightResponse(
            "projected",
            achievedStrategyTags.get(0).name(),
            "Projected highlight",
            "Projected highlight",
            0L,
            "projected",
            "Projected highlight",
            "Projected channel",
            "",
            buyRank != null ? buyRank : 0,
            buyRank != null && rankDiff != null ? buyRank - rankDiff : null,
            null,
            rankDiff,
            GamePointCalculator.QUANTITY_SCALE,
            0L,
            null,
            profitPoints,
            profitRatePercent,
            achievedStrategyTags,
            0L,
            PositionStatus.OPEN.name(),
            Instant.EPOCH
        );
    }

    private static String resolveTitle(GameStrategyType strategyType) {
        return switch (strategyType) {
            case ATLAS_SHOT -> "아틀라스 샷 기록";
            case GALAXY_SHOT -> "갤럭시 샷 기록";
            case SOLAR_SHOT -> "솔라 샷 기록";
            case MOONSHOT -> "문샷 기록";
            case BIG_CASHOUT -> "빅 캐시아웃 기록";
            case SMALL_CASHOUT -> "스몰 캐시아웃 기록";
            case SNIPE -> "스나이프 기록";
        };
    }

    private static String resolveProjectedTitle(GameStrategyType strategyType) {
        return switch (strategyType) {
            case ATLAS_SHOT -> "아틀라스 샷 예상";
            case GALAXY_SHOT -> "갤럭시 샷 예상";
            case SOLAR_SHOT -> "솔라 샷 예상";
            case MOONSHOT -> "문샷 예상";
            case BIG_CASHOUT -> "빅 캐시아웃 예상";
            case SMALL_CASHOUT -> "스몰 캐시아웃 예상";
            case SNIPE -> "스나이프 예상";
        };
    }

    private static String resolveMessage(
        Integer buyRank,
        Integer highlightRank,
        Integer rankDiff,
        Double profitRatePercent,
        GameStrategyType strategyType
    ) {
        return switch (strategyType) {
            case ATLAS_SHOT -> buyRank + "위에서 잡은 영상이 " + highlightRank + "위까지 올라왔습니다.";
            case GALAXY_SHOT -> buyRank + "위에서 잡은 영상이 " + highlightRank + "위까지 올라왔습니다.";
            case SOLAR_SHOT -> buyRank + "위에서 잡은 영상이 " + highlightRank + "위까지 올라왔습니다.";
            case MOONSHOT -> buyRank + "위에서 잡은 영상이 " + highlightRank + "위까지 올라왔습니다.";
            case BIG_CASHOUT, SMALL_CASHOUT -> "수익률 " + formatProfitRatePercent(profitRatePercent) + "% 플레이가 기록됐습니다.";
            case SNIPE -> buyRank + "위에서 진입해 " + rankDiff + "계단을 앞질렀습니다.";
        };
    }

    private static String resolveProjectedMessage(
        Integer buyRank,
        Integer highlightRank,
        Integer rankDiff,
        Double profitRatePercent,
        GameStrategyType strategyType
    ) {
        return resolveMessage(buyRank, highlightRank, rankDiff, profitRatePercent, strategyType)
            + " 매도 시 하이라이트 점수로 확정됩니다.";
    }

    private static String formatProfitRatePercent(Double profitRatePercent) {
        if (profitRatePercent == null || !Double.isFinite(profitRatePercent)) {
            return "0";
        }

        synchronized (PROFIT_RATE_FORMAT) {
            return PROFIT_RATE_FORMAT.format(profitRatePercent);
        }
    }
}
