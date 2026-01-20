package com.runeprofile.ui;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.menus.MenuManager;

import javax.inject.Inject;

@Slf4j
public class PlayerMenuOption {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private MenuManager menuManager;

    @Inject
    private RuneProfileConfig config;

    private static final String LOOKUP_OPTION = "RuneProfile";

    public void startUp() {
        eventBus.register(this);
        if (isEnabled()) {
            menuManager.addPlayerMenuItem(LOOKUP_OPTION);
        }
    }

    public void shutDown() {
        eventBus.unregister(this);
        menuManager.removePlayerMenuItem(LOOKUP_OPTION);
    }

    public boolean isEnabled() {
        return config.showPlayerLookupOption();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) return;

        if (isEnabled()) {
            menuManager.addPlayerMenuItem(LOOKUP_OPTION);
        } else {
            menuManager.removePlayerMenuItem(LOOKUP_OPTION);
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() == MenuAction.RUNELITE_PLAYER && event.getMenuOption().equals(LOOKUP_OPTION)) {
            IndexedObjectSet<? extends Player> players = client.getTopLevelWorldView().players();
            Player player = players.byIndex(event.getId());

            if (player == null) {
                return;
            }
            String target = player.getName();

            if (target == null || target.isEmpty()) {
                return;
            }
            Utils.openProfileInBrowser(target);
        }
    }
}
