package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
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

@Slf4j
@Singleton
public class AutoSyncScheduler {
    @Inject
    private EventBus eventBus;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private Client client;

    @Inject
    private RuneProfilePlugin plugin;

    @Inject
    private RuneProfileConfig config;

    // A reference to the pending auto-sync task
    private ScheduledFuture<?> autoSyncFuture;

    private static final int RAPID_SYNC_SECONDS = 3;
    private static final int AUTO_SYNC_MINUTES = 1;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isEnabled() {
        return config.autosyncProfile();
    }

    public void startUp() {
        eventBus.register(this);

        if (!isEnabled()) {
            return;
        }

        start();
    }

    public void shutDown() {
        eventBus.unregister(this);

        stop();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) return;

        if (config.autosyncProfile()) {
            start();
        } else {
            stop();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (!isEnabled()) return;

        if (event.getGameState() == GameState.LOGGED_IN) {
            start();
        }

        if (event.getGameState() == GameState.LOGIN_SCREEN && client.getLocalPlayer() != null) {
            plugin.updateProfileAsync(true);
            stop();
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

    private synchronized void start() {
        log.debug("Starting auto-sync scheduler...");
        // Schedule the first auto-sync
        resetAutoSyncTimer();
    }

    private synchronized void stop() {
        log.debug("Stopping auto-sync scheduler...");
        if (autoSyncFuture != null) {
            autoSyncFuture.cancel(true);
            autoSyncFuture = null;
        }
    }

    private synchronized void syncAndResetTimer() {
        log.debug("Syncing profile...");
        resetAutoSyncTimer();
        plugin.updateProfileAsync(true);
    }
}