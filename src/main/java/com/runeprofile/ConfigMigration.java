package com.runeprofile;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Singleton;

@Slf4j
@Singleton
public class ConfigMigration {
    private final ConfigManager configManager;

    @Inject
    private ConfigMigration(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void migrate() {
        migrateClogSyncButtonToMode();
    }

    /**
     * The "show_clog_sync_button" boolean was replaced by the "clog_sync_button_mode" enum.
     * true maps to the default (REPLACE_SEARCH), so only false needs an explicit write.
     */
    private void migrateClogSyncButtonToMode() {
        String oldValue = configManager.getConfiguration(RuneProfilePlugin.CONFIG_GROUP, "show_clog_sync_button");
        if (oldValue == null) {
            return;
        }

        if ("false".equals(oldValue)) {
            configManager.setConfiguration(RuneProfilePlugin.CONFIG_GROUP, "clog_sync_button_mode",
                    RuneProfileConfig.SyncButtonMode.DISABLED);
        }
        configManager.unsetConfiguration(RuneProfilePlugin.CONFIG_GROUP, "show_clog_sync_button");

        log.debug("Migrated show_clog_sync_button={} to clog_sync_button_mode", oldValue);
    }
}
