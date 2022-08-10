package com.runeprofile.achievementdiary;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AchievementDiaryTier {
	EASY("Easy"),
	MEDIUM("Medium"),
	HARD("Hard"),
	ELITE("Elite");
	
	@Getter
	private final String name;
}
