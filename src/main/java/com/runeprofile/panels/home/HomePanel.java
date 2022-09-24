package com.runeprofile.panels.home;

import com.runeprofile.RuneProfilePlugin;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HomePanel extends JPanel {
	public HomePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 6));

		Border buttonBorder = new EmptyBorder(8, 16, 8, 16);

		// Update account button
		JButton updateAccountButton = new JButton("Update Account");
		updateAccountButton.setBorder(buttonBorder);
		updateAccountButton.addActionListener((event) -> {
			try {
				runeProfilePlugin.updateAccount();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});

		wrapper.add(updateAccountButton);

		// Update model button
		JButton updateModelButton = new JButton("Update Model");
		updateModelButton.setBorder(buttonBorder);
		updateModelButton.addActionListener((event) -> {
			try {
				runeProfilePlugin.updateModel();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});

		wrapper.add(updateModelButton);

		// Delete profile
		JButton deleteProfileButton = new JButton("Delete Profile");
		deleteProfileButton.setBorder(buttonBorder);
		deleteProfileButton.addActionListener((event) -> SwingUtilities.invokeLater(runeProfilePlugin::deleteProfile));

		wrapper.add(deleteProfileButton);

		add(wrapper, BorderLayout.NORTH);
	}
}
