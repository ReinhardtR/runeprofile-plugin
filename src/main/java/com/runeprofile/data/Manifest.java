package com.runeprofile.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class Manifest {
    // Fields must not be final: a final primitive with a constant initializer is a
    // constant variable (JLS 4.12.4), so its getter compiles to the constant and
    // ignores the values Gson writes into the field.
    int version = -1;
    Map<String, List<String>> pages = new HashMap<>();
    int[] combatAchievementVarps = new int[0];

    /**
     * Minimum gp value for a drop to be recorded as valuable. Defaults to 0 so a
     * missing/old manifest never causes the plugin to record everything; callers
     * fall back to their own constant when this is unset.
     */
    int valuableDropThreshold = 0;

    /**
     * Items with a fixed value used instead of their GE price. Lets the backend
     * add tracked drops (vestiges, Araxxor pieces, ...) without a plugin update.
     */
    List<SpecialValuableDrop> specialValuableDrops = new ArrayList<>();

    @Data
    public static class SpecialValuableDrop {
        int itemId = 0;
        int value = 0;
    }
}
