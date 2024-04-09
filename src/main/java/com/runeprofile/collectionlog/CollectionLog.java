package com.runeprofile.collectionlog;

import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class CollectionLog {

	// First map is a tab, second map is the pages in the tab
	private final Map<String, Map<String, CollectionLogPage>> tabs = new HashMap<>();

	@Setter
	private int uniqueItemsTotal;

	@Setter
	private int uniqueItemsObtained;

	public Map<String, CollectionLogPage> getTab(String key) {
		if (tabs.containsKey(key)) {
			return tabs.get(key);
		}

		tabs.put(key, new HashMap<>());

		return tabs.get(key);
	}
}
