package com.runeprofile.leaderboards;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.vars.AccountType;
import net.runelite.client.hiscore.HiscoreEndpoint;

@RequiredArgsConstructor
public enum Leaderboards {
	NORMAL("Normal", HiscoreEndpoint.NORMAL),
	IRONMAN("Ironman", HiscoreEndpoint.IRONMAN),
	HARDCORE("Hardcore", HiscoreEndpoint.HARDCORE_IRONMAN),
	ULTIMATE("Ultimate", HiscoreEndpoint.ULTIMATE_IRONMAN),
	DEFENCE_PURE("1 Defence Pure", HiscoreEndpoint.PURE),
	SKILLER("Level 3 Skiller", HiscoreEndpoint.LEVEL_3_SKILLER);

	@Getter
	private final String name;

	@Getter
	private final HiscoreEndpoint endpoint;
}
