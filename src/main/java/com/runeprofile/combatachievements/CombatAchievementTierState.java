package com.runeprofile.combatachievements;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CombatAchievementTierState {
	@Getter
	private final CombatAchievementTier tier;

	@Getter
	private final int completedTasks;

	@Getter
	private final int totalTasks;
}
