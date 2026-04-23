package com.yongsoo.youtubeatlasbackend.game;

import java.text.DecimalFormat;
import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;

final class GameNotificationFactory {

    private static final DecimalFormat PROFIT_RATE_FORMAT = new DecimalFormat("0.#");

    private GameNotificationFactory() {
    }

    static List<GameNotificationResponse> fromHighlight(GameHighlightResponse highlight) {
        if (highlight == null) {
            return List.of();
        }

        if (highlight.strategyTags() == null || highlight.strategyTags().isEmpty()) {
            return List.of();
        }

        return highlight.strategyTags().stream()
            .map(strategyType -> fromHighlight(highlight, strategyType))
            .toList();
    }

    static List<GameNotificationResponse> fromPositionSnapshot(
        GamePosition position,
        int currentRank,
        long currentPricePoints,
        Instant createdAt
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

        int rankDiff = position.getBuyRank() - currentRank;
        long projectedHighlightScore = GameService.calculateProjectedPositionHighlightScore(
            position.getBuyRank(),
            rankDiff,
            profitRatePercent,
            profitPoints,
            strategyTags
        );
        return strategyTags.stream()
            .map(strategyType -> fromPositionSnapshot(
                position,
                currentRank,
                rankDiff,
                profitRatePercent,
                strategyTags,
                projectedHighlightScore,
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
            createdAt,
            true
        ));
    }

    private static GameNotificationResponse fromHighlight(GameHighlightResponse highlight, GameStrategyType strategyType) {
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
            highlight.strategyTags(),
            highlight.highlightScore(),
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

    private static String resolveTitle(GameStrategyType strategyType) {
        return switch (strategyType) {
            case ATLAS_SHOT -> "아틀라스 샷 기록";
            case MOONSHOT -> "문샷 기록";
            case BIG_CASHOUT -> "빅 캐시아웃 기록";
            case SMALL_CASHOUT -> "스몰 캐시아웃 기록";
            case SNIPE -> "스나이프 기록";
        };
    }

    private static String resolveProjectedTitle(GameStrategyType strategyType) {
        return switch (strategyType) {
            case ATLAS_SHOT -> "아틀라스 샷 예상";
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
