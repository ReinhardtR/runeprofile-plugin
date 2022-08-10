package com.runeprofile.collectionlog;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum CollectionLogTabs {
	BOSSES(4),
	RAIDS(5),
	CLUES(6),
	MINIGAMES(7),
	OTHER(8);

	@Getter
	private final int id;
}
