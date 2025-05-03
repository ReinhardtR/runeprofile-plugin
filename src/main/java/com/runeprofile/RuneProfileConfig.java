package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneProfilePlugin.CONFIG_GROUP)
public interface RuneProfileConfig extends Config {
    @ConfigItem(
            keyName = "update_on_logout",
            name = "Update on logout",
            description = "Automatically update your RuneProfile on logout. (Excluding model and Collection Log)"
    )
    default boolean updateOnLogout() {
        return true;
    }

    @ConfigItem(
            keyName = "include_clan_data",
            name = "Include Clan in Profile",
            description = "Include your clan in your profile data when updating."
    )
    default boolean includeClanData() {
        return true;
    }

    @ConfigItem(
            keyName = "menu_lookup_option",
            name = "Menu option",
            description = "Add RuneProfile option to menus"
    )
    default boolean showMenuLookupOption() {
        return false;
    }

    @ConfigItem(
            keyName = "show_clog_sync_button",
            name = "Enable RuneProfile button",
            description = "Shows the RuneProfile button in the Collection Log window."
    )
    default boolean showClogSyncButton() {
        return true;
    }

    @ConfigItem(
            keyName = "log_command",
            name = "Enable !log command",
            description = "Enables the !log command in the game chat."
    )
    default boolean enableLogCommand() {
        return true;
    }
}
