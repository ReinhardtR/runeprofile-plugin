package com.runeprofile.achievementdiary;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;

@RequiredArgsConstructor
public enum AchievementDiary {
	ARDOUGNE(1, "Ardougne"),
	DESERT(5, "Desert"),
	FALADOR(2, "Falador"),
	FREMENNIK(3, "Fremennik"),
	KANDARIN(4, "Kandarin"),
	KARAMJA(0, "Karamja"),
	KOUREND(11, "Kourend & Kebos"),
	LUMBRIDGE(6, "Lumbridge & Draynor"),
	MORYTANIA(7, "Morytania"),
	VARROCK(8, "Varrock"),
	WESTERN_PROVINCES(10, "Western Provinces"),
	WILDERNESS(9, "Wilderness");

	@Getter
	private final int id;

	@Getter
	private final String name;

	public AchievementDiaryState getState(Client client) {
		// https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cdiary_completion_info%5D.cs2
		client.runScript(2200, id);

		int[] stack = client.getIntStack();

		AchievementDiaryTierState easyTierState = new AchievementDiaryTierState(
						AchievementDiaryTier.EASY,
						stack[0],
						stack[1]
		);

		AchievementDiaryTierState mediumTierState = new AchievementDiaryTierState(
						AchievementDiaryTier.MEDIUM,
						stack[3],
						stack[4]
		);

		AchievementDiaryTierState hardTierState = new AchievementDiaryTierState(
						AchievementDiaryTier.HARD,
						stack[6],
						stack[7]
		);

		AchievementDiaryTierState eliteTierState = new AchievementDiaryTierState(
						AchievementDiaryTier.ELITE,
						stack[9],
						stack[10]
		);

		return new AchievementDiaryState(name, easyTierState, mediumTierState, hardTierState, eliteTierState);
	}
}
