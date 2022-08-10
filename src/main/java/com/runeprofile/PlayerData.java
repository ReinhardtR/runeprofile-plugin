package com.runeprofile;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runeprofile.achievementdiary.AchievementDiary;
import com.runeprofile.achievementdiary.AchievementDiaryState;
import com.runeprofile.collectionlog.CollectionLog;
import com.runeprofile.combatachievements.CombatAchievementTier;
import com.runeprofile.combatachievements.CombatAchievementTierState;
import com.runeprofile.playermodel.PlayerModel;
import com.runeprofile.playermodel.PlayerModelFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;

import java.io.IOException;

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

		json.add("model", createPlayerModelJSON(player));
		json.add("skills", createSkillsXPJSON(client.getSkillExperiences(), client.getOverallExperience()));
		json.add("collectionLog", createCollectionLogJSON());

		// Not supported by API endpoint yet
//		json.add("achievementDiaries", getAchievementDiariesJSON());
//		json.add("combatAchievements", getCombatAchievementsJSON());
//		json.add("quests", getQuestsJSON());
	}

	private JsonObject createPlayerModelJSON(Player player) {
		// player.setAnimation(2566);
		// player.setAnimationFrame(0);
		PlayerModel playerModel = PlayerModelFactory.getPlayerModel(player.getModel(), player.getName());

		JsonObject playerModelJSON = new JsonObject();
		playerModelJSON.addProperty("obj", playerModel.getObj());
		playerModelJSON.addProperty("mtl", playerModel.getMtl());

		return playerModelJSON;
	}

	private JsonObject createSkillsXPJSON(int[] xps, long overallXP) {
		JsonObject skillXPsJSON = new JsonObject();
		Skill[] skills = Skill.values();

		// Loops through the skills (-1 skips overall xp)
		for (int i = 0; i < skills.length - 1; i++) {
			skillXPsJSON.addProperty(skills[i].toString().toLowerCase(), xps[i]);
		}

		// Adds the overall experience, as it's not included in the loop above
		skillXPsJSON.addProperty(Skill.OVERALL.toString().toLowerCase(), overallXP);

		return skillXPsJSON;
	}

	private JsonObject createCollectionLogJSON() {
		CollectionLog collectionLog = RuneProfilePlugin.getCollectionLogManager().getCollectionLog();
		return new JsonParser().parse(collectionLog.toString()).getAsJsonObject();
	}

	private JsonObject getAchievementDiariesJSON() {
		JsonObject json = new JsonObject();

		for (AchievementDiary ad : AchievementDiary.values()) {
			JsonObject diaryJson = new JsonObject();

			AchievementDiaryState adState = ad.getState(client);

			ImmutableList.of(
							adState.getEasyTier(),
							adState.getMediumTier(),
							adState.getHardTier(),
							adState.getEliteTier()
			).forEach((tier) -> {
				JsonObject tierJSON = new JsonObject();
				tierJSON.addProperty("completedTasks", tier.getCompletedTasks());
				tierJSON.addProperty("totalTasks", tier.getTotalTasks());
				tierJSON.addProperty("isCompleted", tier.isCompleted());

				String tierKey = tier.getTier().toString().toLowerCase();
				diaryJson.add(tierKey, tierJSON);
			});

			String diaryKey = ad.getName().toLowerCase();
			json.add(diaryKey, diaryJson);
		}

		return json;
	}

	private JsonObject getQuestsJSON() {
		JsonObject json = new JsonObject();

		json.addProperty("questPoints", client.getVar(VarPlayer.QUEST_POINTS));

		JsonObject questList = new JsonObject();
		for (Quest quest : Quest.values()) {
			QuestState questState = quest.getState(client);

			questList.addProperty(quest.getName(), questState.name());
		}

		json.add("questList", questList);

		return json;
	}

	private JsonObject getCombatAchievementsJSON() {
		JsonObject json = new JsonObject();

		for (CombatAchievementTier ca : CombatAchievementTier.values()) {
			CombatAchievementTierState caState = ca.getState(client);

			JsonObject caJSON = new JsonObject();
			caJSON.addProperty("completedTasks", caState.getCompletedTasks());
			caJSON.addProperty("totalTasks", caState.getTotalTasks());

			String tierKey = ca.getName().toLowerCase();
			json.add(tierKey, caJSON);
		}

		return json;
	}
}
