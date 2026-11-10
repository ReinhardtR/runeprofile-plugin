package com.runeprofile.pluginmessage;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.data.CollectionLogItem;
import com.runeprofile.data.CollectionLogPage;
import com.runeprofile.events.ProfileUpdated;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets other plugins read the local player's collection log through RuneLite's PluginMessage bus.
 * <p>
 * Request:
 * <pre>
 * eventBus.post(new PluginMessage("runeprofile", "collection-log-request", Map.of(
 *     "page", "Vorkath",       // page name or alias, same as the !log command
 *     "requestId", "anything"  // optional, echoed back unchanged
 * )));
 * </pre>
 * Response, posted on the client thread as {@code PluginMessage("runeprofile", "collection-log")}:
 * <pre>
 * success   boolean
 * page      String              the page as requested
 * requestId Object              only if the request had one
 * name      String              canonical page name           (success only)
 * obtained  int                 items with quantity > 0       (success only)
 * total     int                 items on the page             (success only)
 * items     List&lt;Map&gt;           {id: int, name: String, quantity: int} (success only)
 * error     String              why it failed                 (failure only)
 * </pre>
 * Answers come from the player's synced RuneProfile, so the player needs a profile and the page
 * reflects the last sync. Results are cached and refreshed after each profile sync, so plugins
 * may ask as often as they like.
 */
@Slf4j
@Singleton
public class CollectionLogPluginMessage {
    public static final String NAMESPACE = "runeprofile";
    public static final String REQUEST_NAME = "collection-log-request";
    public static final String RESPONSE_NAME = "collection-log";

    private static final long SUCCESS_TTL_MS = 5 * 60 * 1000;
    private static final long FAILURE_TTL_MS = 60 * 1000;

    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    @Inject
    private RuneProfileApiClient runeProfileApiClient;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final CompletableFuture<CollectionLogPage> future;
        final long createdAt = System.currentTimeMillis();

        CacheEntry(CompletableFuture<CollectionLogPage> future) {
            this.future = future;
        }

        boolean isExpired() {
            if (!future.isDone()) return false;
            long ttl = future.isCompletedExceptionally() ? FAILURE_TTL_MS : SUCCESS_TTL_MS;
            return System.currentTimeMillis() - createdAt > ttl;
        }
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
        cache.clear();
    }

    @Subscribe
    public void onProfileUpdated(ProfileUpdated event) {
        cache.clear();
    }

    @Subscribe
    public void onPluginMessage(PluginMessage message) {
        if (!NAMESPACE.equals(message.getNamespace()) || !REQUEST_NAME.equals(message.getName())) {
            return;
        }

        Map<String, Object> data = message.getData();
        Object requestId = data.get("requestId");
        Object rawPage = data.get("page");
        String page = rawPage instanceof String ? ((String) rawPage).trim() : "";

        clientThread.invoke(() -> {
            if (page.isEmpty()) {
                respondError(page, requestId, "Missing \"page\".");
                return;
            }

            Player localPlayer = client.getLocalPlayer();
            String username = client.getGameState() == GameState.LOGGED_IN && localPlayer != null
                    ? localPlayer.getName()
                    : null;
            if (username == null || username.isEmpty()) {
                respondError(page, requestId, "Not logged in.");
                return;
            }

            getPage(username, page).whenComplete((result, ex) -> clientThread.invokeLater(() -> {
                if (ex != null) {
                    respondError(page, requestId, Utils.getApiErrorMessage(ex, "Failed to load collection log page."));
                } else {
                    respondPage(page, requestId, result);
                }
            }));
        });
    }

    private CompletableFuture<CollectionLogPage> getPage(String username, String page) {
        String key = username.toLowerCase(Locale.ROOT) + "|" + page.toLowerCase(Locale.ROOT);
        return cache.compute(key, (k, entry) -> {
            if (entry != null && !entry.isExpired()) return entry;
            return new CacheEntry(runeProfileApiClient.getCollectionLogPage(username, page));
        }).future;
    }

    private void respondPage(String page, Object requestId, CollectionLogPage result) {
        List<Map<String, Object>> items = new ArrayList<>();
        int obtained = 0;
        for (CollectionLogItem item : result.getItems()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", item.getId());
            entry.put("name", itemManager.getItemComposition(item.getId()).getName());
            entry.put("quantity", item.getQuantity());
            items.add(entry);
            if (item.getQuantity() > 0) obtained++;
        }

        Map<String, Object> data = baseResponse(page, requestId, true);
        data.put("name", result.getName());
        data.put("obtained", obtained);
        data.put("total", items.size());
        data.put("items", items);
        eventBus.post(new PluginMessage(NAMESPACE, RESPONSE_NAME, data));
    }

    private void respondError(String page, Object requestId, String error) {
        Map<String, Object> data = baseResponse(page, requestId, false);
        data.put("error", error);
        eventBus.post(new PluginMessage(NAMESPACE, RESPONSE_NAME, data));
    }

    private static Map<String, Object> baseResponse(String page, Object requestId, boolean success) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("page", page);
        if (requestId != null) data.put("requestId", requestId);
        return data;
    }
}
