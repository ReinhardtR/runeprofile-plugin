package com.runeprofile.leaderboards;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.vars.AccountType;
import net.runelite.client.hiscore.HiscoreEndpoint;

@RequiredArgsConstructor
public enum Leaderboards {
	NORMAL("NORMAL", HiscoreEndpoint.NORMAL),
	IRONMAN("IRONMAN", HiscoreEndpoint.IRONMAN),
	HARDCORE("HARDCORE_IRONMAN", HiscoreEndpoint.HARDCORE_IRONMAN),
	ULTIMATE("ULTIMATE_IRONMAN", HiscoreEndpoint.ULTIMATE_IRONMAN),
	DEFENCE_PURE("1_DEFENCE_PURE", HiscoreEndpoint.PURE),
	SKILLER("LEVEL_3_SKILLER", HiscoreEndpoint.LEVEL_3_SKILLER);

	@Getter
	private final String gameMode;

	@Getter
	private final HiscoreEndpoint endpoint;
}
