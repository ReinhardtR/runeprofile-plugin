package com.runeprofile.panels;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.utils.SupplierWithException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Slf4j
public class MainButtonsPanel extends JPanel {
    @Inject
    public MainButtonsPanel(RuneProfilePlugin plugin, ConfigManager configManager) {
        setLayout(new BorderLayout());
        JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 6));

        // Update Model
        JLabel updatedModelLabel = createUpdateLabel(configManager);
        JButton updateModelButton = createUpdateButton(
                updatedModelLabel,
                plugin::updateModelAsync
        );

        wrapper.add(updatedModelLabel);
        wrapper.add(updateModelButton);


        // Open Profile
        JButton openProfileButton = new JButton("Open Profile");
        openProfileButton.addActionListener(e -> {
            String username = RuneProfilePlugin.getClient().getLocalPlayer().getName();
            if (username == null) return;
            plugin.openProfileInBrowser(username);
        });

        wrapper.add(openProfileButton);

        if (plugin.isDeveloperMode()) {
            // DEV ONLY - generate hiscores icons
            JButton generateHiscoreIcons = new JButton("DEV: Hiscores Icons");
            generateHiscoreIcons.addActionListener(e -> {
                plugin.DEV_generateHiscoreIconsJson();
            });
            wrapper.add(generateHiscoreIcons);
        }

        add(wrapper, BorderLayout.NORTH);
    }

    private JLabel createUpdateLabel(ConfigManager configManager) {
        String lastUpdatedDate = configManager.getRSProfileConfiguration(RuneProfilePlugin.CONFIG_GROUP, RuneProfileConfig.MODEL_UPDATE_DATE);
        JLabel label = new JLabel((lastUpdatedDate != null) ? "Last update: " + lastUpdatedDate : "Last update: Never");
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    private JButton createUpdateButton(JLabel statusLabel, SupplierWithException<CompletableFuture<String>, Exception> updateAction) {
        JButton button = new JButton("Update Model");
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 30));
        button.addActionListener((event) -> {
            button.setEnabled(false);
            statusLabel.setText("Updating...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    try {
                        String dateString = updateAction.get().get();
                        return "Last update: " + dateString;
                    } catch (Exception e) {
                        return RuneProfilePlugin.getApiErrorMessage(e, "Failed to update model");
                    }
                }

                @Override
                protected void done() {
                    String newLabel = null;

                    try {
                        newLabel = get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(e);
                    } finally {
                        button.setEnabled(true);
                    }

                    statusLabel.setText(newLabel);
                }
            }.execute();
        });
        return button;
    }
}

