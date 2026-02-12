package com.runeprofile.autosync;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.Manifest;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;


import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Singleton
public class ManifestService {
    @Inject
    private EventBus eventBus;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private RuneProfileApiClient apiClient;

    private ScheduledFuture<?> manifestRefreshFuture;
    private Manifest cachedManifest;

    public void startUp() {
        eventBus.register(this);
        startManifestRefreshTask();
    }

    public void shutDown() {
        eventBus.unregister(this);
        stopManifestRefreshTask();
    }

    public void reset() {
        stopManifestRefreshTask();
        startManifestRefreshTask();
    }

    public boolean isEnabled() {
        return config.commandSuggestionOverlay();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) return;

        if (isEnabled()) {
            reset();
        } else {
            shutDown();
        }
    }

    public void startManifestRefreshTask() {
        manifestRefreshFuture = scheduledExecutorService.scheduleAtFixedRate(
                this::refreshManifest,
                0,
                60,
                TimeUnit.MINUTES
        );
    }

    public void stopManifestRefreshTask() {
        if (manifestRefreshFuture != null) {
            manifestRefreshFuture.cancel(true);
            manifestRefreshFuture = null;
        }
    }

    public void refreshManifest() {
        apiClient.getManifest().thenAccept(manifest -> {
            if (manifest != null) {
                cachedManifest = manifest;
                log.debug("Manifest refreshed successfully");
            } else {
                log.debug("Received null manifest from API");
            }
        }).exceptionally(ex -> {
            log.debug("Failed to refresh manifest", ex);
            return null;
        });
    }

    public @Nullable Manifest getManifest() {
        return cachedManifest;
    }
}
