package com.runeprofile.achievementdiary;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AchievementDiaryState {
	@Getter
	private final String name;

	@Getter
	private final AchievementDiaryTierState easyTier;

	@Getter
	private final AchievementDiaryTierState mediumTier;

	@Getter
	private final AchievementDiaryTierState hardTier;

	@Getter
	private final AchievementDiaryTierState eliteTier;
}
