package com.yongsoo.youtubeatlasbackend.game;

import java.time.Instant;
import java.util.List;

import com.yongsoo.youtubeatlasbackend.game.api.GameHighlightResponse;
import com.yongsoo.youtubeatlasbackend.game.api.GameNotificationResponse;

final class GameNotificationFactory {

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
        long highlightScore = GameService.calculateProjectedPositionHighlightScore(
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
                highlightScore,
                createdAt,
                strategyType
            ))
            .toList();
    }

    static List<GameNotificationResponse> fromTierPromotion(
        GamePosition position,
        GameSeasonCoinTier tier,
        long highlightScore,
        Instant createdAt
    ) {
        if (position == null || tier == null) {
            return List.of();
        }

        return List.of(new GameNotificationResponse(
            resolveTierPromotionId(position.getSeason().getId(), tier.getTierCode()),
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
        long highlightScore,
        Instant createdAt,
        GameStrategyType strategyType
    ) {
        return new GameNotificationResponse(
            resolveId(position.getId(), strategyType),
            strategyType.name(),
            resolveTitle(strategyType),
            resolveMessage(position.getBuyRank(), currentRank, rankDiff, profitRatePercent, strategyType),
            position.getId(),
            position.getVideoId(),
            position.getTitle(),
            position.getChannelTitle(),
            position.getThumbnailUrl(),
            strategyTags,
            highlightScore,
            null,
            createdAt,
            true
        );
    }

    private static String resolveId(Long positionId, GameStrategyType strategyType) {
        return "game-" + positionId + "-" + strategyType.name();
    }

    private static String resolveTierPromotionId(Long seasonId, String tierCode) {
        return "tier-promotion-" + seasonId + "-" + tierCode;
    }

    private static String resolveTitle(GameStrategyType strategyType) {
        return switch (strategyType) {
            case MOONSHOT -> "문샷 적중";
            case BIG_CASHOUT -> "빅 캐시아웃";
            case SMALL_CASHOUT -> "스몰 캐시아웃";
            case SNIPE -> "스나이프 성공";
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
            case MOONSHOT -> buyRank + "위에서 잡은 영상이 " + highlightRank + "위까지 올라왔습니다.";
            case BIG_CASHOUT, SMALL_CASHOUT -> "수익률 " + profitRatePercent + "% 플레이가 기록됐습니다.";
            case SNIPE -> buyRank + "위에서 진입해 " + rankDiff + "계단을 앞질렀습니다.";
        };
    }
}
