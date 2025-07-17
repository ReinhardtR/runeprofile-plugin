package com.runeprofile.autosync;

import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class AutoSyncScheduler {
    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfilePlugin plugin;

    // A reference to the pending auto-sync task
    private ScheduledFuture<?> autoSyncFuture;

    private static final int RAPID_SYNC_SECONDS = 3;
    private static final int AUTO_SYNC_MINUTES = 1;

    public void startUp() {
        // Schedule the first auto-sync
        resetAutoSyncTimer();
    }

    public void shutDown() {
        if (autoSyncFuture != null) {
            autoSyncFuture.cancel(true);
        }
    }

    public void resetAutoSyncTimer() {
        if (autoSyncFuture != null) {
            autoSyncFuture.cancel(false);
        }

        log.debug("Auto-sync timer reset. Next sync in {} minutes.", AUTO_SYNC_MINUTES);
        autoSyncFuture = scheduledExecutorService.schedule(this::syncAndResetTimer, AUTO_SYNC_MINUTES, TimeUnit.MINUTES);
    }

    public void startRapidSync() {
        log.debug("Starting rapid sync...");
        resetAutoSyncTimer();
        scheduledExecutorService.schedule(this::syncAndResetTimer, RAPID_SYNC_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void syncAndResetTimer() {
        log.debug("Syncing profile...");
        resetAutoSyncTimer();
        plugin.updateProfileAsync(true);
    }
}