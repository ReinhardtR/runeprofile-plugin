package com.runeprofile.autosync;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.utils.AccountHash;
import com.runeprofile.utils.PlayerState;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatically creates a profile for accounts that don't have one yet.
 * On login (or plugin startup while already logged in), checks if the account
 * has a profile; if not, triggers an initial profile sync.
 */
@Slf4j
@Singleton
public class ProfileCreationService {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private RuneProfileApiClient apiClient;

    @Inject
    private RuneProfilePlugin plugin;

    // Accounts already checked this session, so each account is only checked once
    private final Set<String> checkedAccountIds = ConcurrentHashMap.newKeySet();

    // Prevent overlapping existence checks
    private final AtomicBoolean checkInFlight = new AtomicBoolean(false);

    // Delay before the initial sync, so login-time data (clan channels, etc.) has loaded
    private static final int INITIAL_SYNC_DELAY_SECONDS = 60;

    // The scheduled initial sync, cancelled if the player logs out while waiting
    private ScheduledFuture<?> pendingInitialSync;
    private String pendingInitialSyncAccountId;

    public void startUp() {
        eventBus.register(this);
        // The plugin can be installed from the plugin hub while already logged in,
        // in which case no GameStateChanged event will fire - check right away.
        scheduleCheck(false);
    }

    public void shutDown() {
        eventBus.unregister(this);
        cancelPendingInitialSync();
        checkedAccountIds.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        GameState state = event.getGameState();
        if (state == GameState.LOGGED_IN) {
            // delay the initial sync, login-time data (clan channels, etc.) loads a while after login
            scheduleCheck(true);
        } else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING) {
            cancelPendingInitialSync();
        }
    }

    private synchronized void cancelPendingInitialSync() {
        if (pendingInitialSync == null) return;

        log.debug("Abandoning pending initial sync, player logged out");
        pendingInitialSync.cancel(false);
        pendingInitialSync = null;

        // allow the check to run again on the next login
        if (pendingInitialSyncAccountId != null) {
            checkedAccountIds.remove(pendingInitialSyncAccountId);
            pendingInitialSyncAccountId = null;
        }
    }

    private void scheduleCheck(boolean delayInitialSync) {
        if (!config.autosyncProfile()) return;

        clientThread.invokeLater(() -> {
            if (!PlayerState.isValidPlayerState(client)) return;

            String accountId = AccountHash.getHashed(client);
            if (accountId == null || checkedAccountIds.contains(accountId)) return;

            if (!checkInFlight.compareAndSet(false, true)) return;

            checkProfileExists(accountId, delayInitialSync);
        });
    }

    private void checkProfileExists(String accountId, boolean delayInitialSync) {
        log.debug("Checking if a profile exists for the current account");
        apiClient.getAccountAsync(accountId).whenComplete((account, ex) -> {
            try {
                if (ex == null) {
                    log.debug("Profile already exists");
                    checkedAccountIds.add(accountId);
                    return;
                }

                if (Utils.isAccountNotFound(ex)) {
                    checkedAccountIds.add(accountId);
                    if (delayInitialSync) {
                        scheduleInitialSync(accountId);
                    } else {
                        runInitialSync(accountId);
                    }
                } else {
                    // Transient error: don't mark as checked, so it is retried on the next login
                    log.warn("Profile existence check failed", ex);
                }
            } finally {
                checkInFlight.set(false);
            }
        });
    }

    /**
     * Schedules the initial profile sync a while after login, so login-time data
     * (clan channels, group ironman info, etc.) has had time to load. Abandoned
     * if the player logs out or switches accounts in the meantime, in which case
     * the check is re-armed for the next login.
     */
    private synchronized void scheduleInitialSync(String accountId) {
        log.debug("No profile found, scheduling initial sync in {} seconds", INITIAL_SYNC_DELAY_SECONDS);

        pendingInitialSyncAccountId = accountId;
        pendingInitialSync = scheduledExecutorService.schedule(() -> {
            synchronized (this) {
                pendingInitialSync = null;
                pendingInitialSyncAccountId = null;
            }
            runInitialSync(accountId);
        }, INITIAL_SYNC_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void runInitialSync(String accountId) {
        clientThread.invokeLater(() -> {
            if (!PlayerState.isValidPlayerState(client) || !accountId.equals(AccountHash.getHashed(client))) {
                log.debug("Abandoning initial sync, player logged out or switched accounts");
                checkedAccountIds.remove(accountId);
                return;
            }

            try {
                plugin.updateProfileAsync(true, "initial-profile-creation");
            } catch (IllegalStateException stateEx) {
                log.warn("Could not run initial profile sync", stateEx);
            }
        });
    }
}
