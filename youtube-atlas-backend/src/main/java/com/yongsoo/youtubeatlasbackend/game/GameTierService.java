package com.yongsoo.youtubeatlasbackend.game;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameTierService {

    private static final List<DefaultTierDefinition> DEFAULT_TIER_DEFINITIONS = List.of(
        new DefaultTierDefinition("BRONZE", "브론즈", 0L, "season-bronze", "bronze-investor", "bronze", 5, 1),
        new DefaultTierDefinition("SILVER", "실버", 5_000L, "season-silver", "silver-investor", "silver", 7, 2),
        new DefaultTierDefinition("GOLD", "골드", 15_000L, "season-gold", "gold-investor", "gold", 10, 3),
        new DefaultTierDefinition("PLATINUM", "플래티넘", 60_000L, "season-platinum", "platinum-investor", "platinum", 12, 4),
        new DefaultTierDefinition("DIAMOND", "다이아몬드", 300_000L, "season-diamond", "diamond-investor", "diamond", 15, 5),
        new DefaultTierDefinition("MASTER", "마스터", 1_800_000L, "season-master", "master-investor", "master", 17, 6),
        new DefaultTierDefinition("LEGEND", "레전드", 12_600_000L, "season-legend", "legend-investor", "legend", 20, 7)
    );

    private final GameSeasonTierRepository gameSeasonTierRepository;
    private final Clock clock;

    public GameTierService(GameSeasonTierRepository gameSeasonTierRepository, Clock clock) {
        this.gameSeasonTierRepository = gameSeasonTierRepository;
        this.clock = clock;
    }

    @Transactional
    public List<GameSeasonTier> getOrCreateTiers(GameSeason season) {
        return getOrCreateTiers(season, null);
    }

    @Transactional
    public List<GameSeasonTier> getOrCreateTiers(GameSeason season, GameSeason templateSeason) {
        List<GameSeasonTier> existingTiers = gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(season.getId());
        if (!existingTiers.isEmpty()) {
            return existingTiers;
        }

        List<GameSeasonTier> templateTiers = templateSeason != null
            ? gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(templateSeason.getId())
            : List.of();
        Instant now = Instant.now(clock);
        List<GameSeasonTier> tiersToCreate = templateTiers.isEmpty()
            ? createDefaultTiers(season, now)
            : copyTiers(season, templateTiers, now);

        try {
            return gameSeasonTierRepository.saveAllAndFlush(tiersToCreate);
        } catch (DataIntegrityViolationException ignored) {
            return gameSeasonTierRepository.findBySeasonIdOrderBySortOrderAsc(season.getId());
        }
    }

    public GameSeasonTier resolveTier(List<GameSeasonTier> tiers, long score) {
        return tiers.stream()
            .filter(tier -> score >= tier.getMinScore())
            .max(
                Comparator.comparingLong(GameSeasonTier::getMinScore)
                    .thenComparingInt(GameSeasonTier::getSortOrder)
            )
            .orElseGet(() -> tiers.stream().min(Comparator.comparingInt(GameSeasonTier::getSortOrder)).orElseThrow());
    }

    private List<GameSeasonTier> createDefaultTiers(GameSeason season, Instant now) {
        return DEFAULT_TIER_DEFINITIONS.stream()
            .map(definition -> createTier(
                season,
                definition.tierCode(),
                definition.displayName(),
                definition.minScore(),
                definition.badgeCode(),
                definition.titleCode(),
                definition.profileThemeCode(),
                definition.inventorySlots(),
                definition.sortOrder(),
                now
            ))
            .toList();
    }

    private List<GameSeasonTier> copyTiers(GameSeason season, List<GameSeasonTier> templateTiers, Instant now) {
        return templateTiers.stream()
            .map(tier -> createTier(
                season,
                tier.getTierCode(),
                tier.getDisplayName(),
                tier.getMinScore(),
                tier.getBadgeCode(),
                tier.getTitleCode(),
                tier.getProfileThemeCode(),
                normalizeInventorySlots(tier.getInventorySlots()),
                tier.getSortOrder(),
                now
            ))
            .toList();
    }

    private GameSeasonTier createTier(
        GameSeason season,
        String tierCode,
        String displayName,
        long minScore,
        String badgeCode,
        String titleCode,
        String profileThemeCode,
        int inventorySlots,
        int sortOrder,
        Instant now
    ) {
        GameSeasonTier tier = new GameSeasonTier();
        tier.setSeason(season);
        tier.setTierCode(tierCode);
        tier.setDisplayName(displayName);
        tier.setMinScore(minScore);
        tier.setBadgeCode(badgeCode);
        tier.setTitleCode(titleCode);
        tier.setProfileThemeCode(profileThemeCode);
        tier.setInventorySlots(inventorySlots);
        tier.setSortOrder(sortOrder);
        tier.setCreatedAt(now);
        return tier;
    }

    private int normalizeInventorySlots(Integer inventorySlots) {
        return inventorySlots == null || inventorySlots < 1 ? 5 : inventorySlots;
    }

    private record DefaultTierDefinition(
        String tierCode,
        String displayName,
        long minScore,
        String badgeCode,
        String titleCode,
        String profileThemeCode,
        int inventorySlots,
        int sortOrder
    ) {
    }
}
