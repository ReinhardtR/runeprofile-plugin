package com.runeprofile.combatachievements;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;

@RequiredArgsConstructor
public enum CombatAchievementTier {
	EASY(1, "Easy"),
	MEDIUM(2, "Medium"),
	HARD(3, "Hard"),
	ELITE(4, "Elite"),
	MASTER(5, "Master"),
	GRANDMASTER(6, "Grandmaster");

	@Getter
	private final int id;

	@Getter
	private final String name;

	public CombatAchievementTierState getState(Client client) {
		// CA Tasks Completed in Tier
		// https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cca_tasks_completed_tier%5D.cs2
		client.runScript(4784, id);
		int tasksCompleted = client.getIntStack()[0];

		// CA Total Tasks in Tier
		// https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc,ca_tasks_tier_total%5D.cs2
		client.runScript(4789, id);
		int totalTasks = client.getIntStack()[0];

		return new CombatAchievementTierState(this, tasksCompleted, totalTasks);
	}
}
