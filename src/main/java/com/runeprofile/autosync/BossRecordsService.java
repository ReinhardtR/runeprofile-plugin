package com.runeprofile.autosync;

import com.runeprofile.data.BossRecords;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;

/** Reads the kill counts and personal bests RuneLite's Chat Commands plugin stores per profile. */
@Slf4j
@Singleton
public class BossRecordsService {
    private static final String KILL_COUNT_GROUP = "killcount";
    private static final String PERSONAL_BEST_GROUP = "personalbest";

    @Inject
    private ConfigManager configManager;

    public BossRecords collect() {
        BossRecords records = new BossRecords();

        String profile = configManager.getRSProfileKey();
        if (profile == null) {
            return records;
        }

        for (String key : configManager.getRSProfileConfigurationKeys(KILL_COUNT_GROUP, profile, "")) {
            Integer killCount = read(KILL_COUNT_GROUP, profile, key, Integer.class);
            if (killCount != null && killCount > 0) {
                records.getKillCounts().put(key, killCount);
            }
        }

        for (String key : configManager.getRSProfileConfigurationKeys(PERSONAL_BEST_GROUP, profile, "")) {
            Double seconds = read(PERSONAL_BEST_GROUP, profile, key, Double.class);
            if (seconds != null && seconds > 0) {
                records.getPersonalBests().put(key, seconds);
            }
        }

        return records;
    }

    private <T> T read(String group, String profile, String key, Class<T> type) {
        try {
            return configManager.getConfiguration(group, profile, key, type);
        } catch (Exception e) {
            log.debug("Skipping unreadable {} entry {}", group, key, e);
            return null;
        }
    }
}
