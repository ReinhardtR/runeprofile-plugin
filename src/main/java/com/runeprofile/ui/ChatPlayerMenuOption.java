package com.runeprofile.ui;

import com.google.common.collect.ImmutableList;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

import javax.inject.Inject;

@Slf4j
public class ChatPlayerMenuOption {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    // menu option
    private static final String KICK_OPTION = "Kick";
    private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Add ignore", "Remove friend", "Delete", KICK_OPTION);

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!config.showMenuLookupOption()) return;

        int groupId = WidgetUtil.componentToInterface(event.getActionParam1());
        String option = event.getOption();

        if (!AFTER_OPTIONS.contains(option)
                // prevent duplicate menu options in friends list
                || (option.equals("Delete") && groupId != InterfaceID.IGNORE)) {
            return;
        }

        boolean addMenuLookup = (groupId == InterfaceID.FRIENDS
                || groupId == InterfaceID.CHATCHANNEL_CURRENT
                || groupId == InterfaceID.CLANS_SIDEPANEL
                || groupId == InterfaceID.CLANS_GUEST_SIDEPANEL
                // prevent from adding for Kick option (interferes with the raiding party one)
                || groupId == InterfaceID.CHATBOX && !KICK_OPTION.equals(option)
                || groupId == InterfaceID.RAIDS_SIDEPANEL
                || groupId == InterfaceID.PM_CHAT
                || groupId == InterfaceID.IGNORE);

        if (addMenuLookup) {
            String username = Text.toJagexName(Text.removeTags(event.getTarget()));

            client.getMenu().createMenuEntry(-2)
                    .setTarget(event.getTarget())
                    .setOption("RuneProfile")
                    .setType(MenuAction.RUNELITE)
                    .setIdentifier(event.getIdentifier())
                    .onClick(e -> Utils.openProfileInBrowser(username));
        }
    }
}
