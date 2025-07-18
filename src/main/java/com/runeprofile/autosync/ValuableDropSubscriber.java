package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.activities.ValuableDropActivity;
import com.runeprofile.utils.ItemUtils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Singleton
public class ValuableDropSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private ItemManager itemManager;

    @Inject
    private RuneProfilePlugin plugin;

    @Inject
    private RuneProfileConfig config;

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isEnabled() {
        return config.autosyncProfile() && config.trackValuableDrops() && plugin.isValidPlayerState();
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

        for (ItemStack itemStack : items) {
            ItemComposition item = itemManager.getItemComposition(itemStack.getId());
            int itemId = ItemUtils.getUnnotedItemId(item);
            int value = ItemUtils.getPerceivedItemValue(itemManager, itemId);

            if (value >= ItemUtils.VALUABLE_DROP_THRESHOLD) {
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

        plugin.addActivitiesAsync(valuableDrops);
    }
}
