package com.runeprofile;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(RuneProfilePlugin.CONFIG_GROUP)
public interface RuneProfileConfig extends Config {
    @ConfigSection(
            name = "Profile",
            description = "Settings for your RuneProfile",
            position = 1
    )
    String profileSection = "profileSection";

    @ConfigItem(
            keyName = "update_on_logout", // config option was previously used only for update on logout
            name = "Auto-sync Profile",
            description = "Automatically updates your RuneProfile for you.",
            position = 1,
            section = profileSection
    )
    default boolean autosyncProfile() {
        return true;
    }

    @ConfigItem(
            keyName = "track_valuable_drops",
            name = "Track Valuable Drops",
            description = "Adds valuable drops to your RuneProfile activities. (Requires auto-sync to be enabled)",
            position = 2,
            section = profileSection
    )
    default boolean trackValuableDrops() {
        return true;
    }

    @ConfigItem(
            keyName = "include_clan_data",
            name = "Include Clan in Profile",
            description = "Include your clan on your RuneProfile.",
            position = 3,
            section = profileSection
    )
    default boolean includeClanData() {
        return true;
    }

    @ConfigSection(
            name = "Commands",
            description = "Settings for chat commands",
            position = 2
    )
    String commandsSection = "commandsSection";

    @ConfigItem(
            keyName = "log_command",
            name = "Enable !log command",
            description = "Enables the !log command in the game chat.",
            position = 1,
            section = commandsSection
    )
    default boolean enableLogCommand() {
        return true;
    }

    @ConfigItem(
            keyName = "command_suggestion_overlay",
            name = "Command Suggestion Overlay",
            description = "Displays a list of potential commands in an overlay while you're typing a chat message.",
            position = 2,
            section = commandsSection
    )
    default boolean commandSuggestionOverlay() {
        return true;
    }

    @ConfigItem(
            keyName = "enableExtendedItemNames",
            name = "Show item names for !log",
            description = "Show item names next to their icons in the collection log command.",
            position = 3,
            section = commandsSection
    )
    default boolean enableExtendedItemNames() {
        return false;
    }

    @ConfigSection(
            name = "Menu Options",
            description = "Settings for menu options",
            position = 3
    )
    String menuOptionsSection = "menuOptionsSection";

    @ConfigItem(
            keyName = "menu_lookup_option",
            name = "Chat menu option",
            description = "Add RuneProfile option to chat menus",
            position = 1,
            section = menuOptionsSection
    )
    default boolean showMenuLookupOption() {
        return false;
    }

    @ConfigItem(
            keyName = "player_menu_lookup_option",
            name = "Player menu option",
            description = "Add RuneProfile option to player menu",
            position = 2,
            section = menuOptionsSection
    )
    default boolean showPlayerLookupOption() {
        return false;
    }

    @ConfigSection(
            name = "Other",
            description = "Other plugin settings",
            position = 4
    )
    String otherSection = "otherSection";

    @ConfigItem(
            keyName = "show_side_panel",
            name = "Show the RuneProfile side panel",
            description = "Adds RuneProfile to the RuneLite plugin side bar.",
            position = 1,
            section = otherSection
    )
    default boolean showSidePanel() {
        return true;
    }

    @ConfigItem(
            keyName = "show_clog_sync_button",
            name = "Enable RuneProfile Sync button",
            description = "Shows the RuneProfile button in the Collection Log menu.",
            position = 2,
            section = otherSection
    )
    default boolean showClogSyncButton() {
        return true;
    }

}
