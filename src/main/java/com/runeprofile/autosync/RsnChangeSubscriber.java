package com.runeprofile.autosync;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Nameable;
import net.runelite.api.events.NameableNameChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import com.google.common.base.Strings;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class RsnChangeSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    // Code from: WiseOldMan
    // Repository: https://github.com/wise-old-man/wiseoldman-runelite-plugin
    // License: BSD 2-Clause License
    private boolean isValidNameChange(String prev, String curr) {
        return !(Strings.isNullOrEmpty(prev)
                || curr.equals(prev)
                || prev.startsWith("[#")
                || curr.startsWith("[#"));
    }

    @Subscribe
    public void onNameableNameChanged(NameableNameChanged nameableNameChanged) {
        final Nameable nameable = nameableNameChanged.getNameable();

        String name = nameable.getName();
        String prev = nameable.getPrevName();

        if (!isValidNameChange(prev, name)) {
            return;
        }

        autoSyncScheduler.startRapidSync();
    }
}
