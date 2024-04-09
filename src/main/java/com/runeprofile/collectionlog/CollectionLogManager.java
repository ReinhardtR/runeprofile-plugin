package com.runeprofile.collectionlog;

import com.google.gson.Gson;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class CollectionLogManager {
	private static final int TAB_ACTIVE_COLOR = 16754735;
	private static final int TAB_TEXT_INDEX = 3;

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final Gson gson;
	private String previousTab = null;
	@Getter
	private CollectionLog collectionLog;

	public CollectionLogManager(Gson gson) {
		this.gson = gson;
		this.client = RuneProfilePlugin.getClient();
		this.configManager = RuneProfilePlugin.getConfigManager();
		this.clientThread = RuneProfilePlugin.getClientThread();
		reloadManager();
	}

	public void reloadManager() {
		collectionLog = getStoredCollectionLog();
	}

	private CollectionLog getStoredCollectionLog() {
		String collectionLogString = configManager.getRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.COLLECTION_LOG
		);

		if (collectionLogString == null) {
			return new CollectionLog();
		}

		try {
			return gson.fromJson(collectionLogString, CollectionLog.class);
		} catch (Exception e) {
			log.error("Error parsing collection log. String: " + collectionLogString);
			throw e;
		}
	}

	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		if (scriptPostFired.getScriptId() == ScriptID.COLLECTION_DRAW_LIST) {
			log.info("COLLECTION DRAW LIST FIRED");

			if (RuneProfilePlugin.getClient().getWidget(InterfaceID.ADVENTURE_LOG) != null) {
				log.info("Adventure Log isn't supported");
				return;
			}

			clientThread.invokeLater(this::updateCollectionLog);
			updateCollectionLogPanel();
		}
	}

	private void updateCollectionLogPanel() {
		String tabName = getTabName();

		if (tabName == null) {
			return;
		}

		if (tabName.equals(previousTab)) {
			RuneProfilePlugin.getPanel().getMainPanel().getCollectionLogPanel().newEntrySelected(getEntryName());
			return;
		}

		previousTab = tabName;

		List<String> entriesInTab = new ArrayList<>();

		Widget[] entryWidgets = getEntriesInTab();
		if (entryWidgets != null) {
			for (Widget entryWidget : entryWidgets) {
				entriesInTab.add(entryWidget.getText());
			}
		}

		Map<String, CollectionLogPage> storedTab = collectionLog.getTab(tabName);

		if (storedTab == null) {
			return;
		}

		Set<String> storedInLog = storedTab.keySet();

		List<String> missingEntries = new ArrayList<>(entriesInTab);
		missingEntries.removeAll(storedInLog);
		missingEntries.remove(getEntryName());

		RuneProfilePlugin.getPanel().getMainPanel().getCollectionLogPanel().newTabSelected(missingEntries);
	}

	private Widget[] getEntriesInTab() {
		String entryName = getEntryName();

		if (entryName == null) {
			return null;
		}

		CollectionLogTabs tab = CollectionLogTabs.getByName(getTabName());

		if (tab == null) {
			return null;
		}

		Widget entryList = client.getWidget(InterfaceID.COLLECTION_LOG, tab.getEntryListId());

		if (entryList == null) {
			return null;
		}

		return entryList.getDynamicChildren();
	}

	public CollectionLogPage getEntry() {
		String name = getEntryName();

		if (name == null) {
			return null;
		}

		int index = getEntryIndex();
		CollectionLogItem[] items = getItems().toArray(new CollectionLogItem[0]);
		CollectionLogKillCount[] killCounts = getKillCounts().toArray(new CollectionLogKillCount[0]);

		return new CollectionLogPage(index, items, killCounts.length > 0 ? killCounts : null);
	}

	public void updateCollectionLog() {
		// Get entry
		CollectionLogPage collectionLogPage = getEntry();

		if (collectionLogPage == null) {
			log.error("Failed to get entry");
			return;
		}

		// Update collection log
		String tabTitle = getTabName();
		String entryName = getEntryName();
		collectionLog.getTab(tabTitle).put(entryName, collectionLogPage);

		int uniqueItemsObtained = client.getVarpValue(2943);
		collectionLog.setUniqueItemsObtained(uniqueItemsObtained);

		int uniqueItemsTotal = client.getVarpValue(2944);
		collectionLog.setUniqueItemsTotal(uniqueItemsTotal);

		configManager.setRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.COLLECTION_LOG,
						gson.toJson(collectionLog)
		);

		log.info("Updated the Collection Log");
	}

	private Widget getEntryHead() {
		return client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_HEADER);
	}

	private int getEntryIndex() {
		Widget[] entries = getEntriesInTab();

		int index = -1;

		if (entries == null) {
			log.error("Failed to get entry index");
			return index;
		}

		String entryName = getEntryName();

		for (int i = 0; i < entries.length; i++) {
			String entryText = entries[i].getText();

			if (entryText.equals(entryName)) {
				index = i;
				break;
			}
		}

		log.info("ENTRY INDEX: " + index);
		return index;
	}

	private String getEntryName() {
		Widget entryHead = getEntryHead();

		if (entryHead == null) {
			return null;
		}

		Widget[] children = entryHead.getDynamicChildren();

		if (children == null || children.length == 0) {
			log.error("Failed to get entry name");
			return null;
		}

		return children[0].getText();
	}

	private String getTabName() {
		for (CollectionLogTabs tab : CollectionLogTabs.values()) {
			Widget tabWidget = client.getWidget(InterfaceID.COLLECTION_LOG, tab.getId());

			if (tabWidget == null) {
				continue;
			}

			Widget[] children = tabWidget.getDynamicChildren();

			if (children == null || TAB_TEXT_INDEX >= children.length) {
				continue;
			}

			Widget titleWidget = children[TAB_TEXT_INDEX];
			String title = titleWidget.getText();
			int color = titleWidget.getTextColor();

			if (color == TAB_ACTIVE_COLOR) {
				return title;
			}
		}

		log.error("Failed to get tab name");
		return null;
	}

	private List<CollectionLogItem> getItems() {
		List<CollectionLogItem> items = new ArrayList<>();

		Widget itemsContainer = client.getWidget(ComponentID.COLLECTION_LOG_ENTRY_ITEMS);

		if (itemsContainer == null) {
			return items;
		}

		Widget[] itemWidgets = itemsContainer.getDynamicChildren();

		for (Widget itemWidget : itemWidgets) {
			int index = itemWidget.getIndex();
			int id = itemWidget.getItemId();
			String name = unwrapText(itemWidget.getName());
			int quantity = itemWidget.getOpacity() == 0 ? itemWidget.getItemQuantity() : 0;

			items.add(new CollectionLogItem(index, id, name, quantity));
		}

		return items;
	}

	private List<CollectionLogKillCount> getKillCounts() {
		List<CollectionLogKillCount> killCounts = new ArrayList<>();

		Widget entryHead = getEntryHead();

		if (entryHead == null) {
			return killCounts;
		}

		Widget[] children = entryHead.getDynamicChildren();

		if (children == null || children.length < 3) {
			return killCounts;
		}

		// add all counters of all lines in the widget (starting from child index 2)
		for (int childIndex = 2; childIndex < children.length; childIndex++) {
			String rawKillCount = children[childIndex].getText();
			String[] killCountParts = rawKillCount.split(": ");

			// guard: make sure this is a KC line
			if (killCountParts.length <= 1) {
				continue;
			}

			String killCountName = killCountParts[0];
			int killCountAmount = Integer.parseInt(unwrapText(killCountParts[1]));

			killCounts.add(new CollectionLogKillCount(childIndex - 2, killCountName, killCountAmount));
		}

		return killCounts;
	}

	// Example
	// Input: "<col=ff9040>Abyssal whip</col>"
	// Output: "Abyssal whip"
	private String unwrapText(String text) {
		return text.split(">")[1]
						.split("<")[0]
						.replace(",", "");
	}
}
