package com.yongsoo.youtubeatlasbackend.game.api;

import java.util.List;

public record InventorySlotResponse(
    Integer baseSlots,
    Integer totalSlots,
    Integer maxSlots,
    TierResponse currentTier,
    TierResponse nextTier,
    List<TierResponse> tiers
) {
}
