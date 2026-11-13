package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;

/**
 * A kill count or personal best line of a collection log page, as the API resolved it.
 */
@Data
public class CollectionLogCounter {
    private final String label;

    // Compact label for the chat reply (e.g. "PB"). Null when the page has none, or from older APIs.
    @Nullable
    private final String shortLabel;

    // "kc" or "pb"
    private final String type;

    // Kill count, or personal best in seconds. Null when unknown.
    @Nullable
    private final Double value;

    public String getDisplayLabel() {
        return shortLabel != null && !shortLabel.isEmpty() ? shortLabel : label;
    }

    public boolean isPersonalBest() {
        return "pb".equals(type);
    }
}
