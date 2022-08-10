package com.runeprofile.achievementdiary;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AchievementDiaryTierState {
	@Getter
	private final AchievementDiaryTier tier;
	@Getter
	private final int completedTasks;
	@Getter
	private final int totalTasks;
	@Getter
	private final boolean isCompleted;
}
