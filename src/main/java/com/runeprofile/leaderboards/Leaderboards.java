package com.runeprofile.leaderboards;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.vars.AccountType;
import net.runelite.client.hiscore.HiscoreEndpoint;

@RequiredArgsConstructor
public enum Leaderboards {
	NORMAL(AccountType.NORMAL, HiscoreEndpoint.NORMAL),
	IRONMAN(AccountType.IRONMAN, HiscoreEndpoint.IRONMAN),
	HARDCORE(AccountType.HARDCORE_IRONMAN, HiscoreEndpoint.HARDCORE_IRONMAN),
	ULTIMATE(AccountType.ULTIMATE_IRONMAN, HiscoreEndpoint.ULTIMATE_IRONMAN);

	@Getter
	private final AccountType accountType;

	@Getter
	private final HiscoreEndpoint endpoint;
}
