package com.runeprofile.collectionlog;

import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class CollectionLog {

	// First map is a tab, second map is the entries in the tab
	private final Map<String, Map<String, CollectionLogEntry>> tabs = new HashMap<>();

	@Setter
	private int uniqueItemsTotal;

	@Setter
	private int uniqueItemsObtained;

	public Map<String, CollectionLogEntry> getTab(String key) {
		if (tabs.containsKey(key)) {
			return tabs.get(key);
		}

		tabs.put(key, new HashMap<>());

		return tabs.get(key);
	}
}
