package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Singleton
public class AutoSyncScheduler {
    @Inject
    private EventBus eventBus;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfilePlugin plugin;

    @Inject
    private RuneProfileConfig config;

    // A reference to the pending auto-sync task
    private ScheduledFuture<?> autoSyncFuture;

    // Prevent multiple sync executions from overlapping
    private final AtomicBoolean isSyncing = new AtomicBoolean(false);

    // Store remaining time when paused on logout (in milliseconds)
    private static final TimeUnit PAUSE_TIME_UNIT = TimeUnit.SECONDS;
    private long remainingSyncPause = 0;

    private static final int RAPID_SYNC_SECONDS = 3;
    private static final int AUTO_SYNC_MINUTES = 60;

    public boolean isEnabled() {
        return config.autosyncProfile();
    }

    public void startUp() {
        eventBus.register(this);
        if (isEnabled()) {
            resetAutoSyncTimer();
        }
    }

    public void shutDown() {
        eventBus.unregister(this);
        cancelScheduledSync();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) return;

        if (isEnabled()) {
            resetAutoSyncTimer();
        } else {
            cancelScheduledSync();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (!isEnabled()) return;

        if (event.getGameState() != GameState.LOGGED_IN) {
            if (autoSyncFuture != null) {
                long remainingTime = autoSyncFuture.getDelay(PAUSE_TIME_UNIT);
                cancelScheduledSync();
                remainingSyncPause = remainingTime;
                log.debug("Player logged out, pausing sync timer. {} {} remaining.", remainingSyncPause, PAUSE_TIME_UNIT.toString().toLowerCase());
            }
        } else if (event.getGameState() == GameState.LOGGED_IN) {
            if (remainingSyncPause > 0) {
                log.debug("Resuming sync with {} {} remaining.", remainingSyncPause, PAUSE_TIME_UNIT.toString().toLowerCase());
                scheduleSyncWithDelay(remainingSyncPause, PAUSE_TIME_UNIT);
            } else {
                resetAutoSyncTimer();
            }
        }
    }

    /**
     * Called when a rapid sync is requested.
     * Cancels any existing scheduled sync and schedules a sync after 3 seconds.
     */
    public synchronized void startRapidSync() {
        log.debug("Starting rapid sync...");
        cancelScheduledSync();
        // Schedule a sync in 3 seconds, then resume normal cycle
        autoSyncFuture = scheduledExecutorService.schedule(() -> {
            performSync();
            scheduleNextSync();
        }, RAPID_SYNC_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Resets the auto-sync cycle.
     */
    public synchronized void resetAutoSyncTimer() {
        if (!isEnabled()) {
            log.debug("Auto-sync is disabled; ignoring reset request.");
            return;
        }

        scheduleNextSync();
    }

    /**
     * Schedules the next sync after the configured auto-sync interval.
     */
    private synchronized void scheduleNextSync() {
        log.debug("Next sync scheduled in {} minutes", AUTO_SYNC_MINUTES);
        cancelScheduledSync();
        autoSyncFuture = scheduledExecutorService.schedule(() -> {
            performSync();
            scheduleNextSync();
        }, AutoSyncScheduler.AUTO_SYNC_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Schedules a sync after a specific delay.
     * Any existing future or pause state is cancelled.
     *
     * @param delay The amount of time to wait before syncing, in nanoseconds.
     */
    private synchronized void scheduleSyncWithDelay(long delay, TimeUnit unit) {
        cancelScheduledSync();
        autoSyncFuture = scheduledExecutorService.schedule(() -> {
            performSync();
            scheduleNextSync();
        }, delay, unit);
    }

    /**
     * Cancels any scheduled sync and clears the pause state.
     */
    private synchronized void cancelScheduledSync() {
        if (autoSyncFuture != null) {
            autoSyncFuture.cancel(false);
            autoSyncFuture = null;
        }
        remainingSyncPause = 0;
    }

    /**
     * Performs the actual sync, ensuring only one sync runs at a time.
     */
    private void performSync() {
        if (!isSyncing.compareAndSet(false, true)) {
            log.debug("Sync already in progress, skipping...");
            return;
        }

        log.debug("Syncing profile...");
        try {
            plugin.updateProfileAsync(true);
        } finally {
            isSyncing.set(false);
        }
    }
}