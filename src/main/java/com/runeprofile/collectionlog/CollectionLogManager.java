package com.runeprofile.collectionlog;

import com.google.gson.Gson;
import com.runeprofile.RuneProfileConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CollectionLogManager {
//	private static final int ENTRY_VARBIT_INDEX = 2049;
	private static final int TAB_ACTIVE_COLOR = 16754735;
	private static final int TAB_TEXT_INDEX = 3;

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;

	@Getter
	private final CollectionLog collectionLog;

	public CollectionLogManager(Client client, ClientThread clientThread, ConfigManager configManager) {
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;

		collectionLog = getStoredCollectionLog();
	}

	private CollectionLog getStoredCollectionLog() {
		long accountHash = client.getAccountHash();

		String collectionLogString = configManager.getConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.COLLECTION_LOG_KEY + accountHash
		);

		if (collectionLogString == null) {
			return new CollectionLog();
		}

		return new Gson().fromJson(collectionLogString, CollectionLog.class);
	}

	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		if (scriptPostFired.getScriptId() == ScriptID.COLLECTION_DRAW_LIST) {
			log.info("COLLECTION DRAW LIST");
			clientThread.invokeLater(this::updateCollectionLog);
		}
	}

//	public void onVarbitChanged(VarbitChanged varbitChanged) {
//		if (varbitChanged.getIndex() == ENTRY_VARBIT_INDEX) {
//			log.info("ENTRY VARBIT CHANGED");
//			clientThread.invokeLater(this::updateCollectionLog);
//		}
//	}

	public CollectionLogEntry getEntry() {
		String name = getEntryName();

		if (name == null) {
			return null;
		}

		int index = getEntryIndex();
		CollectionLogItem[] items = getItems().toArray(new CollectionLogItem[0]);
		CollectionLogKillCount[] killCounts = getKillCounts().toArray(new CollectionLogKillCount[0]);

		return new CollectionLogEntry(index, items, killCounts.length > 0 ? killCounts : null);
	}

	public void updateCollectionLog() {
		// Get entry
		CollectionLogEntry collectionLogEntry = getEntry();

		if (collectionLogEntry == null) {
			return;
		}

		// Update collection log
		collectionLog.getTab(getTabTitle()).put(getEntryName(), collectionLogEntry);

		int uniqueItemsObtained = client.getVarpValue(2943);
		collectionLog.setUniqueItemsObtained(uniqueItemsObtained);

		int uniqueItemsTotal = client.getVarpValue(2944);
		collectionLog.setUniqueItemsTotal(uniqueItemsTotal);

		// Set configuration for account
		long accountHash = client.getAccountHash();

		configManager.setConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.COLLECTION_LOG_KEY + accountHash,
						collectionLog
		);
	}

	private Widget getEntryHead() {
		return client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_HEADER);
	}

	private int getEntryIndex() {
		String entryName = getEntryName();

		if (entryName == null) {
			return -1;
		}

		CollectionLogTabs tab = CollectionLogTabs.getByName(getTabTitle());

		if (tab == null) {
			return -1;
		}

		Widget entryList = client.getWidget(WidgetID.COLLECTION_LOG_ID, tab.getEntryListId());

		if (entryList == null) {
			return -1;
		}

		Widget[] entries = entryList.getDynamicChildren();

		int index = -1;

		log.info("ENTRY NAME: " + entryName);
		for (int i = 0; i < entries.length; i++) {
			String entryText = entries[i].getText();
			log.info("CURRENT ENTRY TEXT: " + entryText);
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
			return null;
		}

		return children[0].getText();
	}

	private String getTabTitle() {
		for (CollectionLogTabs tab : CollectionLogTabs.values()) {
			Widget tabWidget = client.getWidget(WidgetID.COLLECTION_LOG_ID, tab.getId());

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

		return null;
	}

	private List<CollectionLogItem> getItems() {
		List<CollectionLogItem> items = new ArrayList<>();

		Widget itemsContainer = client.getWidget(WidgetInfo.COLLECTION_LOG_ENTRY_ITEMS);

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

			killCounts.add(new CollectionLogKillCount(killCountName, killCountAmount));
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
