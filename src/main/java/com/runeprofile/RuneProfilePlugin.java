package com.runeprofile;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.inject.Provides;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;

import com.runeprofile.data.*;
import com.runeprofile.modelexporter.ModelExporter;
import com.runeprofile.ui.SyncButtonManager;
import com.runeprofile.utils.AccountHash;
import com.runeprofile.utils.RuneProfileApiException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.menus.MenuManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Named;
import java.awt.image.BufferedImage;
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
    public static final String CONFIG_GROUP = "runeprofile";
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
    private MenuManager menuManager;

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

    // menu option
    private static final String RUNEPROFILE_MENU_OPTION = "Open RuneProfile";
    private static final String KICK_OPTION = "Kick";
    private static final ImmutableList<String> AFTER_OPTIONS = ImmutableList.of("Message", "Add ignore", "Remove friend", "Delete", KICK_OPTION);

    // collection log
    private final Map<Integer, Integer> playerItems = new HashMap<>();
    private int tickCollectionLogScriptFired = -1;
    private final Map<Integer, Integer> loadedItemIcons = new HashMap<>();
    private final String COLLECTION_LOG_COMMAND = "!log";
    private final Pattern COLLECTION_LOG_COMMAND_PATTERN = Pattern.compile("^(" + COLLECTION_LOG_COMMAND + ")\\s+([a-zA-Z]+(?: [a-zA-Z]+)*)$", Pattern.CASE_INSENSITIVE);

    public static Client getClient() {
        return instance.client;
    }

    @Provides
    RuneProfileConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(RuneProfileConfig.class);
    }

    @Override
    protected void startUp() {
        instance = this;

        this.runeProfilePanel = injector.getInstance(RuneProfilePanel.class);
        final BufferedImage toolbarIcon = Icon.LOGO.getImage();

        navigationButton = NavigationButton.builder()
                .tooltip("RuneProfile")
                .icon(toolbarIcon)
                .panel(runeProfilePanel)
                .priority(4)
                .build();

        clientToolbar.addNavigation(navigationButton);

        chatCommandManager.registerCommand(COLLECTION_LOG_COMMAND, this::executeLogCommand);

        GameState state = client.getGameState();
        updatePanelState(state);

        syncButtonManager.startUp();
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navigationButton);
        chatCommandManager.unregisterCommand(COLLECTION_LOG_COMMAND);

        syncButtonManager.shutDown();
    }


    @Subscribe
    private void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState state = gameStateChanged.getGameState();

        updatePanelState(state);

        // clear player state
        switch (state) {
            case HOPPING:
            case LOGGING_IN:
            case CONNECTION_LOST:
                playerItems.clear();
                break;
        }

        if (state == GameState.LOGIN_SCREEN) {
            // update on logout
            if (config.updateOnLogout() && client.getLocalPlayer() != null) {
                new Thread(this::updateProfileAsync).start();
            }
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

    private void updatePanelState(GameState state) {
        if (state == GameState.LOGGED_IN) {
            if (!isValidProfileType()) {
                runeProfilePanel.loadInvalidRequestState();
            } else {
                runeProfilePanel.loadValidState();
            }
        } else {
            runeProfilePanel.loadInvalidState();
        }
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!config.menuLookupOption()) return;

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
                    .onClick(e -> openProfileInBrowser(username));
        }
    }

    public void openProfileInBrowser(String username) {
        String url = "https://www.runeprofile.com/" + username.replace(" ", "%20");
        LinkBrowser.browse(url);
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

        String senderName = chatMessage.getType().equals(ChatMessageType.PRIVATECHATOUT)
                ? client.getLocalPlayer().getName()
                : Text.sanitize(chatMessage.getName());

        runeProfileApiClient.getCollectionLogPage(senderName, pageName).whenComplete((page, ex) -> {
            clientThread.invokeLater(() -> {
                if (ex != null) {
                    log.info("Instance of RuneProfileApiException: {}", ex.getClass());
                    final String errorMessage = getApiErrorMessage(ex, "Failed to load collection log page.");
                    updateChatMessage(chatMessage, errorMessage);
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
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Error updating profile", ex);

                        final String errorMessage = getApiErrorMessage(ex, "Failed to update your profile.");

                        clientThread.invokeLater(() -> {
                            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", errorMessage, "RuneProfile");
                        });

                        throw new RuneProfileApiException(errorMessage);
                    }

                    clientThread.invokeLater(() -> {
                        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Your profile has been updated!", "RuneProfile");
                    });
                });
    }

    public CompletableFuture<String> updateModelAsync() {
        isValidRequest();

        CompletableFuture<PlayerModelData> dataFuture = new CompletableFuture<>();

        clientThread.invokeLater(() -> {
            String accountHash = AccountHash.getHashed(client);

            Player player = client.getLocalPlayer();
            Model model = player.getModel();

            byte[] modelBytes = null;
            try {
                modelBytes = ModelExporter.toBytes(model);
            } catch (IOException e) {
                dataFuture.completeExceptionally(e);
            }

            NPC pet = client.getFollower();
            Model petModel = pet != null ? pet.getModel() : null;

            byte[] petModelBytes = null;
            if (petModel != null) {
                try {
                    petModelBytes = ModelExporter.toBytes(petModel);
                } catch (IOException e) {
                    dataFuture.completeExceptionally(e);
                }
            }

            dataFuture.complete(new PlayerModelData(accountHash, modelBytes, petModelBytes));
        });

        return dataFuture.thenCompose((data) -> runeProfileApiClient.updateModelAsync(data)
                .handle((result, ex) -> {
                    if (ex != null) {
                        log.error("Error updating model", ex);
                        throw new RuntimeException(ex.getMessage());
                    }

                    SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
                    final String dateString = sdf.format(new Date());

                    configManager.setRSProfileConfiguration(
                            CONFIG_GROUP,
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

    private String getApiErrorMessage(Throwable ex, String defaultMessage) {
        Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
        return cause instanceof RuneProfileApiException
                ? cause.getMessage()
                : defaultMessage;
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
