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
 * features (command suggestions, quest ids, combat achievement varps and the
 * special valuable-drop overrides), so it is refreshed for the lifetime of the
 * plugin regardless of individual feature toggles.
 * <p>
 * Each refresh schedules the next one rather than running at a fixed rate, so a
 * failed fetch can retry with exponential backoff instead of waiting out the
 * full hourly period.
 * <p>
 * Backoff is deliberately asymmetric. Until a manifest has been cached the
 * plugin is degraded - combat achievements and valuable-drop overrides are
 * skipped entirely, and quests fall back to RuneLite's Quest enum - so retries
 * start after a couple of seconds and never stretch beyond a few minutes. Once
 * a copy is cached the data is merely stale rather than missing, so retries
 * start slower and are allowed to grow into the normal hourly cadence.
 */
@Slf4j
@Singleton
public class ManifestService {
    private static final long REFRESH_MINUTES = 60;
    /**
     * First retry while no manifest is cached. The plugin is degraded until one
     * arrives, and the common failure here is a transient blip at login, so this
     * is short enough to recover before the player notices.
     */
    private static final long INITIAL_RETRY_WITHOUT_MANIFEST_SECONDS = 2;
    /** First retry once a manifest is cached: the copy is only stale, so there is no rush. */
    private static final long INITIAL_RETRY_WITH_MANIFEST_SECONDS = 30;
    /** Cap while no manifest is cached: keep trying to get the plugin to a working state. */
    private static final long MAX_RETRY_WITHOUT_MANIFEST_MINUTES = 5;
    /** Cap once a manifest is cached: settle back into the normal refresh cadence. */
    private static final long MAX_RETRY_WITH_MANIFEST_MINUTES = REFRESH_MINUTES;
    /** Fraction each delay is randomly stretched or shrunk by. */
    private static final double JITTER_RATIO = 0.25;
    /** Keeps the exponential shift well inside long range; the cap applies anyway. */
    private static final int MAX_BACKOFF_DOUBLINGS = 20;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfileApiClient apiClient;

    private ScheduledFuture<?> manifestRefreshFuture;

    // Written on the HTTP callback thread, read from the client thread.
    private volatile Manifest cachedManifest;
    private volatile int consecutiveFailures;
    // Guards against an in-flight fetch rescheduling itself after shutDown().
    private volatile boolean running;

    public void startUp() {
        running = true;
        consecutiveFailures = 0;
        scheduleRefresh(0, TimeUnit.MILLISECONDS);
    }

    public void shutDown() {
        running = false;
        cancelScheduledRefresh();
    }

    private synchronized void scheduleRefresh(long delay, TimeUnit unit) {
        if (!running) return;
        cancelScheduledRefresh();
        manifestRefreshFuture = scheduledExecutorService.schedule(this::refreshManifest, delay, unit);
    }

    private synchronized void cancelScheduledRefresh() {
        if (manifestRefreshFuture != null) {
            // Not interrupting: the task only kicks off an async request, and the
            // in-flight fetch is already made harmless by the running flag.
            manifestRefreshFuture.cancel(false);
            manifestRefreshFuture = null;
        }
    }

    private void refreshManifest() {
        // whenComplete rather than thenAccept/exceptionally so that success and
        // failure share one handler and cannot both schedule a follow-up.
        apiClient.getManifest().whenComplete((manifest, ex) -> {
            if (ex != null) {
                onRefreshFailed(ex.toString());
                return;
            }
            if (manifest == null) {
                onRefreshFailed("API returned no manifest");
                return;
            }

            cachedManifest = manifest;
            consecutiveFailures = 0;
            log.debug("Manifest refreshed successfully (version {})", manifest.getVersion());
            scheduleRefresh(jitter(TimeUnit.MINUTES.toMillis(REFRESH_MINUTES)), TimeUnit.MILLISECONDS);
        });
    }

    private void onRefreshFailed(String reason) {
        consecutiveFailures++;
        long delayMillis = retryDelayMillis();
        log.debug("Failed to refresh manifest (attempt {}): {}. Retrying in {}s.",
                consecutiveFailures, reason, TimeUnit.MILLISECONDS.toSeconds(delayMillis));
        scheduleRefresh(delayMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Exponential backoff whose starting point and ceiling both depend on whether a
     * manifest is already cached - missing data is urgent, stale data is not.
     */
    private long retryDelayMillis() {
        boolean cached = cachedManifest != null;
        long initial = TimeUnit.SECONDS.toMillis(cached
                ? INITIAL_RETRY_WITH_MANIFEST_SECONDS
                : INITIAL_RETRY_WITHOUT_MANIFEST_SECONDS);
        long cap = TimeUnit.MINUTES.toMillis(cached
                ? MAX_RETRY_WITH_MANIFEST_MINUTES
                : MAX_RETRY_WITHOUT_MANIFEST_MINUTES);

        int doublings = Math.min(Math.max(consecutiveFailures - 1, 0), MAX_BACKOFF_DOUBLINGS);
        return jitter(Math.min(initial << doublings, cap));
    }

    /**
     * Spreads a delay by +/-{@link #JITTER_RATIO} so that an outage - which fails
     * every client at once - does not resolve into a synchronised burst of
     * retries when the API recovers.
     */
    private static long jitter(long delayMillis) {
        double factor = 1 + ((Math.random() * 2 - 1) * JITTER_RATIO);
        return Math.max(1, (long) (delayMillis * factor));
    }

    public @Nullable Manifest getManifest() {
        return cachedManifest;
    }
}
