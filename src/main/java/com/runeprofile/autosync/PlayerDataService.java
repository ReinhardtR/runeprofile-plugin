package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.data.*;
import com.runeprofile.modelexporter.ModelExporter;
import com.runeprofile.utils.AccountHash;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.*;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.RuneScapeProfileChanged;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class PlayerDataService {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private ManifestService manifestService;

    // Clog items are not available through the client API, so we store them manually on clog open or item collection.
    // See: CollectionLogWidgetSubscriber and CollectionNotificationSubscriber
    private final Map<Integer, Integer> clogItems = new HashMap<>();

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    public void reset() {
        clearItems();
    }

    @Subscribe
    public void onRuneScapeProfileChanged(RuneScapeProfileChanged event) {
        log.debug("RuneScape Profile changed to {}, resetting player data service", event.getNewProfile());
        reset();
    }

    public void storeItem(int itemId, int quantity) {
        if (quantity <= 0) return;
        clogItems.put(itemId, quantity);
    }

    public void clearItems() {
        clogItems.clear();
    }

    public CompletableFuture<String> getAccountIdAsync() {
        CompletableFuture<String> accountIdFuture = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            String accountId = AccountHash.getHashed(client);
            accountIdFuture.complete(accountId);
        });
        return accountIdFuture;
    }

    public CompletableFuture<PlayerData> getPlayerDataAsync() {
        CompletableFuture<PlayerData> playerDataFuture = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            Manifest manifest = manifestService.getManifest();
            PlayerData playerData = new PlayerData();

            Player player = client.getLocalPlayer();

            playerData.setId(AccountHash.getHashed(client));
            playerData.setUsername(player.getName());
            playerData.setAccountType(client.getVarbitValue(VarbitID.IRONMAN));
            playerData.setClan(getPlayerClanData(player));
            playerData.setGroupName(getPlayerGroupName());

            collectSkills(playerData);
            collectQuests(playerData, manifest);
            collectCombatAchievementVarps(playerData, manifest);
            collectAchievementDiaryTiers(playerData);

            // Unlike the rest, clog items are accumulated as the player sees them
            // rather than read from the client here.
            playerData.setItems(clogItems);

            log.debug("Player Data: {}", playerData);
            playerDataFuture.complete(playerData);
        });
        return playerDataFuture;
    }

    private void collectSkills(PlayerData playerData) {
        for (Skill skill : Skill.values()) {
            playerData.getSkills().put(skill.getName(), client.getSkillExperience(skill));
        }
    }

    /**
     * Records the state of every quest the manifest lists, falling back to RuneLite's Quest enum.
     */
    private void collectQuests(PlayerData playerData, @Nullable Manifest manifest) {
        int[] questIds = manifest != null && manifest.getQuestIds().length > 0
                ? manifest.getQuestIds()
                : Arrays.stream(Quest.values()).mapToInt(Quest::getId).toArray();

        for (int questId : questIds) {
            try {
                // What Quest#getState does internally: the script leaves the status on the int stack.
                client.runScript(ScriptID.QUEST_STATUS_GET, questId);
                playerData.getQuests().put(questId, fromQuestStatus(client.getIntStack()[0]));
            } catch (Exception e) {
                log.debug("Unable to read state of quest {}: {}", questId, e.toString());
            }
        }
    }

    /** Maps a QUEST_STATUS_GET result to RuneProfile's state encoding. */
    private static int fromQuestStatus(int status) {
        // Same mapping Quest#getState applies: 1 is not started, 2 is finished, and
        // every other value means the quest is underway.
        if (status == 1) return 0;
        if (status == 2) return 2;
        return 1;
    }

    /** No-op without a manifest: the varps to read are only known from it. */
    private void collectCombatAchievementVarps(PlayerData playerData, @Nullable Manifest manifest) {
        if (manifest == null) return;

        for (int varpId : manifest.getCombatAchievementVarps()) {
            playerData.getCombatAchievementVarps().put(varpId, client.getVarpValue(varpId));
        }
    }

    private void collectAchievementDiaryTiers(PlayerData playerData) {
        for (AchievementDiary diary : AchievementDiary.values()) {
            int areaId = diary.getId();
            int[] completedCounts = diary.getTiersCompletedCount(client);
            for (int tierIndex = 0; tierIndex < completedCounts.length; tierIndex++) {
                playerData.getAchievementDiaryTiers()
                        .add(new AchievementDiaryTierData(areaId, tierIndex, completedCounts[tierIndex]));
            }
        }
    }

    public @Nullable PlayerClanData getPlayerClanData(Player player) {
        if (!config.includeClanData()) return new PlayerClanData("", -1, -1, "");

        ClanSettings clanSettings = client.getClanSettings();
        if (clanSettings == null) return new PlayerClanData("", -1, -1, ""); // not in a clan

        ClanMember member = clanSettings.findMember(player.getName());
        if (member == null) return null;

        ClanRank rank = member.getRank();
        if (rank == null) return null;

        ClanTitle title = clanSettings.titleForRank(rank);
        if (title == null) return null;

        return new PlayerClanData(clanSettings.getName(), rank.getRank(), title.getId(), title.getName());
    }

    public @Nullable String getPlayerGroupName() {
        ClanSettings clanSettings = client.getClanSettings(ClanID.GROUP_IRONMAN);
        if (clanSettings == null) return null;
        return clanSettings.getName();
    }

    public CompletableFuture<PlayerModelData> getPlayerModelDataAsync() {
        CompletableFuture<PlayerModelData> dataFuture = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            String accountHash = AccountHash.getHashed(client);

            Player player = client.getLocalPlayer();
            Model model = player != null ? player.getModel() : null;
            if (model == null) {
                dataFuture.completeExceptionally(new IllegalStateException("Player model is not available yet"));
                return;
            }

            byte[] modelBytes = null;
            try {
                modelBytes = ModelExporter.toBytes(client, model);
            } catch (IOException e) {
                dataFuture.completeExceptionally(e);
                return;
            }

            NPC pet = client.getFollower();
            Model petModel = pet != null ? pet.getModel() : null;

            byte[] petModelBytes = null;
            if (petModel != null) {
                try {
                    petModelBytes = ModelExporter.toBytes(client, petModel);
                } catch (IOException e) {
                    dataFuture.completeExceptionally(e);
                    return;
                }
            }

            dataFuture.complete(new PlayerModelData(accountHash, modelBytes, petModelBytes));
        });
        return dataFuture;
    }
}
