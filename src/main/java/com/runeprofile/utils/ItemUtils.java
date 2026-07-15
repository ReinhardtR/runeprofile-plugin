package com.runeprofile.utils;


import lombok.NonNull;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

import javax.annotation.Nullable;
import java.util.Map;

public class ItemUtils {
    public static final String ITEM_CACHE_BASE_URL = "https://static.runelite.net/cache/item/";

    /**
     * Default cutoff used until the manifest is loaded. The backend can override
     * this via the manifest.
     */
    public static final int VALUABLE_DROP_THRESHOLD = 1_000_000;

    public static int getUnnotedItemId(@NonNull ItemComposition comp) {
        return isItemNoted(comp) ? comp.getLinkedNoteId() : comp.getId();
    }

    /**
     * Resolves the value used to decide whether a drop is valuable. Items present
     * in {@code valueOverrides} (the special valuable drops served by the manifest)
     * use their fixed value; everything else uses the live GE price.
     */
    public static int getPerceivedItemValue(@NonNull ItemManager itemManager, int itemId, @Nullable Map<Integer, Integer> valueOverrides) {
        if (valueOverrides != null) {
            Integer override = valueOverrides.get(itemId);
            if (override != null) {
                return override;
            }
        }
        return itemManager.getItemPriceWithSource(itemId, true);
    }

    public static boolean isItemNoted(@NonNull ItemComposition item) {
        return item.getNote() != -1;
    }
}
