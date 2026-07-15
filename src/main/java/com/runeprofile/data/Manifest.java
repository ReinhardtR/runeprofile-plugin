package com.runeprofile.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Manifest {
    final int version = -1;
    final Map<String, List<String>> pages = new HashMap<>();
    final int[] combatAchievementVarps = new int[0];

    /**
     * Minimum gp value for a drop to be recorded as valuable. Defaults to 0 so a
     * missing/old manifest never causes the plugin to record everything; callers
     * fall back to their own constant when this is unset.
     */
    final int valuableDropThreshold = 0;

    /**
     * Items with a fixed value used instead of their GE price. Lets the backend
     * add tracked drops (vestiges, Araxxor pieces, ...) without a plugin update.
     */
    final List<SpecialValuableDrop> specialValuableDrops = new ArrayList<>();

    @Data
    public static class SpecialValuableDrop {
        final int itemId = 0;
        final int value = 0;
    }
}
