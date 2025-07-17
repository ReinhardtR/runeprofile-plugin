package com.runeprofile;

import com.runeprofile.panels.InvalidPanel;
import com.runeprofile.panels.LayoutPluginPanel;
import com.runeprofile.panels.HeaderPanel;
import com.runeprofile.utils.Icon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

@Slf4j
public class RuneProfilePanel extends PluginPanel {
    private final EventBus eventBus;
    private final Client client;
    private final ClientToolbar clientToolbar;
    private final RuneProfileConfig config;
    private final NavigationButton navigationButton;
    private final InvalidPanel invalidPanel;
    private final LayoutPluginPanel layoutPluginPanel;

    @Inject
    public RuneProfilePanel(EventBus eventBus, Client client, ClientToolbar clientToolbar, RuneProfileConfig config, HeaderPanel headerPanel, InvalidPanel invalidPanel, LayoutPluginPanel layoutPluginPanel) {
        super(false);
        setLayout(new BorderLayout());

        this.eventBus = eventBus;
        this.client = client;
        this.clientToolbar = clientToolbar;
        this.config = config;
        this.invalidPanel = invalidPanel;
        this.layoutPluginPanel = layoutPluginPanel;

        add(headerPanel, BorderLayout.NORTH);
        loadLoginState();

        final BufferedImage toolbarIcon = Icon.LOGO.getImage();
        navigationButton = NavigationButton.builder()
                .tooltip("RuneProfile")
                .icon(toolbarIcon)
                .panel(this)
                .priority(4)
                .build();
    }

    public void startUp() {
        eventBus.register(this);

        updateState(client.getGameState());

        if (config.showSidePanel()) {
            clientToolbar.addNavigation(navigationButton);
        }
    }

    public void shutDown() {
        eventBus.unregister(this);

        clientToolbar.removeNavigation(navigationButton);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) return;

        if (config.showSidePanel()) {
            clientToolbar.addNavigation(navigationButton);
        } else {
            clientToolbar.removeNavigation(navigationButton);
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        updateState(event.getGameState());
    }

    private void updateState(GameState state) {
        if (state == GameState.LOGGED_IN) {
            RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
            if (profileType != RuneScapeProfileType.STANDARD) {
                loadInvalidPlayerState();
            } else {
                loadValidState();
            }
        } else {
            loadLoginState();
        }
    }

    private void loadValidState() {
        SwingUtilities.invokeLater(() -> {
            if (invalidPanel != null) {
                remove(invalidPanel);
            }

            add(layoutPluginPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
    }

    private void loadLoginState() {
        SwingUtilities.invokeLater(() -> {
            if (layoutPluginPanel != null) {
                remove(layoutPluginPanel);
            }

            invalidPanel.setHintText("Login to use this plugin.");
            add(invalidPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
    }

    private void loadInvalidPlayerState() {
        SwingUtilities.invokeLater(() -> {
            if (layoutPluginPanel != null) {
                remove(layoutPluginPanel);
            }

            invalidPanel.setHintText("Invalid world/mode.");
            add(invalidPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
    }
}
