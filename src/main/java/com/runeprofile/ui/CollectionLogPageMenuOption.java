package com.runeprofile.ui;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.data.DefaultClogPageData;
import com.runeprofile.utils.AccountHash;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class CollectionLogPageMenuOption {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RuneProfileApiClient runeProfileApiClient;

    private static final List<Integer> collectionLogPageIds = Arrays.asList(40697867, 40697871, 40697888, 40697883, 40697890);

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void onMenuEntryAdded(MenuEntryAdded event) {
        boolean isCollectionLogPageList = collectionLogPageIds.stream()
                .anyMatch(id -> id == event.getActionParam1());

        if (!isCollectionLogPageList) {
            return;
        }

        String target = event.getTarget();
        if (target.isEmpty()) {
            return;
        }

        String pageName = Utils.sanitize(target);

        client.getMenu().createMenuEntry(-2)
                .setTarget(target)
                .setOption("RuneProfile Default")
                .setType(MenuAction.RUNELITE)
                .setIdentifier(event.getIdentifier())
                .onClick(e -> setDefaultPage(pageName));

    }

    private void setDefaultPage(String pageName) {
        CompletableFuture<DefaultClogPageData> dataFuture = new CompletableFuture<>();

        clientThread.invokeLater(() -> {
            dataFuture.complete(
                    new DefaultClogPageData(
                            AccountHash.getHashed(client),
                            pageName
                    )
            );
        });

        dataFuture.thenCompose(runeProfileApiClient::setDefaultClogPage).whenComplete((result, ex) -> {
            clientThread.invokeLater(() -> {
                String message = ex != null
                        ? Utils.getApiErrorMessage(ex, "Failed to set default collection log page.")
                        : "Default collection log page set to: " + pageName;

                client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", message, "RuneProfile");
            });
        });
    }
}
