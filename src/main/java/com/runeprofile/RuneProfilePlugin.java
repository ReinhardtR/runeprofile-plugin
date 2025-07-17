package com.runeprofile;

import com.google.inject.Provides;

import com.runeprofile.autosync.AutoSyncScheduler;
import com.runeprofile.autosync.CollectionNotificationSubscriber;
import com.runeprofile.autosync.CollectionLogWidgetSubscriber;
import com.runeprofile.autosync.PlayerDataService;
import com.runeprofile.ui.ChatPlayerMenuOption;
import com.runeprofile.ui.CollectionLogCommand;
import com.runeprofile.ui.CollectionLogPageMenuOption;
import com.runeprofile.ui.ManualUpdateButtonManager;
import com.runeprofile.utils.RuneProfileApiException;
import com.runeprofile.utils.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.inject.Named;

@Slf4j
@PluginDescriptor(
        name = "RuneProfile",
        description = "Share your achievements on RuneProfile.com",
        tags = {"runeprofile", "rune", "profile", "collection"}
)
public class RuneProfilePlugin extends Plugin {
    public static final String CONFIG_GROUP = "runeprofile";

    @Inject
    @Named("developerMode")
    @Getter
    private boolean developerMode;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RuneProfileApiClient runeProfileApiClient;

    @Inject
    private PlayerDataService playerDataService;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    @Inject
    private CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;

    @Inject
    private CollectionNotificationSubscriber collectionNotificationSubscriber;

    @Inject
    private ManualUpdateButtonManager manualUpdateButtonManager;

    @Inject
    private CollectionLogPageMenuOption collectionLogPageMenuOption;

    @Inject
    private ChatPlayerMenuOption chatPlayerMenuOption;

    @Inject
    private CollectionLogCommand collectionLogCommand;

    private RuneProfilePanel runeProfilePanel;

    @Provides
    RuneProfileConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RuneProfileConfig.class);
    }

    @Override
    protected void startUp() {
        this.runeProfilePanel = injector.getInstance(RuneProfilePanel.class);
        runeProfilePanel.startUp();

        manualUpdateButtonManager.startUp();
        collectionLogPageMenuOption.startUp();
        chatPlayerMenuOption.startUp();
        collectionLogCommand.startUp();

        collectionLogWidgetSubscriber.startUp();
        collectionNotificationSubscriber.startUp();

        autoSyncScheduler.startUp();
    }

    @Override
    protected void shutDown() {
        runeProfilePanel.shutDown();

        manualUpdateButtonManager.shutDown();
        collectionLogPageMenuOption.shutDown();
        chatPlayerMenuOption.shutDown();
        collectionLogCommand.shutDown();

        collectionLogWidgetSubscriber.shutDown();
        collectionNotificationSubscriber.shutDown();

        autoSyncScheduler.shutDown();
    }

    public void updateProfileAsync(boolean isAutoSync) {
        if (!isValidPlayerState()) {
            throw new IllegalStateException("Invalid player state");
        }

        if (!isAutoSync) {
            autoSyncScheduler.resetAutoSyncTimer();
        }

        playerDataService.getPlayerDataAsync().thenCompose((data) -> runeProfileApiClient.updateProfileAsync(data))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error updating profile", ex);

                        final String errorMessage = Utils.getApiErrorMessage(ex, "Failed to update your profile.");

                        if (!isAutoSync) {
                            clientThread.invokeLater(() -> {
                                client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", errorMessage, "RuneProfile");
                            });
                        }

                        throw new RuneProfileApiException(errorMessage);
                    }

                    if (!isAutoSync) {
                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your profile has been updated!", "RuneProfile");
                        });
                    }
                });
    }

    public void updateModelAsync() {
        if (!isValidPlayerState()) {
            throw new IllegalStateException("Invalid player state");
        }

        playerDataService.getPlayerModelDataAsync().thenCompose((data) -> runeProfileApiClient.updateModelAsync(data)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error updating model", ex);

                        final String errorMessage = Utils.getApiErrorMessage(ex, "Failed to update your player model.");

                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", errorMessage, "RuneProfile");
                        });

                        throw new RuneProfileApiException(errorMessage);
                    }

                    clientThread.invokeLater(() -> {
                        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your player model has been updated!", "RuneProfile");
                    });
                }));
    }

    public boolean isValidPlayerState() {
        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            log.debug("Invalid account hash: {}", accountHash);
            return false;
        }

        Player player = client.getLocalPlayer();
        if (player == null || player.getName() == null) {
            log.debug("Invalid player");
            return false;
        }

        RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
        boolean isValidProfileType = profileType.equals(RuneScapeProfileType.STANDARD);
        if (!isValidProfileType) {
            log.debug("Invalid profile type: {}", profileType);
            return false;
        }

        return true;
    }
}
