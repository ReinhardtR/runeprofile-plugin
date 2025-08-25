package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.data.*;
import com.runeprofile.modelexporter.ModelExporter;
import com.runeprofile.utils.AccountHash;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanRank;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Singleton
public class PlayerDataService {
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private RuneProfileConfig config;

    // Clog items are not available through the client API, so we store them manually on clog open or item collection.
    // See: CollectionLogWidgetSubscriber and CollectionNotificationSubscriber
    private final Map<Integer, Integer> clogItems = new HashMap<>();

    public void reset() {
        clogItems.clear();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        GameState gameState = gameStateChanged.getGameState();
        if (gameState != GameState.LOGGED_IN) {
            reset();
        }
    }

    public void storeItem(int itemId, int quantity) {
        if (quantity <= 0) return;
        clogItems.put(itemId, quantity);
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
            PlayerData playerData = new PlayerData();

            Player player = client.getLocalPlayer();
            String username = player.getName();

            // general
            playerData.setId(AccountHash.getHashed(client));
            playerData.setUsername(username);
            playerData.setAccountType(client.getVarbitValue(VarbitID.IRONMAN));

            // clan
            playerData.setClan(getPlayerClanData(player));

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
            playerData.setItems(clogItems);

            playerDataFuture.complete(playerData);
        });
        return playerDataFuture;
    }

    public PlayerClanData getPlayerClanData(Player player) {
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

    public CompletableFuture<PlayerModelData> getPlayerModelDataAsync() {
        CompletableFuture<PlayerModelData> dataFuture = new CompletableFuture<>();
        clientThread.invokeLater(() -> {
            String accountHash = AccountHash.getHashed(client);

            Player player = client.getLocalPlayer();
            Model model = player.getModel();

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
