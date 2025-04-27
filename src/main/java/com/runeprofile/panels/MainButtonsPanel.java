package com.runeprofile.panels;

import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;


@Slf4j
public class MainButtonsPanel extends JPanel {
    @Inject
    public MainButtonsPanel(RuneProfilePlugin plugin, Client client, ClientThread clientThread) {
        setLayout(new BorderLayout());
        JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 6));

        // Update Model
        JButton updateModelButton = createButton("Update Player Model");
        updateModelButton.addActionListener((e) -> {
            clientThread.invokeLater(() -> {
                client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Updating player model...", "RuneProfile");
            });
            plugin.updateModelAsync();
        });
        wrapper.add(updateModelButton);

        // Open Profile
        JButton openProfileButton = createButton("Open Profile");
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

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 30));
        return button;
    }
}

