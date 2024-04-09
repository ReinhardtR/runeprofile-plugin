package com.runeprofile.collectionlog;

import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;

@RequiredArgsConstructor
public class CollectionLogPage {
	private final int index;

	private final CollectionLogItem[] items;

	@Nullable
	private final CollectionLogKillCount[] killCounts;
}
