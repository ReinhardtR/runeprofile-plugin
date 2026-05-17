package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.utils.PlayerState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CombatAchievementSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    private static final Pattern CA_TASK_COMPLETED_PATTERN = Pattern.compile(
            "Congratulations, you've completed an? (?:\\w+) combat task:"
    );

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        if (!config.autosyncProfile() || !PlayerState.isValidPlayerState(client)) {
            return;
        }

        if (CA_TASK_COMPLETED_PATTERN.matcher(event.getMessage()).find()) {
            log.debug("Combat achievement task completed: {}", event.getMessage());
            autoSyncScheduler.startRapidSync("combat-achievement-completed");
        }
    }
}
