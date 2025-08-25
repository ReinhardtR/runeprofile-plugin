package com.runeprofile.utils;


import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

import javax.annotation.Nullable;

public class ItemUtils {
    public static final String ITEM_CACHE_BASE_URL = "https://static.runelite.net/cache/item/";

    public static final int VALUABLE_DROP_THRESHOLD = 1_000_000;

    @Getter
    @RequiredArgsConstructor
    public enum ClanBroadcastValue {
        BELLATOR_VESTIGE(28279, 5_000_000),
        MAGUS_VESTIGE(28281, 5_000_000),
        VENATOR_VESTIGE(28283, 5_000_000),
        ULTOR_VESTIGE(28285, 5_000_000),

        NOXIOUS_POINT(29790, 10_000_000),
        NOXIOUS_BLADE(29792, 10_000_000),
        NOXIOUS_POMMEL(29794, 10_000_000),
        ARAXYTE_FANG(29799, 50_000_000),

        MOKHAIOTL_CLOTH(31109, 75_000_000);

        private final int itemId;
        private final int value;

        public static @Nullable ClanBroadcastValue getByItemId(int itemId) {
            for (ClanBroadcastValue value : ClanBroadcastValue.values()) {
                if (value.getItemId() == itemId) {
                    return value;
                }
            }
            return null;
        }
    }

    public static int getUnnotedItemId(@NonNull ItemComposition comp) {
        return isItemNoted(comp) ? comp.getLinkedNoteId() : comp.getId();
    }

    public static int getPerceivedItemValue(@NonNull ItemManager itemManager, int itemId) {
        ClanBroadcastValue clanBroadcastValue = ClanBroadcastValue.getByItemId(itemId);
        if (clanBroadcastValue != null) {
            return clanBroadcastValue.getValue();
        }
        return itemManager.getItemPriceWithSource(itemId, true);
    }

    public static boolean isItemNoted(@NonNull ItemComposition item) {
        return item.getNote() != -1;
    }
}
