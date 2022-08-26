package com.runeprofile;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.inject.Provides;
import com.runeprofile.collectionlog.CollectionLog;
import com.runeprofile.collectionlog.CollectionLogManager;
import com.runeprofile.playermodel.PLYExporter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;

@Slf4j
@PluginDescriptor(name = "RuneProfile")
public class RuneProfilePlugin extends Plugin {
	private static RuneProfilePlugin instance;

	@Inject
	private Client client;

	@Inject
	private RuneProfileConfig config;

	@Inject
	private RuneProfileApiClient runeProfileApiClient;

	@Inject
	private ExternalPluginManager externalPluginManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Getter
	@Inject
	private ClientThread clientThread;

	@Inject
	private HiscoreClient hiscoreClient;

	@Getter
	@Inject
	private ConfigManager configManager;

	private NavigationButton navigationButton;
	private CollectionLogManager collectionLogManager;

	public static CollectionLogManager getCollectionLogManager() {
		return instance.collectionLogManager;
	}

	public static Client getClient() {
		return instance.client;
	}

	public static HiscoreClient getHiscoreClient() {
		return instance.hiscoreClient;
	}

	@Provides
	RuneProfileConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RuneProfileConfig.class);
	}

	@Override
	protected void startUp() {
		instance = this;

		RuneProfilePanel runeProfilePanel = new RuneProfilePanel(this);
		final BufferedImage toolbarIcon = Icon.LOGO.getImage();

		navigationButton = NavigationButton.builder()
						.tooltip("RuneProfile")
						.icon(toolbarIcon)
						.panel(runeProfilePanel)
						.priority(1)
						.build();

		clientToolbar.addNavigation(navigationButton);
	}

	@Override
	protected void shutDown() {
		clientToolbar.removeNavigation(navigationButton);
	}

	@Subscribe
	private void onGameStateChanged(GameStateChanged gameStateChanged) {
		if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
//			try {
//				updateRuneProfile();
//			} catch (IllegalStateException e) {
//				log.error("Failed to update RuneProfile: " + e.getMessage());
//			}
		} else if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			collectionLogManager = new CollectionLogManager(client, clientThread, configManager);
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		if (collectionLogManager != null) {
			collectionLogManager.onScriptPostFired(scriptPostFired);
		}
	}

//	@Subscribe
//	public void onVarbitChanged(VarbitChanged varbitChanged) {
//		if (collectionLogManager != null) {
//			collectionLogManager.onVarbitChanged(varbitChanged);
//		}
//	}

	public void testStuff() {
		log.info("Test stuff");

		try {
			CollectionLog collectionLog = collectionLogManager.getCollectionLog();

			log.info(collectionLog.toString());

			new Gson().toJson(collectionLog, new FileWriter("collection.json", false));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void updateRuneProfile() throws IllegalStateException {
		if (!isValidWorldType(client.getWorldType())) {
			throw new IllegalStateException("Not on a valid world type");
		}

		long accountHash = client.getAccountHash();

		if (accountHash == -1) {
			throw new IllegalStateException("Failed to get AccountHash");
		}

		Player player = client.getLocalPlayer();

		if (player == null || player.getName() == null) {
			throw new IllegalStateException("Failed to get Player");
		}

		clientThread.invokeLater(() -> {
			try {
				PlayerData playerData = new PlayerData(client);

				new Thread(() -> {
					runeProfileApiClient.createOrUpdateRuneProfile(playerData);
				}).start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
	}


	private boolean isValidWorldType(EnumSet<WorldType> worldTypes) {
		return ImmutableList.of(
						WorldType.DEADMAN,
						WorldType.NOSAVE_MODE,
						WorldType.SEASONAL,
						WorldType.TOURNAMENT_WORLD
		).stream().noneMatch(worldTypes::contains);
	}
}
