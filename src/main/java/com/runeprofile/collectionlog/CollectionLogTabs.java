package com.runeprofile.collectionlog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CollectionLogTabs {
	BOSSES("Bosses", 4, 12),
	RAIDS("Raids", 5, 16),
	CLUES("Clues", 6, 32),
	MINIGAMES("Minigames", 7, 35),
	OTHER("Other", 8, 34);

	@Getter
	private final String name;

	@Getter
	private final int id;

	@Getter
	private final int entryListId;

	public static CollectionLogTabs getByName(String name) {
		for (CollectionLogTabs tab : CollectionLogTabs.values()) {
			if (tab.getName().equals(name)) {
				return tab;
			}
		}

		return null;
	}
}
