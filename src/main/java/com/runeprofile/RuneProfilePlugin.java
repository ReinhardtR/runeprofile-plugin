package com.runeprofile;

import com.google.gson.Gson;
import com.google.inject.Provides;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;

import com.runeprofile.data.*;
import com.runeprofile.modelexporter.PlayerModelExporter;
import com.runeprofile.ui.SyncButtonManager;
import com.runeprofile.utils.AccountHash;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.chat.ChatCommandManager;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
        name = "RuneProfile",
        description = "Share your achievements on RuneProfile.com",
        tags = {"runeprofile", "rune", "profile", "collection"}
)
public class RuneProfilePlugin extends Plugin {
    private static RuneProfilePlugin instance;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private RuneProfileApiClient runeProfileApiClient;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private SpriteManager spriteManager;

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private Gson gson;

    @Inject
    @Named("developerMode")
    @Getter
    private boolean developerMode;

    @Inject
    private ScheduledExecutorService scheduledExecutorService;

    private RuneProfilePanel runeProfilePanel;
    private NavigationButton navigationButton;

    @Inject
    private SyncButtonManager syncButtonManager;

    // collection log
    private final Map<Integer, Integer> playerItems = new HashMap<>();
    private int tickCollectionLogScriptFired = -1;
    private final Map<Integer, Integer> loadedItemIcons = new HashMap<>();
    private final String COLLECTION_LOG_COMMAND = "!log";
    private final Pattern COLLECTION_LOG_COMMAND_PATTERN = Pattern.compile("^(" + COLLECTION_LOG_COMMAND + ")\\s+([a-zA-Z]+(?: [a-zA-Z]+)*)$", Pattern.CASE_INSENSITIVE);

    public static Client getClient() {
        return instance.client;
    }

    public static ConfigManager getConfigManager() {
        return instance.configManager;
    }

