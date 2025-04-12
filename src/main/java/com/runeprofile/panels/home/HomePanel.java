package com.runeprofile.panels.home;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class HomePanel extends JPanel {

    private final Border buttonBorder;

    public HomePanel(RuneProfilePlugin plugin) {
        buttonBorder = new EmptyBorder(8, 16, 8, 16);

        setLayout(new BorderLayout());
        JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 6));

        // Update Model
        JLabel updatedModelLabel = createUpdateLabel(RuneProfileConfig.MODEL_UPDATE_DATE);
        JButton updateModelButton = createUpdateButton(
                "Update Model",
                updatedModelLabel,
                plugin::updateModelAsync
        );

        wrapper.add(updatedModelLabel);
        wrapper.add(updateModelButton);


        // Open Profile
        JButton openProfileButton = new JButton("Open Profile");
        openProfileButton.setBorder(buttonBorder);
        openProfileButton.addActionListener(e -> {
            String username = RuneProfilePlugin.getClient().getLocalPlayer().getName();
            LinkBrowser.browse("https://runeprofile.com/" + username);
        });

        wrapper.add(openProfileButton);

        if (plugin.isDeveloperMode()) {
            // DEV ONLY - generate hiscores icons
            JButton generateHiscoreIcons = new JButton("DEV: Hiscores Icons");
            generateHiscoreIcons.setBorder(buttonBorder);
            generateHiscoreIcons.addActionListener(e -> {
                plugin.DEV_generateHiscoreIconsJson();
            });
            wrapper.add(generateHiscoreIcons);
        }

        add(wrapper, BorderLayout.NORTH);
    }

    private JLabel createUpdateLabel(String configKey) {
        String lastUpdatedDate = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, configKey);
        JLabel label = new JLabel((lastUpdatedDate != null) ? "Last update: " + lastUpdatedDate : "Last update: Never");
        label.setFont(FontManager.getRunescapeSmallFont());
        return label;
    }

    private JButton createUpdateButton(String buttonText, JLabel statusLabel, SupplierWithException<CompletableFuture<String>, Exception> updateAction) {
        JButton button = new JButton(buttonText);
        button.setBorder(buttonBorder);
        button.addActionListener((event) -> {
            button.setEnabled(false);
            statusLabel.setText("Last update: Updating...");
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() throws Exception {
                    try {
                        return updateAction.get().get();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return "Update interrupted";
                    } catch (IOException e) {
                        log.debug(Arrays.toString(e.getStackTrace()));
                        log.error("Failed to update: {}", e.toString());
                        return "Failed to update";
                    }
                }

                @Override
                protected void done() {
                    try {
                        String lastUpdated = get();
                        statusLabel.setText("Last update: " + lastUpdated);
                    } catch (Exception e) {
                        log.debug(Arrays.toString(e.getStackTrace()));
                        log.error("Failed to update: {}", e.toString());
                        statusLabel.setText("Last update: Failed");
                    } finally {
                        button.setEnabled(true);
                    }
                }
            }.execute();
        });
        return button;
    }
}

@FunctionalInterface
interface SupplierWithException<T, E extends Exception> {
    T get() throws E;
}