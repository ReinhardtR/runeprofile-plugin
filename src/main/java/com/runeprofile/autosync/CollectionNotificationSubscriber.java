package com.runeprofile.autosync;

import com.google.common.collect.ImmutableSet;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.utils.ItemSearcher;
import com.runeprofile.utils.PlayerState;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Singleton
public class CollectionNotificationSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    @Inject
    private PlayerDataService playerDataService;

    @Inject
    private ItemSearcher itemSearcher;

    private static final Pattern NEW_ITEM_REGEX = Pattern.compile("New item added to your collection log: (?<itemName>(.*))");
    private static final int POPUP_PREFIX_LENGTH = "New item:".length();

    private final AtomicBoolean popupStarted = new AtomicBoolean(false);

    // Items that can't be synced via collection log notifications because of non-unique names
    private final static Set<String> IGNORED_ITEMS = ImmutableSet.of("Medallion fragment", "Ancient page");

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isEnabled() {
        return config.autosyncProfile() && PlayerState.isValidPlayerState(client);
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    public void reset() {
        popupStarted.set(false);
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState != GameState.HOPPING && gameState != GameState.LOGGED_IN) {
            reset();
        }
    }

    // Code from: DinkPlugin
    // Repository: https://github.com/pajlads/DinkPlugin
    // License: BSD 2-Clause License
    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE || client.getVarbitValue(VarbitID.OPTION_COLLECTION_NEW_ITEM) != 1 || !isEnabled()) {
            return;
        }

        Matcher matcher = NEW_ITEM_REGEX.matcher(chatMessage.getMessage());
        if (matcher.find()) {
            String itemName = matcher.group("itemName");
            handleAutosync(itemName);
            log.debug("Chat message found for new item: {}", itemName);
        }
    }

    // Code from: DinkPlugin
    // Repository: https://github.com/pajlads/DinkPlugin
    // License: BSD 2-Clause License
    @Subscribe
    public void onScriptPreFired(ScriptPreFired event) {
        final int scriptId = event.getScriptId();
        if (scriptId == ScriptID.NOTIFICATION_START) {
            popupStarted.set(true);
        } else if (scriptId == ScriptID.NOTIFICATION_DELAY) {
            String topText = client.getVarcStrValue(VarClientID.NOTIFICATION_TITLE);
            if (popupStarted.getAndSet(false) && "Collection log".equalsIgnoreCase(topText) && isEnabled()) {
                String bottomText = Utils.sanitize(client.getVarcStrValue(VarClientID.NOTIFICATION_MAIN));
                String itemName = bottomText.substring(POPUP_PREFIX_LENGTH).trim();
                handleAutosync(itemName);
                log.debug("Script fired for new item: {}", itemName);
            }
        }
    }

    private void handleAutosync(String itemName) {
        if (IGNORED_ITEMS.contains(itemName)) {
            log.debug("Ignoring collection log item with non-unique name: {}", itemName);
            return;
        }

        Integer itemId = itemSearcher.findItemId(itemName);
        if (itemId == null) {
            log.debug("Failed to find item ID for: {}", itemName);
            return;
        }
        playerDataService.storeItem(itemId, 1);
        autoSyncScheduler.startRapidSync();
    }
}
