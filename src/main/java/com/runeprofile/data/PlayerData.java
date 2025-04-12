package com.runeprofile.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class PlayerData {
    String id;
    String username;
    Integer accountType;

    // skill name -> xp
    Map<String, Integer> skills = new HashMap<>();

    // quest id -> quest state
    Map<Integer, Integer> quests = new HashMap<>();

    // tier id -> completed count
    Map<Integer, Integer> combatAchievementTiers = new HashMap<>();

    List<AchievementDiaryTierData> achievementDiaryTiers = new ArrayList<>();

    // item id -> quantity
    Map<Integer, Integer> items = new HashMap<>();
}
