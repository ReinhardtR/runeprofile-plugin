package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.utils.PlayerState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class QuestCompletedSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() != InterfaceID.QUEST_COMPLETED) {
            return;
        }

        if (!config.autosyncProfile() || !PlayerState.isValidPlayerState(client)) {
            return;
        }

        // Delay by 1 tick to ensure quest varbits have settled
        clientThread.invokeLater(() -> {
            log.debug("Quest completion scroll detected, scheduling rapid sync");
            autoSyncScheduler.startRapidSync("quest-completed");
        });
    }
}
