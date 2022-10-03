package com.runeprofile.dataobjects;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.achievementdiary.AchievementDiary;
import com.runeprofile.achievementdiary.AchievementDiaryState;
import com.runeprofile.collectionlog.CollectionLog;
import com.runeprofile.combatachievements.CombatAchievementTier;
import com.runeprofile.combatachievements.CombatAchievementTierState;
import com.runeprofile.leaderboards.Leaderboards;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.vars.AccountType;
import net.runelite.client.hiscore.HiscoreResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PlayerData {
	private static final List<Skill> skillsOrder = ImmutableList.of(
					Skill.ATTACK,
					Skill.HITPOINTS,
					Skill.MINING,
					Skill.STRENGTH,
					Skill.AGILITY,
					Skill.SMITHING,
					Skill.DEFENCE,
					Skill.HERBLORE,
					Skill.FISHING,
					Skill.RANGED,
					Skill.THIEVING,
					Skill.COOKING,
					Skill.PRAYER,
					Skill.CRAFTING,
					Skill.FIREMAKING,
					Skill.MAGIC,
					Skill.FLETCHING,
					Skill.WOODCUTTING,
					Skill.RUNECRAFT,
					Skill.SLAYER,
					Skill.FARMING,
					Skill.CONSTRUCTION,
					Skill.HUNTER
	);

	@Getter
	private final JsonObject json;

	public PlayerData(RuneProfilePlugin runeProfilePlugin) throws IllegalArgumentException, IOException, InterruptedException {
		AtomicLong accountHash = new AtomicLong();
		AtomicReference<String> username = new AtomicReference<>();
		AtomicReference<AccountType> accountType = new AtomicReference<>();
		AtomicReference<Model> model = new AtomicReference<>();
		AtomicInteger combatLevel = new AtomicInteger();
		AtomicInteger questPoints = new AtomicInteger();

		// Linked because the order is important.
		Map<String, Integer> skills = new LinkedHashMap<>();
		Map<AchievementDiary, AchievementDiaryState> achievementDiaries = new LinkedHashMap<>();

		Map<Quest, QuestState> quests = new HashMap<>();
		Map<CombatAchievementTier, CombatAchievementTierState> combatAchievements = new HashMap<>();

		CountDownLatch countDownLatch = new CountDownLatch(1);

		RuneProfilePlugin.getClientThread().invokeLater(() -> {
			Client client = RuneProfilePlugin.getClient();
			Player player = client.getLocalPlayer();

			// Misc
			accountHash.set(client.getAccountHash());
			username.set(player.getName());
			accountType.set(client.getAccountType());

			boolean hasModel = Boolean.parseBoolean(
							RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(
											RuneProfileConfig.CONFIG_GROUP,
											RuneProfileConfig.HAS_MODEL
							)
			);

			if (!hasModel) {
				model.set(player.getModel());
				RuneProfilePlugin.getConfigManager().setRSProfileConfiguration(
								RuneProfileConfig.CONFIG_GROUP,
								RuneProfileConfig.HAS_MODEL,
								"true"
				);
			}

			// Skills
			combatLevel.set(player.getCombatLevel());

			skillsOrder.forEach((skill -> {
				skills.put(skill.getName(), client.getSkillExperience(skill));
				;
			}));

			// Achievement Diaries
			for (AchievementDiary achievementDiary : AchievementDiary.values()) {
				achievementDiaries.put(achievementDiary, achievementDiary.getState(client));
			}

			// Quests
			questPoints.set(client.getVarpValue(VarPlayer.QUEST_POINTS));

			for (Quest quest : Quest.values()) {
				quests.put(quest, quest.getState(client));
			}

			// Combat Achievements
			for (CombatAchievementTier combatAchievementTier : CombatAchievementTier.values()) {
				combatAchievements.put(combatAchievementTier, combatAchievementTier.getState(client));
			}

			countDownLatch.countDown();
		});

		countDownLatch.await();

		json = new JsonObject();

		json.addProperty("accountHash", accountHash.get());
		json.addProperty("username", username.get());
		json.addProperty("accountType", accountType.get().toString());

		if (model.get() != null) {
			json.addProperty("model", "test");
		}

		json.addProperty("combatLevel", combatLevel.get());
		json.addProperty("description", getDescription());

		json.add("skills", createSkillsXPJSON(skills));
		json.add("collectionLog", createCollectionLogJSON());
		json.add("achievementDiaries", getAchievementDiariesJSON(achievementDiaries));
		json.add("combatAchievements", getCombatAchievementsJSON(combatAchievements));
		json.add("questList", getQuestsJSON(questPoints.get(), quests));
		json.add("hiscores", getHiscoresJSON(username.get()));
	}

	private String getDescription() {
		return RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.DESCRIPTION
		);
	}

	private JsonArray createSkillsXPJSON(Map<String, Integer> skills) {
		JsonArray skillsJSON = new JsonArray();

		int index = 0;
		for (Map.Entry<String, Integer> skill : skills.entrySet()) {
			JsonObject skillJSON = new JsonObject();

			skillJSON.addProperty("index", index);
			skillJSON.addProperty("name", skill.getKey());
			skillJSON.addProperty("xp", skill.getValue());

			skillsJSON.add(skillJSON);

			index++;
		}

		return skillsJSON;
	}

	private JsonObject createCollectionLogJSON() {
		CollectionLog collectionLog = RuneProfilePlugin.getCollectionLogManager().getCollectionLog();
		return new JsonParser().parse(collectionLog.toString()).getAsJsonObject();
	}

	private JsonArray getAchievementDiariesJSON(Map<AchievementDiary, AchievementDiaryState> achievementDiaries) {
		JsonArray diaries = new JsonArray();

		for (Map.Entry<AchievementDiary, AchievementDiaryState> entry : achievementDiaries.entrySet()) {
			AchievementDiary ad = entry.getKey();
			AchievementDiaryState adState = entry.getValue();

			JsonObject areaJson = new JsonObject();
			areaJson.addProperty("area", ad.getName());

			ImmutableList.of(
							adState.getEasyTier(),
							adState.getMediumTier(),
							adState.getHardTier(),
							adState.getEliteTier()
			).forEach((tier) -> {
				JsonObject tierJSON = new JsonObject();
				tierJSON.addProperty("completed", tier.getCompletedTasks());
				tierJSON.addProperty("total", tier.getTotalTasks());

				areaJson.add(tier.getTier().getName(), tierJSON);
			});

			diaries.add(areaJson);
		}

		return diaries;
	}

	private JsonObject getQuestsJSON(int questPoints, Map<Quest, QuestState> quests) {
		JsonObject json = new JsonObject();

		json.addProperty("points", questPoints);

		JsonArray questsArray = new JsonArray();

		for (Map.Entry<Quest, QuestState> entry : quests.entrySet()) {
			Quest quest = entry.getKey();
			QuestState questState = entry.getValue();

			JsonObject questJSON = new JsonObject();
			questJSON.addProperty("name", quest.getName());
			questJSON.addProperty("state", questState.name());

			questsArray.add(questJSON);
		}

		json.add("quests", questsArray);

		return json;
	}

	private JsonObject getCombatAchievementsJSON(Map<CombatAchievementTier, CombatAchievementTierState> combatAchievements) {
		JsonObject json = new JsonObject();

		for (Map.Entry<CombatAchievementTier, CombatAchievementTierState> entry : combatAchievements.entrySet()) {
			CombatAchievementTier caTier = entry.getKey();
			CombatAchievementTierState caState = entry.getValue();

			JsonObject caJSON = new JsonObject();
			caJSON.addProperty("completed", caState.getCompletedTasks());
			caJSON.addProperty("total", caState.getTotalTasks());

			String tierKey = caTier.getName();
			json.add(tierKey, caJSON);
		}

		return json;
	}

	private JsonObject getHiscoresJSON(String username) {
		JsonObject hiscores = new JsonObject();

		for (Leaderboards leaderboard : Leaderboards.values()) {
			JsonArray skills = new JsonArray();
			JsonArray activities = new JsonArray();
			JsonArray bosses = new JsonArray();

			HiscoreResult result = null;

			try {
				result = RuneProfilePlugin.getHiscoreClient().lookup(username, leaderboard.getEndpoint());
			} catch (IOException e) {
				log.error("Error looking up hiscores", e);
			}

			if (result == null) {
				JsonObject entry = new JsonObject();
				entry.add("skills", skills);
				entry.add("activities", activities);
				entry.add("bosses", bosses);
				hiscores.add(leaderboard.name().toLowerCase(), entry);

				continue;
			}

			AtomicInteger index = new AtomicInteger();
			result.getSkills().forEach((hiscore, data) -> {
				JsonObject entryItem = new JsonObject();
				entryItem.addProperty("index", index.get());
				entryItem.addProperty("name", hiscore.getName());
				entryItem.addProperty("rank", data.getRank());

				switch (hiscore.getType()) {
					case OVERALL:
					case SKILL:
						entryItem.addProperty("level", data.getLevel());
						entryItem.addProperty("xp", data.getExperience());
						skills.add(entryItem);
						break;
					case ACTIVITY:
						entryItem.addProperty("score", data.getLevel());
						activities.add(entryItem);
						break;
					case BOSS:
						entryItem.addProperty("kills", data.getLevel());
						bosses.add(entryItem);
						break;
					default:
						break;
				}

				index.getAndIncrement();
			});

			JsonObject entry = new JsonObject();
			entry.add("skills", skills);
			entry.add("activities", activities);
			entry.add("bosses", bosses);
			hiscores.add(leaderboard.name().toLowerCase(), entry);
		}

		return hiscores;
	}
}
