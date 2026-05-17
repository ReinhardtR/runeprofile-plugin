package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PlayerData {
    private String id;
    private String username;
    private Integer accountType;

    @Nullable
    private PlayerClanData clan;

    @Nullable
    private String groupName;

    // skill name -> xp
    private Map<String, Integer> skills = new HashMap<>();

    // quest id -> quest state
    private Map<Integer, Integer> quests = new HashMap<>();

    // varp id -> raw 32-bit value
    private Map<Integer, Integer> combatAchievementVarps = new HashMap<>();

    private List<AchievementDiaryTierData> achievementDiaryTiers = new ArrayList<>();

    // item id -> quantity
    private Map<Integer, Integer> items = new HashMap<>();
}
