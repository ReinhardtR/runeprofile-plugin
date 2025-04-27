package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneProfilePlugin.CONFIG_GROUP)
public interface RuneProfileConfig extends Config {
    @ConfigItem(keyName = "update_on_logout", name = "Update on logout", description = "Automatically update your RuneProfile on logout. (Excluding model and Collection Log)")
    default boolean updateOnLogout() {
        return true;
    }

    @ConfigItem(
            keyName = "menuLookupOption",
            name = "Menu option",
            description = "Add RuneProfile option to menus"
    )
    default boolean menuLookupOption() {
        return false;
    }
}
