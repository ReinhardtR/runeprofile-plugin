package com.runeprofile.panels.settings;

import com.runeprofile.RuneProfilePlugin;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class DeleteProfilePanel extends JPanel {
	public DeleteProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 0));

		JLabel titleLabel = new JLabel("Delete Profile");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		wrapper.add(titleLabel);

		// Delete profile
		JButton deleteProfileButton = new JButton("Delete Profile");
		deleteProfileButton.addActionListener((event) -> {
			SwingUtilities.invokeLater(() -> deleteProfileButton.setEnabled(false));

			runeProfilePlugin.deleteProfile();

			SwingUtilities.invokeLater(() -> deleteProfileButton.setEnabled(true));
		});
		deleteProfileButton.setBorder(new EmptyBorder(8, 16, 8, 16));

		wrapper.add(deleteProfileButton);

		add(wrapper, BorderLayout.NORTH);
	}
}
