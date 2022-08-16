package com.runeprofile;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runeprofile.achievementdiary.AchievementDiary;
import com.runeprofile.achievementdiary.AchievementDiaryState;
import com.runeprofile.collectionlog.CollectionLog;
import com.runeprofile.combatachievements.CombatAchievementTier;
import com.runeprofile.combatachievements.CombatAchievementTierState;
import com.runeprofile.playermodel.PLYExporter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import java.io.IOException;
import java.util.Base64;

@Slf4j
public class PlayerData {
	@Getter
	private final JsonObject json;
	private final Client client;

	public PlayerData(Client client) throws IllegalArgumentException, IOException {
		this.client = client;

		Player player = client.getLocalPlayer();

		json = new JsonObject();

		json.addProperty("accountHash", client.getAccountHash());
		json.addProperty("username", player.getName());
		json.addProperty("accountType", client.getAccountType().toString());
		json.addProperty("model", createPlayerModelJSON(player));

		json.add("skills", createSkillsXPJSON(client.getSkillExperiences(), client.getOverallExperience()));
		json.add("collectionLog", createCollectionLogJSON());
		json.add("achievementDiaries", getAchievementDiariesJSON());
		json.add("combatAchievements", getCombatAchievementsJSON());
		json.add("questList", getQuestsJSON());
	}

	private String createPlayerModelJSON(Player player) {
		// player.setAnimation(2566);
		// player.setAnimationFrame(0);

		byte[] bytes = new byte[0];

		try {
			bytes = PLYExporter.export(player.getModel(), player.getName());
		} catch (IOException e) {
			log.error("Error exporting player model", e);
		}

		return Base64.getEncoder().encodeToString(bytes);
	}

	private JsonArray createSkillsXPJSON(int[] xps, long overallXP) {
		JsonArray skillsJSON = new JsonArray();
		Skill[] skills = Skill.values();

		// Loops through the skills (-1 skips overall xp)
		for (int i = 0; i < skills.length - 1; i++) {
			JsonObject skillJSON = new JsonObject();
			skillJSON.addProperty("name", skills[i].getName());
			skillJSON.addProperty("xp", xps[i]);

			skillsJSON.add(skillJSON);
		}

		// Adds the overall experience, as it's not included in the loop above
		JsonObject overallXPJson = new JsonObject();
		overallXPJson.addProperty("name", "Overall");
		overallXPJson.addProperty("xp", overallXP);
		skillsJSON.add(overallXPJson);

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
			questJSON.addProperty("name", quest.name());
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
}
