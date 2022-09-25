package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("runeprofile")
public interface RuneProfileConfig extends Config {
	String CONFIG_GROUP = "runeprofile";
	String COLLECTION_LOG = "collection_log";
	String DESCRIPTION = "description";
	String GENERATED_PATH = "generated_path"; // The generated path for the profile.
	String IS_PRIVATE = "is_private"; // The profile is unlisted/private.
	String HAS_MODEL = "has_model"; // A model has been submitted before.

	@ConfigItem(keyName = "update_on_logout", name = "Update on logout", description = "Automatically update your RuneProfile on logout. (Excluding model)")
	default boolean updateOnLogout() {
		return true;
	}
}
