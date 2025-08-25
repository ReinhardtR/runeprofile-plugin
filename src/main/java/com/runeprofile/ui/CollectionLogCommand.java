package com.runeprofile.ui;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.CollectionLogItem;
import com.runeprofile.data.CollectionLogPage;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CollectionLogCommand {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private RuneProfileApiClient runeProfileApiClient;

    private final Map<Integer, Integer> loadedItemIcons = new HashMap<>();
    private final String COLLECTION_LOG_COMMAND = "!log";
    private final Pattern COLLECTION_LOG_COMMAND_PATTERN = Pattern.compile("^(" + COLLECTION_LOG_COMMAND + ")\\s+([a-zA-Z]+(?: [a-zA-Z]+)*)$", Pattern.CASE_INSENSITIVE);

    public void startUp() {
        eventBus.register(this);

        if (config.enableLogCommand()) {
            chatCommandManager.registerCommand(COLLECTION_LOG_COMMAND, this::executeLogCommand);
        }
    }

    public void shutDown() {
        eventBus.unregister(this);

        chatCommandManager.unregisterCommand(COLLECTION_LOG_COMMAND);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) return;

        if (config.enableLogCommand()) {
            chatCommandManager.registerCommand(COLLECTION_LOG_COMMAND, this::executeLogCommand);
        } else {
            chatCommandManager.unregisterCommand(COLLECTION_LOG_COMMAND);
        }
    }

    private void executeLogCommand(ChatMessage chatMessage, String message) {
        log.debug("Executing log command: {}", message);
        Matcher commandMatcher = COLLECTION_LOG_COMMAND_PATTERN.matcher(message);
        if (!commandMatcher.matches()) {
            log.debug("Invalid command format");
            return;
        }

        String pageName = commandMatcher.group(2);
        if (pageName == null || pageName.isEmpty()) {
            log.debug("Invalid page name");
            return;
        }

        String senderName = chatMessage.getType().equals(ChatMessageType.PRIVATECHATOUT)
                ? client.getLocalPlayer().getName()
                : Text.sanitize(chatMessage.getName());

        runeProfileApiClient.getCollectionLogPage(senderName, pageName).whenComplete((page, ex) -> {
            clientThread.invokeLater(() -> {
                if (ex != null) {
                    final String errorMessage = Utils.getApiErrorMessage(ex, "Failed to load collection log page.");
                    updateChatMessage(chatMessage, errorMessage);
                    return;
                }

                loadPageIcons(page);

                List<CollectionLogItem> items = page.getItems();
                StringBuilder itemBuilder = new StringBuilder();
                for (CollectionLogItem item : items) {
                    if (item.getQuantity() < 1) continue;

                    String itemString = "<img=" + loadedItemIcons.get(item.getId()) + ">";

                    if (config.enableExtendedItemNames()) {
                        String itemName = itemManager.getItemComposition(item.getId()).getName();
                        itemString += " " + itemName + " ";
                    }

                    if (item.getQuantity() > 1) {
                        itemString += "x" + item.getQuantity();
                    }
                    itemString += "  ";
                    itemBuilder.append(itemString);
                }

                int obtainedItemsCount = (int) items.stream().filter(item -> item.getQuantity() > 0).count();
                int totalItemsCount = items.size();

                final String replacementMessage = page.getName() + " " + "(" + obtainedItemsCount + "/" + totalItemsCount + ")" + " : " + itemBuilder;
                updateChatMessage(chatMessage, replacementMessage);
            });
        });
    }

    private void loadPageIcons(CollectionLogPage page) {
        List<CollectionLogItem> itemsToLoad = page.getItems()
                .stream()
                .filter(item -> !loadedItemIcons.containsKey(item.getId()))
                .collect(Collectors.toList());

        final IndexedSprite[] modIcons = client.getModIcons();

        final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + itemsToLoad.size());
        int modIconIdx = modIcons.length;

        for (int i = 0; i < itemsToLoad.size(); i++) {
            final CollectionLogItem item = itemsToLoad.get(i);
            final ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
            final BufferedImage image = ImageUtil.resizeImage(itemManager.getImage(itemComposition.getId()), 18, 16);
            final IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
            final int spriteIndex = modIconIdx + i;

            newModIcons[spriteIndex] = sprite;
            loadedItemIcons.put(item.getId(), spriteIndex);
        }

        client.setModIcons(newModIcons);
    }

    private void updateChatMessage(ChatMessage chatMessage, String message) {
        chatMessage.getMessageNode().setValue(message);
        client.runScript(ScriptID.BUILD_CHATBOX);
    }
}
