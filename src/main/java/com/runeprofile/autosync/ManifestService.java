package com.runeprofile.autosync;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.data.Manifest;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Keeps a cached copy of the backend manifest. The manifest drives several
 * features (command suggestions, combat achievement varps and the special
 * valuable-drop overrides), so it is refreshed for the lifetime of the plugin
 * regardless of individual feature toggles.
 */
@Slf4j
@Singleton
public class ManifestService {
    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfileApiClient apiClient;

    private ScheduledFuture<?> manifestRefreshFuture;
    private Manifest cachedManifest;

    public void startUp() {
        startManifestRefreshTask();
    }

    public void shutDown() {
        stopManifestRefreshTask();
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
