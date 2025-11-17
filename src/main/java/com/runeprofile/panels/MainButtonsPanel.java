package com.runeprofile.panels;

import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.utils.DevTools;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

@Slf4j
public class MainButtonsPanel extends JPanel {
    @Inject
    public MainButtonsPanel(RuneProfilePlugin plugin, Client client, DevTools devTools) {
        setLayout(new BorderLayout());
        JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 6));

        // Update Model
        JButton updateModelButton = createButton("Update Player Model");
        updateModelButton.addActionListener((e) -> plugin.updateModelAsync());
        wrapper.add(updateModelButton);

        // Open Profile
        JButton openProfileButton = createButton("Open Profile");
        openProfileButton.addActionListener(e -> {
            String username = client.getLocalPlayer().getName();
            if (username == null) return;
            Utils.openProfileInBrowser(username);
        });
        wrapper.add(openProfileButton);

        // Delete Profile
        JButton deleteProfileButton = createButton("Delete Profile");
        deleteProfileButton.addActionListener(e -> {
            final int confirmed = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to delete your profile? All activity history will be lost.\nOBS: Your profile will be re-created on next autosync or manual update.",
                    "Confirm Profile Deletion",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirmed == JOptionPane.YES_OPTION) {
                plugin.deleteProfileAsync();
            }
        });
        wrapper.add(deleteProfileButton);

        if (plugin.isDeveloperMode()) {
            // DEV ONLY - generate hiscores icons
            JButton generateHiscoreIcons = createButton("DEV: Hiscores Icons");
            generateHiscoreIcons.addActionListener(e -> {
                devTools.generateHiscoreIconsJson();
            });
            wrapper.add(generateHiscoreIcons);

            // DEV ONLY - generate clan rank icons
            JButton generateClanRankIcons = createButton("DEV: Clan Rank Icons");
            generateClanRankIcons.addActionListener(e -> {
                devTools.generateClanRankIconsJson();
            });
            wrapper.add(generateClanRankIcons);

            // DEV ONLY - generate item icons
            JButton generateItemIcons = createButton("DEV: Item Icons");
            generateItemIcons.addActionListener(e -> {
                devTools.generateItemIconsJson();
            });
            wrapper.add(generateItemIcons);
        }

        add(wrapper, BorderLayout.NORTH);
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 30));
        return button;
    }
}

