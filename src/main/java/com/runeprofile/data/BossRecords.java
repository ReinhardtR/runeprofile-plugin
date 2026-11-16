package com.runeprofile.data;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class BossRecords {
    // Chat Commands boss key -> kill count
    private Map<String, Integer> killCounts = new HashMap<>();

    // Chat Commands boss key -> personal best in seconds
    private Map<String, Double> personalBests = new HashMap<>();

    public boolean isEmpty() {
        return killCounts.isEmpty() && personalBests.isEmpty();
    }
}
