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
public class AchievementDiarySubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    private static final Pattern DIARY_COMPLETION_PATTERN = Pattern.compile(
            "Congratulations! You have completed all of the (?<difficulty>.+) tasks in the (?<area>.+) area"
    );

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.MESBOX) {
            return;
        }

        if (!config.autosyncProfile() || !PlayerState.isValidPlayerState(client)) {
            return;
        }

        if (DIARY_COMPLETION_PATTERN.matcher(event.getMessage()).find()) {
            log.debug("Achievement diary tier completion detected: {}", event.getMessage());
            autoSyncScheduler.startRapidSync("achievement-diary-completed");
        }
    }
}
