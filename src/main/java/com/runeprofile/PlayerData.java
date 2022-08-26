package com.runeprofile;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runeprofile.achievementdiary.AchievementDiary;
import com.runeprofile.achievementdiary.AchievementDiaryState;
import com.runeprofile.collectionlog.CollectionLog;
import com.runeprofile.combatachievements.CombatAchievementTier;
import com.runeprofile.combatachievements.CombatAchievementTierState;
import com.runeprofile.leaderboards.Leaderboards;
import com.runeprofile.playermodel.PLYExporter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.vars.AccountType;
import net.runelite.client.hiscore.HiscoreResult;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

@Slf4j
public class PlayerData {
	@Getter
	private final JsonObject json;
	private final Client client;

	private final Player player;
	private final String username;
	private final AccountType accountType;

	public PlayerData(Client client) throws IllegalArgumentException, IOException {
		this.client = client;

		json = new JsonObject();

		player = client.getLocalPlayer();
		username = player.getName();
		accountType = client.getAccountType();

		json.addProperty("accountHash", client.getAccountHash());
		json.addProperty("username", username);
		json.addProperty("accountType", accountType.toString());
		json.addProperty("model", createPlayerModelJSON());

		json.add("skills", createSkillsXPJSON());
		json.add("collectionLog", createCollectionLogJSON());
		json.add("achievementDiaries", getAchievementDiariesJSON());
		json.add("combatAchievements", getCombatAchievementsJSON());
		json.add("questList", getQuestsJSON());
		json.add("hiscores", getHiscoresJSON());
	}

	private String createPlayerModelJSON() {
		// player.setAnimation(2566);
		// player.setAnimationFrame(0);

		byte[] bytes = new byte[0];

		try {
			bytes = PLYExporter.export(player.getModel(), username);
		} catch (IOException e) {
			log.error("Error exporting player model", e);
		}

		return Base64.getEncoder().encodeToString(bytes);
	}

	private JsonArray createSkillsXPJSON() {
		JsonArray skillsJSON = new JsonArray();

		List<Skill> skillsOrder = ImmutableList.of(
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

		for (Skill skill : skillsOrder) {
			JsonObject skillJSON = new JsonObject();
			skillJSON.addProperty("name", skill.getName());
			skillJSON.addProperty("xp", client.getSkillExperience(skill));

			skillsJSON.add(skillJSON);
		}

		return skillsJSON;
	}

	private JsonObject createCollectionLogJSON() {
		CollectionLog collectionLog = RuneProfilePlugin.getCollectionLogManager().getCollectionLog();
		return new JsonParser().parse(collectionLog.toString()).getAsJsonObject();
	}

	private JsonArray getAchievementDiariesJSON() {
		JsonArray diaries = new JsonArray();

		for (AchievementDiary ad : AchievementDiary.values()) {
			JsonObject areaJson = new JsonObject();
			areaJson.addProperty("area", ad.getName());

			AchievementDiaryState adState = ad.getState(client);

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

	private JsonObject getQuestsJSON() {
		JsonObject json = new JsonObject();

		json.addProperty("points", client.getVar(VarPlayer.QUEST_POINTS));

		JsonArray questsArray = new JsonArray();

		for (Quest quest : Quest.values()) {
			QuestState questState = quest.getState(client);

			JsonObject questJSON = new JsonObject();
			questJSON.addProperty("name", quest.getName());
			questJSON.addProperty("state", questState.name());

			questsArray.add(questJSON);
		}

		json.add("quests", questsArray);

		return json;
	}

	private JsonObject getCombatAchievementsJSON() {
		JsonObject json = new JsonObject();

		for (CombatAchievementTier ca : CombatAchievementTier.values()) {
			CombatAchievementTierState caState = ca.getState(client);

			JsonObject caJSON = new JsonObject();
			caJSON.addProperty("completed", caState.getCompletedTasks());
			caJSON.addProperty("total", caState.getTotalTasks());

			String tierKey = ca.getName();
			json.add(tierKey, caJSON);
		}

		return json;
	}

	private JsonObject getHiscoresJSON() {
		JsonObject hiscores = new JsonObject();

		for (Leaderboards leaderboard : Leaderboards.values()) {
			JsonArray skills = new JsonArray();
			JsonArray activities = new JsonArray();
			JsonArray bosses = new JsonArray();

			HiscoreResult result = null;

			try {
				result = RuneProfilePlugin.getHiscoreClient().lookup(username, leaderboard.getEndpoint());
			} catch (IOException e) {
				log.error("Error looking up hiscore", e);
			}

			if (result == null) {
				JsonObject entry = new JsonObject();
				entry.add("skills", skills);
				entry.add("activities", activities);
				entry.add("bosses", bosses);
				hiscores.add(leaderboard.name().toLowerCase(), entry);

				continue;
			}

			result.getSkills().forEach((hiscore, data) -> {
				JsonObject entryItem = new JsonObject();
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
