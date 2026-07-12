package com.runeprofile;

import com.google.inject.Provides;

import com.runeprofile.autosync.*;
import com.runeprofile.data.AddActivities;
import com.runeprofile.data.UpdateProfileResult;
import com.runeprofile.data.activities.Activity;
import com.runeprofile.data.activities.ActivityData;
import com.runeprofile.events.ProfileCreated;
import com.runeprofile.events.ProfileDeleted;
import com.runeprofile.ui.*;
import com.runeprofile.utils.PlayerState;
import com.runeprofile.utils.RuneProfileApiException;
import com.runeprofile.utils.Utils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@PluginDescriptor(
        name = "RuneProfile"
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
    private EventBus eventBus;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    @Inject
    private RuneProfileApiClient runeProfileApiClient;

    @Inject
    private PlayerDataService playerDataService;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    @Inject
    private ProfileCreationService profileCreationService;

    @Inject
    private ManifestService manifestService;

    @Inject
    private CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;

    @Inject
    private CollectionNotificationSubscriber collectionNotificationSubscriber;

    @Inject
    private RsnChangeSubscriber rsnChangeSubscriber;

    @Inject
    private ValuableDropSubscriber valuableDropSubscriber;

    @Inject
    private SkillSubscriber skillSubscriber;

    @Inject
    private QuestCompletedSubscriber questCompletedSubscriber;

    @Inject
    private AchievementDiarySubscriber achievementDiarySubscriber;

    @Inject
    private CombatAchievementSubscriber combatAchievementSubscriber;

    @Inject
    private ManualUpdateButtonManager manualUpdateButtonManager;

    @Inject
    private CollectionLogPageMenuOption collectionLogPageMenuOption;

    @Inject
    private PlayerMenuOption playerMenuOption;

    @Inject
    private ChatPlayerMenuOption chatPlayerMenuOption;

    @Inject
    private CollectionLogCommand collectionLogCommand;

    @Inject
    private CommandSuggestionOverlay commandSuggestionOverlay;

    private RuneProfilePanel runeProfilePanel;

    @Provides
    RuneProfileConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RuneProfileConfig.class);
    }

    @Override
    protected void startUp() {
        this.runeProfilePanel = injector.getInstance(RuneProfilePanel.class);
        runeProfilePanel.startUp();

        manifestService.startUp();
        playerDataService.startUp();

        autoSyncScheduler.startUp();
        profileCreationService.startUp();
        valuableDropSubscriber.startUp();
        collectionLogWidgetSubscriber.startUp();
        collectionNotificationSubscriber.startUp();
        rsnChangeSubscriber.startUp();
        skillSubscriber.startUp();
        questCompletedSubscriber.startUp();
        achievementDiarySubscriber.startUp();
        combatAchievementSubscriber.startUp();

        manualUpdateButtonManager.startUp();
        collectionLogPageMenuOption.startUp();
        playerMenuOption.startUp();
        chatPlayerMenuOption.startUp();
        collectionLogCommand.startUp();
        commandSuggestionOverlay.startUp();
    }

    @Override
    protected void shutDown() {
        runeProfilePanel.shutDown();

        manifestService.shutDown();
        playerDataService.shutDown();

        autoSyncScheduler.shutDown();
        profileCreationService.shutDown();
        valuableDropSubscriber.shutDown();
        collectionLogWidgetSubscriber.shutDown();
        collectionNotificationSubscriber.shutDown();
        rsnChangeSubscriber.shutDown();
        skillSubscriber.shutDown();
        questCompletedSubscriber.shutDown();
        achievementDiarySubscriber.shutDown();
        combatAchievementSubscriber.shutDown();

        manualUpdateButtonManager.shutDown();
        collectionLogPageMenuOption.shutDown();
        playerMenuOption.shutDown();
        chatPlayerMenuOption.shutDown();
        collectionLogCommand.shutDown();
        commandSuggestionOverlay.shutDown();
    }

    public CompletableFuture<UpdateProfileResult> updateProfileAsync(boolean isAutoSync, String eventSource) {
        if (!PlayerState.isValidPlayerState(client)) {
            if (!isAutoSync) {
                clientThread.invokeLater(() -> {
                    client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "You are not allowed to update your profile on this world.", "RuneProfile");
                });
            }

            throw new IllegalStateException("Invalid player state");
        }

        if (!isAutoSync) {
            // If this is a manual sync, reset the auto-sync timer
            autoSyncScheduler.resetAutoSyncTimer();
        }

        return playerDataService.getPlayerDataAsync().thenCompose((data) -> {
                    // sanity check: a player reported syncs going through on invalid worlds
                    if (!PlayerState.isValidPlayerState(client)) {
                        throw new IllegalStateException("Invalid player state after fetching player data");
                    }
                    return runeProfileApiClient.updateProfileAsync(data, eventSource)
                            .thenApply((result) -> {
                                if (result != null && result.isCreated()) {
                                    onProfileCreated(data.getId());
                                }
                                return result;
                            });
                })
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

                    playerDataService.reset();
                });
    }

    /**
     * Called when a profile update response indicates the profile was newly created.
     * Runs for every creation path: auto-creation at login, the panel's Create Profile
     * button, and re-creation after a profile deletion.
     */
    private void onProfileCreated(String accountId) {
        clientThread.invokeLater(() -> {
            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your RuneProfile has been created! Open your collection log in-game to sync your collection log data. You can update your player model from the RuneProfile side panel.", "RuneProfile");
        });

        eventBus.post(new ProfileCreated(accountId));

        // Upload the player model once so the profile doesn't show the default model.
        // Delayed a bit so the local player is fully rendered.
        scheduledExecutorService.schedule(() -> {
            if (!PlayerState.isValidPlayerState(client)) {
                log.debug("Skipping initial model upload: invalid player state");
                return;
            }
            updateModelAsync(true);
        }, 5, TimeUnit.SECONDS);
    }

    public void deleteProfileAsync() {
        if (!PlayerState.isValidPlayerState(client)) {
            throw new IllegalStateException("Invalid player state");
        }

        playerDataService.getAccountIdAsync().thenCompose((accountId) -> runeProfileApiClient.deleteProfileAsync(accountId).thenApply((v) -> accountId))
                .whenComplete((accountId, ex) -> {
                    if (ex != null) {
                        log.error("Error deleting profile", ex);

                        final String errorMessage = Utils.getApiErrorMessage(ex, "Failed to delete your profile.");

                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", errorMessage, "RuneProfile");
                        });

                        throw new RuneProfileApiException(errorMessage);
                    }

                    eventBus.post(new ProfileDeleted(accountId));

                    clientThread.invokeLater(() -> {
                        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your profile has been deleted!", "RuneProfile");
                    });
                });
    }

    public void updateModelAsync() {
        updateModelAsync(false);
    }

    public void updateModelAsync(boolean quiet) {
        if (!PlayerState.isValidPlayerState(client)) {
            throw new IllegalStateException("Invalid player state");
        }

        playerDataService.getPlayerModelDataAsync()
                .whenComplete((data, ex) -> {
                    if (ex == null) return;

                    log.error("Error exporting player model", ex);
                    if (!quiet) {
                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Failed to export your player model.", "RuneProfile");
                        });
                    }
                })
                .thenCompose((data) -> runeProfileApiClient.updateModelAsync(data)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error updating model", ex);

                        final String errorMessage = Utils.getApiErrorMessage(ex, "Failed to update your player model.");

                        if (quiet) {
                            log.warn("Initial model upload failed: {}", errorMessage);
                            return;
                        }

                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", errorMessage, "RuneProfile");
                        });

                        throw new RuneProfileApiException(errorMessage);
                    }

                    if (quiet) return;

                    clientThread.invokeLater(() -> {
                        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your player model has been updated!", "RuneProfile");
                    });
                }));
    }

    public void addActivitiesAsync(List<? extends Activity<? extends ActivityData>> activities) {
        if (!PlayerState.isValidPlayerState(client)) {
            throw new IllegalStateException("Invalid player state");
        }

        playerDataService.getAccountIdAsync().thenCompose((accountId) -> runeProfileApiClient.addActivities(new AddActivities(accountId, activities))).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Error adding activities", ex);

                final String errorMessage = Utils.getApiErrorMessage(ex, "Failed to add activities.");

                clientThread.invokeLater(() -> {
                    client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", errorMessage, "RuneProfile");
                });

                throw new RuneProfileApiException(errorMessage);
            }
        });
    }

}
