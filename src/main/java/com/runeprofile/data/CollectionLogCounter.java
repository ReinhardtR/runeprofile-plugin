package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;

@Data
public class CollectionLogCounter {
    private final String label;

    @Nullable
    private final String shortLabel;

    // "kc" or "pb"
    private final String type;

    // personal bests in seconds
    @Nullable
    private final Double value;

    public String getDisplayLabel() {
        return shortLabel != null && !shortLabel.isEmpty() ? shortLabel : label;
    }

    public boolean isPersonalBest() {
        return "pb".equals(type);
    }
}
