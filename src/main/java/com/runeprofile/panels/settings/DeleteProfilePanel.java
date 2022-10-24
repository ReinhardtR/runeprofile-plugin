package com.runeprofile.panels.settings;

import com.runeprofile.RuneProfilePlugin;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import java.awt.*;

public class DeleteProfilePanel extends JPanel {
	public DeleteProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new DynamicGridLayout(0, 1, 0, 4));

		JLabel titleLabel = new JLabel("Delete Profile");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		add(titleLabel);

		// Delete profile
		JButton deleteProfileButton = new JButton("Delete Profile");
		deleteProfileButton.addActionListener((event) -> {
			SwingUtilities.invokeLater(() -> deleteProfileButton.setEnabled(false));

			runeProfilePlugin.deleteProfile();

			SwingUtilities.invokeLater(() -> deleteProfileButton.setEnabled(true));
		});

		add(deleteProfileButton);
	}
}
