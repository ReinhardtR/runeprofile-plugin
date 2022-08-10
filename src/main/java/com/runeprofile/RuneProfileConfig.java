package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("runeprofile")
public interface RuneProfileConfig extends Config {
	String CONFIG_GROUP = "runeprofile";
	String COLLECTION_LOG_KEY = "collection_log";

	@ConfigItem(keyName = "placeholder", name = "Placeholder Name", description = "A long placeholder description.")
	default String greeting() {
		return "Placeholder return value!";
	}
}
