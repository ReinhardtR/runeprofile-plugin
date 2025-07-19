package com.runeprofile.autosync;

import com.runeprofile.RuneProfilePlugin;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class CollectionLogWidgetSubscriber {
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

    @Setter
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
        isAutoClogRetrieval = false;
        isManualSync = false;
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
                scheduledExecutorService.execute(() -> plugin.updateProfileAsync(false));
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
        if (preFired.getScriptId() == 4100) {
            tickCollectionLogScriptFired = client.getTickCount();

            Object[] args = preFired.getScriptEvent().getArguments();
            int itemId = (int) args[1];
            int quantity = (int) args[2];

            playerDataService.storeItem(itemId, quantity);
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired scriptPostFired) {
        final int COLLECTION_LOG_SETUP = 7797;
        if (scriptPostFired.getScriptId() == COLLECTION_LOG_SETUP) {
            if (isAutoClogRetrieval) {
                return;
            }

            // disallow updating from the adventure log, to avoid players updating their profile
            // while viewing other players collection logs using the POH adventure log.
            boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
            if (isOpenedFromAdventureLog) return;

            isAutoClogRetrieval = true;
            client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
            client.runScript(2240);
        }
    }
}
