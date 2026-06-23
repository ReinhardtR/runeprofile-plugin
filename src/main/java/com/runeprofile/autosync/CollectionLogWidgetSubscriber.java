package com.runeprofile.autosync;

import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class CollectionLogWidgetSubscriber {
    private static final int COLLECTION_DELAYED_TRANSMIT = 4100;
    private static final int COLLECTION_LOG_SETUP = 7797;
    private static final int COLLECTION_INIT_SCRIPT = 2240;

    @Inject
    private EventBus eventBus;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private Client client;

    @Inject
    private PlayerDataService playerDataService;

    @Inject
    private RuneProfilePlugin plugin;

    private boolean isManualSync = false;
    private boolean isAutoClogRetrieval = false;
    private int tickCollectionLogScriptFired = -1;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    public void reset() {
        isManualSync = false;
        isAutoClogRetrieval = false;
        tickCollectionLogScriptFired = -1;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState != GameState.HOPPING && gameState != GameState.LOGGED_IN) {
            reset();
        }
    }

    // Code from: WikiSync
    // Repository: https://github.com/weirdgloop/WikiSync
    // License: BSD 2-Clause License
    @Subscribe
    public void onGameTick(GameTick gameTick) {
        int tick = client.getTickCount();
        boolean hasClogScriptFired = tickCollectionLogScriptFired != -1;
        boolean hasBufferPassed = tickCollectionLogScriptFired + 2 < tick;
        if (hasClogScriptFired && hasBufferPassed) {
            tickCollectionLogScriptFired = -1;
            log.debug("Clog items script has fired, is manual sync: {}", isManualSync);
            if (isManualSync) {
                scheduledExecutorService.execute(() -> plugin.updateProfileAsync(false, "manual-update-button-clog"));
                isManualSync = false;
            }
            isAutoClogRetrieval = false;
        }
    }

    // Code from: WikiSync
    // Repository: https://github.com/weirdgloop/WikiSync
    // License: BSD 2-Clause License
    @Subscribe
    public void onScriptPreFired(ScriptPreFired preFired) {
        if (preFired.getScriptId() != COLLECTION_DELAYED_TRANSMIT) {
            return;
        }

        // prevent reacting to scripts fired when opened from adventure log
        // e.g. other plugins might fire the collection log script when viewing other players' collection logs
        boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
        if (isOpenedFromAdventureLog) {
            return;
        }

        tickCollectionLogScriptFired = client.getTickCount();

        Object[] args = preFired.getScriptEvent().getArguments();
        int itemId = (int) args[1];
        int quantity = (int) args[2];
        log.debug("Item id: {}, Quantity: {}", itemId, quantity);

        playerDataService.storeItem(itemId, quantity);
    }

    // When the collection log is opened, automatically make the server transmit every clog
    // entry so the full collection log is stored for the next auto-sync (no profile update is
    // triggered here). The menuAction "Search" op is what requests the data from the server;
    // re-running the collection log init script then resets the view, closing the search again.
    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        if (scriptPostFired.getScriptId() != COLLECTION_LOG_SETUP) {
            return;
        }

        // disallow retrieving from the adventure log, to avoid storing another player's
        // collection log while viewing it through the POH adventure log.
        boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
        if (isOpenedFromAdventureLog) {
            playerDataService.clearItems();
            return;
        }

        // guard against re-triggering from the init script we run below (which re-fires setup)
        if (isAutoClogRetrieval) {
            return;
        }

        isAutoClogRetrieval = true;
        client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(COLLECTION_INIT_SCRIPT);
    }

    public void triggerManualSync() {
        isManualSync = true;
    }

    // fail-safe to clear stored items if the collection log is opened from the adventure log
    @Subscribe
    public void onVarbitChanged(VarbitChanged varbitChanged) {
        if (varbitChanged.getVarbitId() == VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) {
            boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
            if (isOpenedFromAdventureLog) {
                log.debug("Collection log opened from adventure log, clearing stored items to avoid incorrect updates.");
                playerDataService.clearItems();
            }
        }
    }
}
