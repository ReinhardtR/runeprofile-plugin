package com.runeprofile;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import com.runeprofile.collectionlog.CollectionLogManager;
import com.runeprofile.dataobjects.PlayerData;
import com.runeprofile.dataobjects.PlayerModelData;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ScriptPostFired;
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
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@PluginDescriptor(
				name = "RuneProfile",
				description = "Show off your achievements on RuneProfile.com",
				tags = {"rune", "profile"}
)
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

	@Inject
	private ClientThread clientThread;

	@Inject
	private HiscoreClient hiscoreClient;

	@Inject
	private ConfigManager configManager;

	private RuneProfilePanel runeProfilePanel;
	private NavigationButton navigationButton;
	private CollectionLogManager collectionLogManager;

	public static CollectionLogManager getCollectionLogManager() {
		return instance.collectionLogManager;
	}

	public static Client getClient() {
		return instance.client;
	}

	public static ClientThread getClientThread() {
		return instance.clientThread;
	}

	public static HiscoreClient getHiscoreClient() {
		return instance.hiscoreClient;
	}

	public static ConfigManager getConfigManager() {
		return instance.configManager;
	}

	public static RuneProfilePanel getPanel() {
		return instance.runeProfilePanel;
	}

	@Provides
	RuneProfileConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(RuneProfileConfig.class);
	}

	@Override
	protected void startUp() {
		instance = this;

		this.runeProfilePanel = new RuneProfilePanel(this);
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

		log.info("Game state changed: {}", gameStateChanged.getGameState());
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
			if (!isValidWorldType(client.getWorldType())) {
				runeProfilePanel.loadInvalidRequestState();
				return;
			}

			// Collection Log Manager is null, if it's the first time logging in.
			// Need to reload the manager if it's a different account.
			if (collectionLogManager == null) {
				collectionLogManager = new CollectionLogManager();
			} else {
				collectionLogManager.reloadManager();
			}

			runeProfilePanel.loadValidState();
		} else if (gameStateChanged.getGameState() == GameState.LOGIN_SCREEN) {
			runeProfilePanel.loadInvalidState();

			// If Collection Log Manager isn't null, then the client was logged in before.
			if (collectionLogManager != null) {
				if (config.updateOnLogout()) {
					try {
						updateAccount();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired) {
		if (collectionLogManager != null) {
			collectionLogManager.onScriptPostFired(scriptPostFired);
		}
	}

	public void updateAccount() throws IllegalStateException, InterruptedException {
		isValidRequest();

		new Thread(() -> {
			try {
				PlayerData playerData = new PlayerData(this);
				runeProfileApiClient.updateAccount(playerData);
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}).start();
	}

	public void updateModel() throws IllegalStateException, InterruptedException {
		isValidRequest();

		AtomicReference<PlayerModelData> playerModelData = new AtomicReference<>();

		CountDownLatch latch = new CountDownLatch(1);

		clientThread.invokeLater(() -> {
			try {
				playerModelData.set(new PlayerModelData(client));
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				latch.countDown();
			}
		});

		latch.await();

		new Thread(() -> {
			runeProfileApiClient.updateModel(playerModelData.get());
		}).start();
	}

	public String updateGeneratedPath() throws Exception {
		isValidRequest();

		long accountHash = client.getAccountHash();

		AtomicReference<String> newUrl = new AtomicReference<>();

		CountDownLatch latch = new CountDownLatch(1);

		new Thread(() -> {
			try {
				String newUrlResult = runeProfileApiClient.updateGeneratedPath(accountHash);
				newUrl.set(newUrlResult);
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				latch.countDown();
			}
		}).start();

		latch.await();

		if (newUrl.get() == null) {
			throw new Exception("Failed to update generated path");
		}

		configManager.setRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.GENERATED_PATH,
						newUrl.get()
		);

		return newUrl.get();
	}

	public JsonObject updateIsPrivate(boolean isPrivate) throws Exception {
		isValidRequest();

		long accountHash = client.getAccountHash();

		AtomicReference<JsonObject> response = new AtomicReference<>();

		CountDownLatch latch = new CountDownLatch(1);

		new Thread(() -> {
			try {
				JsonObject newIsPrivateResult = runeProfileApiClient.updateIsPrivate(accountHash, isPrivate);
				response.set(newIsPrivateResult);
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				latch.countDown();
			}
		}).start();

		latch.await();

		configManager.setRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.GENERATED_PATH,
						response.get().get("isPrivate").getAsBoolean()
		);

		configManager.setRSProfileConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.GENERATED_PATH,
						response.get().get("generatedPath").getAsString()
		);

		return response.get();
	}

	public String updateDescription(String description) {
		isValidRequest();

		long accountHash = client.getAccountHash();

		AtomicReference<String> newDescription = new AtomicReference<>();

		CountDownLatch latch = new CountDownLatch(1);

		new Thread(() -> {
			try {
				String newDescriptionResult = runeProfileApiClient.updateDescription(accountHash, description);
				newDescription.set(newDescriptionResult);
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				latch.countDown();
			}
		}).start();

		try {
			latch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (newDescription.get() == null) {
			throw new RuntimeException("Failed to update description");
		}

		return newDescription.get();
	}

	public void deleteProfile() {
		isValidRequest();

		if (!isConfirmedDeletion("Are you sure you want to delete your RuneProfile?")) {
			return;
		}

		clientThread.invokeLater(() -> {
			long accountHash = client.getAccountHash();

			new Thread(() -> {
				runeProfileApiClient.deleteProfile(accountHash);
			}).start();
		});

		runeProfilePanel.loadInvalidState();
		configManager.unsetRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.COLLECTION_LOG);
		configManager.unsetRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.GENERATED_PATH);
		configManager.unsetRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.DESCRIPTION);
		configManager.unsetRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.IS_PRIVATE);
		configManager.unsetRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.HAS_MODEL);
		collectionLogManager.reloadManager();
		runeProfilePanel.loadValidState();
	}

	private boolean isConfirmedDeletion(String message) {
		int confirm = JOptionPane.showConfirmDialog(
						runeProfilePanel,
						message,
						"RuneProfile",
						JOptionPane.OK_CANCEL_OPTION
		);

		return confirm == JOptionPane.YES_OPTION;
	}

	private void isValidRequest() throws IllegalStateException {
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
