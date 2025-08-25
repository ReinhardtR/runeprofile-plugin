package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(RuneProfilePlugin.CONFIG_GROUP)
public interface RuneProfileConfig extends Config {
    @ConfigItem(
            keyName = "update_on_logout", // config option was previously used only for update on logout
            name = "Auto-sync Profile",
            description = "Automatically updates your RuneProfile for you.",
            position = 1
    )
    default boolean autosyncProfile() {
        return true;
    }

    @ConfigItem(
            keyName = "track_valuable_drops",
            name = "Track Valuable Drops",
            description = "Adds valuable drops to your RuneProfile activities. (Requires auto-sync to be enabled)",
            position = 2
    )
    default boolean trackValuableDrops() {
        return true;
    }

    @ConfigItem(
            keyName = "include_clan_data",
            name = "Include Clan in Profile",
            description = "Include your clan on your RuneProfile.",
            position = 3
    )
    default boolean includeClanData() {
        return true;
    }

    @ConfigItem(
            keyName = "log_command",
            name = "Enable !log command",
            description = "Enables the !log command in the game chat.",
            position = 4
    )
    default boolean enableLogCommand() {
        return true;
    }

    @ConfigItem(
            keyName = "enableExtendedItemNames",
            name = "Show item names for !log",
            description = "Show item names next to their icons in the collection log command.",
            position = 5
    )
    default boolean enableExtendedItemNames()
    {
        return false;
    }

    @ConfigItem(
            keyName = "show_clog_sync_button",
            name = "Enable RuneProfile Sync button",
            description = "Shows the RuneProfile button in the Collection Log menu.",
            position = 6
    )
    default boolean showClogSyncButton() {
        return true;
    }

    @ConfigItem(
            keyName = "menu_lookup_option",
            name = "Chat menu option",
            description = "Add RuneProfile option to chat menus",
            position = 7
    )
    default boolean showMenuLookupOption() {
        return false;
    }

    @ConfigItem(
            keyName = "show_side_panel",
            name = "Show the RuneProfile side panel",
            description = "Adds RuneProfile to the RuneLite plugin side bar.",
            position = 7
    )
    default boolean showSidePanel() {
        return true;
    }
}
