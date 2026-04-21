package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameCoinTierService {

    private static final List<DefaultCoinTierDefinition> DEFAULT_TIER_DEFINITIONS = List.of(
        new DefaultCoinTierDefinition("BRONZE", "브론즈", 0L, "season-bronze", "bronze-investor", "bronze", 1),
        new DefaultCoinTierDefinition("SILVER", "실버", 10_000L, "season-silver", "silver-investor", "silver", 2),
        new DefaultCoinTierDefinition("GOLD", "골드", 30_000L, "season-gold", "gold-investor", "gold", 3),
        new DefaultCoinTierDefinition("PLATINUM", "플래티넘", 120_000L, "season-platinum", "platinum-investor", "platinum", 4),
        new DefaultCoinTierDefinition("DIAMOND", "다이아몬드", 600_000L, "season-diamond", "diamond-investor", "diamond", 5),
        new DefaultCoinTierDefinition("MASTER", "마스터", 3_600_000L, "season-master", "master-investor", "master", 6),
        new DefaultCoinTierDefinition("LEGEND", "레전드", 25_200_000L, "season-legend", "legend-investor", "legend", 7)
    );

    private final GameSeasonCoinTierRepository gameSeasonCoinTierRepository;
    private final Clock clock;

    public GameCoinTierService(GameSeasonCoinTierRepository gameSeasonCoinTierRepository, Clock clock) {
        this.gameSeasonCoinTierRepository = gameSeasonCoinTierRepository;
        this.clock = clock;
    }

    @Transactional
    public List<GameSeasonCoinTier> getOrCreateTiers(GameSeason season) {
        return getOrCreateTiers(season, null);
    }

    @Transactional
    public List<GameSeasonCoinTier> getOrCreateTiers(GameSeason season, GameSeason templateSeason) {
        List<GameSeasonCoinTier> existingTiers = gameSeasonCoinTierRepository.findBySeasonIdOrderBySortOrderAsc(season.getId());
        if (!existingTiers.isEmpty()) {
            return existingTiers;
        }

        List<GameSeasonCoinTier> templateTiers = templateSeason != null
            ? gameSeasonCoinTierRepository.findBySeasonIdOrderBySortOrderAsc(templateSeason.getId())
            : List.of();
        Instant now = Instant.now(clock);
        List<GameSeasonCoinTier> tiersToCreate = templateTiers.isEmpty()
            ? createDefaultTiers(season, now)
            : copyTiers(season, templateTiers, now);

        try {
            return gameSeasonCoinTierRepository.saveAllAndFlush(tiersToCreate);
        } catch (DataIntegrityViolationException ignored) {
            return gameSeasonCoinTierRepository.findBySeasonIdOrderBySortOrderAsc(season.getId());
        }
    }

    public GameSeasonCoinTier resolveTier(List<GameSeasonCoinTier> tiers, long coinBalance) {
        return tiers.stream()
            .filter(tier -> coinBalance >= tier.getMinCoinBalance())
            .max(
                Comparator.comparingLong(GameSeasonCoinTier::getMinCoinBalance)
                    .thenComparingInt(GameSeasonCoinTier::getSortOrder)
            )
            .orElseGet(() -> tiers.stream().min(Comparator.comparingInt(GameSeasonCoinTier::getSortOrder)).orElseThrow());
    }

    private List<GameSeasonCoinTier> createDefaultTiers(GameSeason season, Instant now) {
        return DEFAULT_TIER_DEFINITIONS.stream()
            .map(definition -> createTier(
                season,
                definition.tierCode(),
                definition.displayName(),
                definition.minCoinBalance(),
                definition.badgeCode(),
                definition.titleCode(),
                definition.profileThemeCode(),
                definition.sortOrder(),
                now
            ))
            .toList();
    }

    private List<GameSeasonCoinTier> copyTiers(GameSeason season, List<GameSeasonCoinTier> templateTiers, Instant now) {
        return templateTiers.stream()
            .map(tier -> createTier(
                season,
                tier.getTierCode(),
                tier.getDisplayName(),
                tier.getMinCoinBalance(),
                tier.getBadgeCode(),
                tier.getTitleCode(),
                tier.getProfileThemeCode(),
                tier.getSortOrder(),
                now
            ))
            .toList();
    }

    private GameSeasonCoinTier createTier(
        GameSeason season,
        String tierCode,
        String displayName,
        long minCoinBalance,
        String badgeCode,
        String titleCode,
        String profileThemeCode,
        int sortOrder,
        Instant now
    ) {
        GameSeasonCoinTier tier = new GameSeasonCoinTier();
        tier.setSeason(season);
        tier.setTierCode(tierCode);
        tier.setDisplayName(displayName);
        tier.setMinCoinBalance(minCoinBalance);
        tier.setBadgeCode(badgeCode);
        tier.setTitleCode(titleCode);
        tier.setProfileThemeCode(profileThemeCode);
        tier.setSortOrder(sortOrder);
        tier.setCreatedAt(now);
        return tier;
    }

    private record DefaultCoinTierDefinition(
        String tierCode,
        String displayName,
        long minCoinBalance,
        String badgeCode,
        String titleCode,
        String profileThemeCode,
        int sortOrder
    ) {
    }
}
