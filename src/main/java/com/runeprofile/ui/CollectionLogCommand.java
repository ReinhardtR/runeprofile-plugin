package com.runeprofile.ui;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.autosync.BossRecordsService;
import com.runeprofile.data.BossRecords;
import com.runeprofile.data.CollectionLogCounter;
import com.runeprofile.data.CollectionLogItem;
import com.runeprofile.data.CollectionLogPage;
import com.runeprofile.utils.AccountHash;
import com.runeprofile.utils.PlayerState;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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

    @Inject
    private BossRecordsService bossRecordsService;

    // A middle dot, in the chat's normal text colour. Colours follow RuneLite's chat command
    // convention (like !kc and !pb): the page name and numbers use the player's HIGHLIGHT chat
    // colour, everything else NORMAL, so each player's Chat Color settings apply.
    static final String SEPARATOR = " \u00B7 ";

    // How long an outgoing "!log" may be held back while the sender's kill counts upload.
    private static final long SUBMIT_TIMEOUT_SECONDS = 3;

    // After a failed upload, "!log" messages go out without uploading for this long.
    private static final long RETRY_AFTER_FAILURE_MILLIS = TimeUnit.MINUTES.toMillis(1);

    // What the last "!log" uploaded, so repeated commands don't re-send unchanged values.
    private volatile BossRecords lastSubmittedRecords;

    // The last "!log" upload, while it may still be running.
    private volatile CompletableFuture<Void> submitInFlight;

    private volatile long lastFailedSubmitMillis;

    private final Map<Integer, Integer> loadedItemIcons = new HashMap<>();
    private final String COLLECTION_LOG_COMMAND = "!log";
    private final Pattern COLLECTION_LOG_COMMAND_PATTERN = Pattern.compile("^(" + COLLECTION_LOG_COMMAND + ")\\s+([a-zA-Z]+(?: [a-zA-Z]+)*)$", Pattern.CASE_INSENSITIVE);

    public void startUp() {
        eventBus.register(this);

        if (config.enableLogCommand()) {
            chatCommandManager.registerCommand(COLLECTION_LOG_COMMAND, this::executeLogCommand, this::submitBossRecords);
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
            chatCommandManager.registerCommand(COLLECTION_LOG_COMMAND, this::executeLogCommand, this::submitBossRecords);
        } else {
            chatCommandManager.unregisterCommand(COLLECTION_LOG_COMMAND);
        }
    }

    /**
     * Runs when the local player sends a "!log" message, before it goes out. Uploads their current
     * kill counts and personal bests so everyone who looks the page up sees fresh values, then lets
     * the message through. Returning false sends the message right away.
     *
     * Repeated commands don't pile up requests: an upload still underway is waited on rather than
     * repeated, unchanged values aren't re-sent, and a failed upload isn't retried for a minute.
     */
    private boolean submitBossRecords(ChatInput chatInput, String message) {
        if (!COLLECTION_LOG_COMMAND_PATTERN.matcher(message).matches()) {
            return false;
        }

        // Seasonal and other non-standard worlds keep their own Chat Commands values, which must not
        // land on the main account's profile.
        if (!PlayerState.isValidPlayerState(client)) {
            return false;
        }

        String accountHash = AccountHash.getHashed(client);
        if (accountHash == null) {
            return false;
        }

        CompletableFuture<Void> inFlight = submitInFlight;
        if (inFlight != null && !inFlight.isDone()) {
            holdUntil(chatInput, inFlight);
            return true;
        }

        if (System.currentTimeMillis() - lastFailedSubmitMillis < RETRY_AFTER_FAILURE_MILLIS) {
            return false;
        }

        BossRecords records = bossRecordsService.collect();
        if (records.isEmpty() || Objects.equals(records, lastSubmittedRecords)) {
            return false;
        }

        CompletableFuture<Void> submit = runeProfileApiClient.updateBossRecordsAsync(accountHash, records);
        submitInFlight = submit;
        submit.whenComplete((result, ex) -> {
            if (ex != null) {
                log.debug("Failed to submit boss records for !log", ex);
                lastFailedSubmitMillis = System.currentTimeMillis();
            } else {
                lastSubmittedRecords = records;
            }
        });
        holdUntil(chatInput, submit);
        return true;
    }

    /** Lets the held message go out once the upload finishes, or after the timeout. */
    private static void holdUntil(ChatInput chatInput, CompletableFuture<Void> upload) {
        // A copy, so the timeout doesn't fail the upload for another message waiting on it.
        upload.copy()
                .orTimeout(SUBMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((result, ex) -> chatInput.resume());
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

        // "!log missing <page>" shows only the items the player is still missing from the page.
        // No collection log page or alias starts with the word "missing", so a leading "missing "
        // token is unambiguously the keyword rather than part of a page name.
        boolean missingOnly = false;
        if (pageName.regionMatches(true, 0, "missing ", 0, "missing ".length())) {
            missingOnly = true;
            pageName = pageName.substring("missing ".length()).trim();
            if (pageName.isEmpty()) {
                log.debug("Invalid page name");
                return;
            }
        }

        final boolean showMissing = missingOnly;

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
                int obtainedItemsCount = (int) items.stream().filter(item -> item.getQuantity() > 0).count();
                int totalItemsCount = items.size();
                int missingItemsCount = totalItemsCount - obtainedItemsCount;

                ChatMessageBuilder reply = new ChatMessageBuilder()
                        .append(ChatColorType.HIGHLIGHT)
                        .append(page.getName())
                        .append(ChatColorType.NORMAL);
                if (!showMissing) {
                    reply.append(" (" + obtainedItemsCount + "/" + totalItemsCount + ")");
                } else if (missingItemsCount == 0) {
                    reply.append(" - completed (" + totalItemsCount + "/" + totalItemsCount + ")");
                } else {
                    reply.append(" - missing (" + missingItemsCount + "/" + totalItemsCount + ")");
                }

                appendCounters(reply, page.getChatCounters());

                boolean firstItem = true;
                for (CollectionLogItem item : items) {
                    // Obtained view keeps items with quantity > 0; missing view keeps quantity < 1.
                    if (showMissing ? item.getQuantity() >= 1 : item.getQuantity() < 1) continue;

                    reply.append(ChatColorType.NORMAL).append(firstItem ? SEPARATOR : "  ");
                    firstItem = false;

                    reply.img(loadedItemIcons.get(item.getId()));
                    if (config.enableExtendedItemNames()) {
                        reply.append(" " + itemManager.getItemComposition(item.getId()).getName());
                    }
                    if (item.getQuantity() > 1) {
                        reply.append(" x" + item.getQuantity());
                    }
                }

                final String replacementMessage = reply.build();
                updateChatMessage(chatMessage, replacementMessage);
            });
        });
    }

    /**
     * Appends the known kill counts among the given lines, in their order, each after a separator,
     * number first, e.g. " \u00B7 312 KC \u00B7 45 HMT KC" with the numbers highlighted. Personal bests are left
     * out of the chat reply. Appends nothing when none are known.
     */
    static void appendCounters(ChatMessageBuilder builder, List<CollectionLogCounter> counters) {
        if (counters == null) {
            return;
        }

        for (CollectionLogCounter counter : counters) {
            Double value = counter.getValue();
            if (value == null || counter.isPersonalBest()) continue;

            builder.append(ChatColorType.NORMAL)
                    .append(SEPARATOR)
                    .append(ChatColorType.HIGHLIGHT)
                    .append(String.format("%,d", value.longValue()))
                    .append(ChatColorType.NORMAL)
                    .append(" " + counter.getDisplayLabel());
        }
    }

    /** {@link #appendCounters} on its own, as RuneLite-format text. */
    static String formatCounters(List<CollectionLogCounter> counters) {
        ChatMessageBuilder builder = new ChatMessageBuilder();
        appendCounters(builder, counters);
        return builder.build();
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
        chatMessage.getMessageNode().setRuneLiteFormatMessage(message);
        client.runScript(ScriptID.BUILD_CHATBOX);
    }
}