    @Provides
    RuneProfileConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RuneProfileConfig.class);
    }

    @Override
    protected void startUp() {
        instance = this;

        this.runeProfilePanel = new RuneProfilePanel(this);
        final BufferedImage toolbarIcon = Icon.LOGO.getImage();

        navigationButton = NavigationButton.builder()
                .tooltip("RuneProfile")
                .icon(toolbarIcon)
                .panel(runeProfilePanel)
                .priority(3)
                .build();

        clientToolbar.addNavigation(navigationButton);

        syncButtonManager.startUp();
        chatCommandManager.registerCommand(COLLECTION_LOG_COMMAND, this::executeLogCommand);
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navigationButton);
        syncButtonManager.shutDown();
        chatCommandManager.unregisterCommand(COLLECTION_LOG_COMMAND);
    }

    @Subscribe
    private void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState state = gameStateChanged.getGameState();

        // clear player state
        switch (state) {
            case HOPPING:
            case LOGGING_IN:
            case CONNECTION_LOST:
                playerItems.clear();
                break;
        }

        if (state == GameState.LOGIN_SCREEN) {
            // cant use panel when logged out
            runeProfilePanel.loadInvalidState();

            // update on logout
            if (config.updateOnLogout() && client.getLocalPlayer() != null) {
                new Thread(this::updateProfileAsync).start();
            }
        }

        if (state == GameState.LOGGED_IN) {
            // cant use panel on invalid profiles
            if (!isValidProfileType()) {
                runeProfilePanel.loadInvalidRequestState();
                return;
            }

            runeProfilePanel.loadValidState();
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        int tick = client.getTickCount();
        boolean hasClogScriptFired = tickCollectionLogScriptFired != -1;
        boolean hasBufferPassed = tickCollectionLogScriptFired + 2 < tick;
        if (hasClogScriptFired && hasBufferPassed) {
            tickCollectionLogScriptFired = -1;
            scheduledExecutorService.execute(this::updateProfileAsync);
        }
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired preFired) {
        if (preFired.getScriptId() == 4100) {
            tickCollectionLogScriptFired = client.getTickCount();

            Object[] args = preFired.getScriptEvent().getArguments();
            int itemId = (int) args[1];
            int quantity = (int) args[2];

            playerItems.put(itemId, quantity);
        }
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired postFired) {
        syncButtonManager.onScriptPostFired(postFired);
    }

    private void executeLogCommand(ChatMessage chatMessage, String message) {
        log.debug("Executing log command: {}", message);
        Matcher commandMatcher = COLLECTION_LOG_COMMAND_PATTERN.matcher(message);
        if (!commandMatcher.matches()) {
            log.debug("Invalid command format");
            return;
        }

        String pageName = commandMatcher.group(2);
        if (pageName == null || pageName.isEmpty()) {
            log.debug("Invalid page name");
            return;
        }

        String username = client.getLocalPlayer().getName();

        runeProfileApiClient.getCollectionLogPage(username, pageName).thenAccept((page) -> {
            clientThread.invokeLater(() -> {
                if (page == null) {
                    updateChatMessage(chatMessage, "Failed to load collection log page");
                    return;
                }

                loadPageIcons(page);

                List<CollectionLogItem> items = page.getItems();
                StringBuilder itemBuilder = new StringBuilder();
                for (CollectionLogItem item : items) {
                    if (item.getQuantity() < 1) continue;

                    String itemString = "<img=" + loadedItemIcons.get(item.getId()) + ">";
                    if (item.getQuantity() > 1) {
                        itemString += "x" + item.getQuantity();
                    }
                    itemString += "  ";
                    itemBuilder.append(itemString);
                }

                int obtainedItemsCount = (int) items.stream().filter(item -> item.getQuantity() > 0).count();
                int totalItemsCount = items.size();

                final String replacementMessage = page.getName() + " " + "(" + obtainedItemsCount + "/" + totalItemsCount + ")" + " : " + itemBuilder.toString();
                updateChatMessage(chatMessage, replacementMessage);
            });
        });
    }


    public void updateProfileAsync() {
        log.debug("Executing update profile method");

        isValidRequest();

        getPlayerDataAsync().thenCompose((data) -> runeProfileApiClient.updateProfileAsync(data))
                .handle((dateString, ex) -> {
                    if (ex != null) {
                        log.error("Error updating profile", ex);
                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Failed to update your profile.", "RuneProfile");
                        });

                        throw new RuntimeException(ex);
                    }

                    clientThread.invokeLater(() -> {
                        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your profile has been updated!", "RuneProfile");
                    });

                    return dateString;
                });
    }

    public CompletableFuture<String> updateModelAsync() throws IllegalStateException {
        isValidRequest();

        CompletableFuture<PlayerModelData> dataFuture = new CompletableFuture<>();

        clientThread.invokeLater(() -> {
            String accountHash = AccountHash.getHashed(client);

            Player player = client.getLocalPlayer();
            Model model = player.getModel();

            byte[] modelBytes = null;
            try {
                modelBytes = PlayerModelExporter.export(model);
            } catch (IOException e) {
                dataFuture.completeExceptionally(e);
            }

            dataFuture.complete(new PlayerModelData(accountHash, modelBytes));
        });

        return dataFuture.thenCompose((data) -> runeProfileApiClient.updateModelAsync(data)
                .handle((dateString, ex) -> {
                    if (ex != null) {
                        log.error("Error updating model", ex);
                        throw new RuntimeException(ex);
                    }

                    configManager.setRSProfileConfiguration(
                            RuneProfileConfig.CONFIG_GROUP,
                            RuneProfileConfig.MODEL_UPDATE_DATE,
                            dateString
                    );

                    return dateString;
                }));
    }

    private CompletableFuture<PlayerData> getPlayerDataAsync() {
        CompletableFuture<PlayerData> playerDataFuture = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            PlayerData playerData = new PlayerData();

            // general
            playerData.setId(AccountHash.getHashed(client));
            playerData.setUsername(client.getLocalPlayer().getName());
            playerData.setAccountType(client.getVarbitValue(VarbitID.IRONMAN));

            // skills
            for (Skill skill : Skill.values()) {
                String name = skill.getName();
                int xp = client.getSkillExperience(skill);
                playerData.getSkills().put(name, xp);
            }

            // quests
            for (Quest quest : Quest.values()) {
                int id = quest.getId();
                QuestState stateEnum = quest.getState(client);
                int state = 0;
                if (stateEnum == QuestState.IN_PROGRESS) {
                    state = 1;
                } else if (stateEnum == QuestState.FINISHED) {
                    state = 2;
                }
                playerData.getQuests().put(id, state);
            }

            // combat achievement tiers
            for (CombatAchievementTier tier : CombatAchievementTier.values()) {
                int id = tier.getId();
                int completedCount = tier.getCompletedCount(client);
                playerData.getCombatAchievementTiers().put(id, completedCount);
            }

            // achievement diary tiers
            for (AchievementDiary diary : AchievementDiary.values()) {
                int areaId = diary.getId();
                int[] completedCounts = diary.getTiersCompletedCount(client);
                for (int tierIndex = 0; tierIndex < completedCounts.length; tierIndex++) {
                    int completedCount = completedCounts[tierIndex];
                    playerData.getAchievementDiaryTiers().add(new AchievementDiaryTierData(areaId, tierIndex, completedCount));
                }
            }

            // items
            playerData.setItems(playerItems);

            playerDataFuture.complete(playerData);
        });
        return playerDataFuture;
    }

    private void isValidRequest() throws IllegalStateException {
        log.debug("Validating request");
        if (!isValidProfileType()) {
            clientThread.invokeLater(() -> {
                log.debug("Invalid profile type");
                client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Invalid profile type. Please hop to a standard world.", "RuneProfile");
            });
            throw new IllegalStateException("Invalid profile type");
        }

        long accountHash = client.getAccountHash();

        if (accountHash == -1) {
            log.debug("Failed to get account hash");
            throw new IllegalStateException("Failed to get AccountHash");
        }

        Player player = client.getLocalPlayer();

        if (player == null || player.getName() == null) {
            log.debug("Failed to get player");
            throw new IllegalStateException("Failed to get Player");
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isValidProfileType() {
        RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
        return profileType.equals(RuneScapeProfileType.STANDARD);
    }

    private void updateChatMessage(ChatMessage chatMessage, String message) {
        log.debug("Updating chat message: {}", message);
        chatMessage.getMessageNode().setValue(message);
        client.runScript(ScriptID.BUILD_CHATBOX);
    }

    private void loadPageIcons(CollectionLogPage page) {
        List<CollectionLogItem> itemsToLoad = page.getItems()
                .stream()
                .filter(item -> !loadedItemIcons.containsKey(item.getId()))
                .collect(Collectors.toList());

        final IndexedSprite[] modIcons = client.getModIcons();

        final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + itemsToLoad.size());
        int modIconIdx = modIcons.length;

        for (int i = 0; i < itemsToLoad.size(); i++) {
            final CollectionLogItem item = itemsToLoad.get(i);
            final ItemComposition itemComposition = itemManager.getItemComposition(item.getId());
            final BufferedImage image = ImageUtil.resizeImage(itemManager.getImage(itemComposition.getId()), 18, 16);
            final IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
            final int spriteIndex = modIconIdx + i;

            newModIcons[spriteIndex] = sprite;
            loadedItemIcons.put(item.getId(), spriteIndex);
        }

        client.setModIcons(newModIcons);
    }

    public void DEV_generateHiscoreIconsJson() {
        clientThread.invokeLater(() -> {
            Map<String, String> icons = new HashMap<>();
            for (HiscoreSkill skill : HiscoreSkill.values()) {
                String key = skill.getName();
                int spriteId = skill.getSpriteId();

                if (spriteId == -1) {
                    continue;
                }

                final BufferedImage sprite = spriteManager.getSprite(spriteId, 0);

                if (sprite == null) {
                    log.debug("Failed to load icon for = {}", key);
                    continue;
                }

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    ImageIO.write(sprite, "png", baos);
                    byte[] imageBytes = baos.toByteArray();
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    icons.put(key, base64Image);
                } catch (IOException e) {
                    log.debug("Failed to encode icon for = {}", key);
                }
            }

            String outputFileName = "hiscore-icons.json";
            File outputFile = new File(outputFileName);
            try (FileWriter writer = new FileWriter(outputFile)) {
                gson.toJson(icons, writer); // Serialize the map to JSON and write to the file
                log.debug("Successfully generated hiscore icons in file = {}", outputFileName);
            } catch (IOException e) {
                log.debug("Failed to write hiscore icons file");
            }
        });
    }
}
