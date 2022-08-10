package com.runeprofile;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RuneProfilePluginTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(RuneProfilePlugin.class);
		RuneLite.main(args);
	}
}