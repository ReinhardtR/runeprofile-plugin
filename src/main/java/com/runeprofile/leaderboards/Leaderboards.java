package com.runeprofile.leaderboards;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.vars.AccountType;
import net.runelite.client.hiscore.HiscoreEndpoint;

@RequiredArgsConstructor
public enum Leaderboards {
	NORMAL(HiscoreEndpoint.NORMAL),
	IRONMAN(HiscoreEndpoint.IRONMAN),
	HARDCORE(HiscoreEndpoint.HARDCORE_IRONMAN),
	ULTIMATE(HiscoreEndpoint.ULTIMATE_IRONMAN),
	LEVEL_3_SKILLER(HiscoreEndpoint.LEVEL_3_SKILLER),
	PURE(HiscoreEndpoint.PURE);

	@Getter
	private final HiscoreEndpoint endpoint;
}
