package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.Manifest;
import com.runeprofile.data.activities.ValuableDropActivity;
import com.runeprofile.utils.ItemUtils;
import com.runeprofile.utils.PlayerState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

@Slf4j
@Singleton
public class ValuableDropSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private RuneProfilePlugin plugin;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private ManifestService manifestService;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isEnabled() {
        return config.autosyncProfile() && config.trackValuableDrops() && PlayerState.isValidPlayerState(client);
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onLootReceived(LootReceived event) {
        if (!isEnabled() || event.getType() == LootRecordType.PLAYER) {
            return;
        }

        handleItemsReceived(event.getItems());
    }

    private void handleItemsReceived(Collection<ItemStack> items) {
        List<ValuableDropActivity> valuableDrops = new ArrayList<>();

        Manifest manifest = manifestService.getManifest();
        Map<Integer, Integer> valueOverrides = buildValueOverrides(manifest);
        int threshold = resolveThreshold(manifest);

        for (ItemStack itemStack : items) {
            ItemComposition item = itemManager.getItemComposition(itemStack.getId());
            int itemId = ItemUtils.getUnnotedItemId(item);
            int value = ItemUtils.getPerceivedItemValue(itemManager, itemId, valueOverrides);

            if (value >= threshold) {
                for (int i = 0; i < itemStack.getQuantity(); i++) {
                    log.debug("Valuable drop detected: {} (ID: {}, Value: {})", item.getName(), itemId, value);
                    // Add each item as a separate drop
                    valuableDrops.add(new ValuableDropActivity(new ValuableDropActivity.Data(itemId, value)));
                }
            }
        }

        if (valuableDrops.isEmpty()) {
            return;
        }

        scheduledExecutorService.execute(() -> plugin.addActivitiesAsync(valuableDrops));
    }

    /**
     * Builds the item-id → value overrides from the manifest's special valuable
     * drops, or returns {@code null} when no manifest is loaded yet (values then
     * come straight from the GE price).
     */
    private @Nullable Map<Integer, Integer> buildValueOverrides(@Nullable Manifest manifest) {
        if (manifest == null) {
            return null;
        }

        Map<Integer, Integer> overrides = new HashMap<>();
        for (Manifest.SpecialValuableDrop drop : manifest.getSpecialValuableDrops()) {
            overrides.put(drop.getItemId(), drop.getValue());
        }
        return overrides;
    }

    private int resolveThreshold(@Nullable Manifest manifest) {
        if (manifest != null && manifest.getValuableDropThreshold() > 0) {
            return manifest.getValuableDropThreshold();
        }
        return ItemUtils.VALUABLE_DROP_THRESHOLD;
    }
}
