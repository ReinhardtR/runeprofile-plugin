package com.runeprofile.pluginmessage;

import com.google.gson.Gson;
import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.CollectionLogItem;
import com.runeprofile.data.CollectionLogPage;
import com.runeprofile.events.ProfileDeleted;
import com.runeprofile.events.ProfileUpdated;
import com.runeprofile.utils.RuneProfileApiException;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets other plugins read the local player's collection log through RuneLite's PluginMessage bus.
 * The user must opt in with the "Share collection log with plugins" setting.
 * <p>
 * The message contract is documented in docs/external-plugin-messaging.md.
 */
@Slf4j
@Singleton
public class CollectionLogPluginMessage {
    public static final String NAMESPACE = RuneProfilePlugin.CONFIG_GROUP;
    public static final String REQUEST_NAME = "collection-log-request";
    public static final String RESPONSE_NAME = "collection-log";
    public static final String CHANGED_NAME = "collection-log-changed";

    public static final String REASON_DISABLED = "DISABLED";
    public static final String REASON_INVALID_REQUEST = "INVALID_REQUEST";
    public static final String REASON_NOT_LOGGED_IN = "NOT_LOGGED_IN";
    public static final String REASON_PROFILE_NOT_FOUND = "PROFILE_NOT_FOUND";
    public static final String REASON_PAGE_NOT_FOUND = "PAGE_NOT_FOUND";
    public static final String REASON_ERROR = "ERROR";

    private static final String API_CODE_PAGE_NOT_FOUND = "CollectionLogPageNotFound";

    // Failed lookups are retried after this long. Successful ones stay cached until the next
    // profile sync, which is the only way the page can change.
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
    private Gson gson;

    @Inject
    private RuneProfileConfig config;

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
            return future.isCompletedExceptionally()
                    && System.currentTimeMillis() - createdAt > FAILURE_TTL_MS;
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
        if (!config.allowExternalCollectionLogRequests()) return;

        // Tell listeners to request their pages again instead of polling.
        clientThread.invokeLater(() -> eventBus.post(new PluginMessage(NAMESPACE, CHANGED_NAME, Collections.emptyMap())));
    }

    @Subscribe
    public void onProfileDeleted(ProfileDeleted event) {
        cache.clear();
    }

    @Subscribe
    public void onPluginMessage(PluginMessage message) {
        if (!NAMESPACE.equalsIgnoreCase(message.getNamespace()) || !REQUEST_NAME.equalsIgnoreCase(message.getName())) {
            return;
        }

        Map<String, Object> data = message.getData();
        Object requestId = data.get("requestId");

        CollectionLogRequest request;
        try {
            request = gson.fromJson(gson.toJsonTree(data), CollectionLogRequest.class);
        } catch (RuntimeException e) {
            // Gson can throw more than JsonParseException for values it can't convert.
            log.warn("Failed to parse collection log request from an external plugin: {}", data, e);
            respondError(null, requestId, REASON_INVALID_REQUEST, "Malformed request.");
            return;
        }

        String sourcePlugin = request.getSourcePlugin();
        String page = request.getPage() == null ? "" : request.getPage().trim();

        if (sourcePlugin == null || sourcePlugin.trim().isEmpty()) {
            log.info("Rejecting collection log request without 'sourcePlugin': {}", data);
            respondError(page, requestId, REASON_INVALID_REQUEST, "Missing \"sourcePlugin\".");
            return;
        }

        if (!config.allowExternalCollectionLogRequests()) {
            log.debug("Rejecting collection log request from {}: sharing is disabled", sourcePlugin);
            respondError(page, requestId, REASON_DISABLED,
                    "Enable \"Share collection log with plugins\" in the RuneProfile plugin settings.");
            return;
        }

        if (page.isEmpty()) {
            log.info("Rejecting collection log request from {} without 'page'", sourcePlugin);
            respondError(page, requestId, REASON_INVALID_REQUEST, "Missing \"page\".");
            return;
        }

        log.debug("Collection log request from {} for page '{}'", sourcePlugin, page);

        clientThread.invoke(() -> {
            String username = getLocalUsername();
            if (username == null) {
                respondError(page, requestId, REASON_NOT_LOGGED_IN, "Not logged in.");
                return;
            }

            getPage(username, page).whenComplete((result, ex) -> clientThread.invoke(() -> {
                if (ex != null) {
                    respondApiError(page, requestId, ex);
                } else {
                    respondPage(page, requestId, result);
                }
            }));
        });
    }

    private String getLocalUsername() {
        // LOADING happens on every region change while logged in.
        GameState state = client.getGameState();
        if (state != GameState.LOGGED_IN && state != GameState.LOADING) return null;

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) return null;

        String name = localPlayer.getName();
        return name == null || name.isEmpty() ? null : name;
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
            items.add(Collections.unmodifiableMap(entry));
            if (item.getQuantity() > 0) obtained++;
        }

        Map<String, Object> data = baseResponse(page, requestId, true);
        data.put("name", result.getName());
        data.put("obtained", obtained);
        data.put("total", items.size());
        data.put("items", Collections.unmodifiableList(items));
        post(data);
    }

    private void respondApiError(String page, Object requestId, Throwable ex) {
        Throwable cause = ex instanceof CompletionException && ex.getCause() != null ? ex.getCause() : ex;
        String code = cause instanceof RuneProfileApiException ? ((RuneProfileApiException) cause).getCode() : null;

        String reason;
        if (API_CODE_PAGE_NOT_FOUND.equals(code)) {
            reason = REASON_PAGE_NOT_FOUND;
        } else if (RuneProfileApiException.CODE_ACCOUNT_NOT_FOUND.equals(code)) {
            reason = REASON_PROFILE_NOT_FOUND;
        } else {
            reason = REASON_ERROR;
        }
        respondError(page, requestId, reason, Utils.getApiErrorMessage(ex, "Failed to load collection log page."));
    }

    private void respondError(String page, Object requestId, String reason, String error) {
        Map<String, Object> data = baseResponse(page, requestId, false);
        data.put("reason", reason);
        data.put("error", error);
        post(data);
    }

    private void post(Map<String, Object> data) {
        PluginMessage response = new PluginMessage(NAMESPACE, RESPONSE_NAME, Collections.unmodifiableMap(data));
        clientThread.invokeLater(() -> eventBus.post(response));
    }

    private static Map<String, Object> baseResponse(String page, Object requestId, boolean success) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("page", page == null ? "" : page);
        if (requestId != null) data.put("requestId", requestId);
        return data;
    }
}
