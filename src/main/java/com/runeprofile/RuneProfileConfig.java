package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("runeprofile")
public interface RuneProfileConfig extends Config {
    String CONFIG_GROUP = "runeprofile";

    String MODEL_UPDATE_DATE = "model_update_date"; // The last time the model was updated.

    @ConfigItem(keyName = "update_on_logout", name = "Update on logout", description = "Automatically update your RuneProfile on logout. (Excluding model and Collection Log)")
    default boolean updateOnLogout() {
        return true;
    }
}
