package com.runeprofile.panels;

import com.runeprofile.RuneProfilePlugin;
import net.runelite.client.ui.DynamicGridLayout;

import javax.swing.*;
import java.awt.*;

public class ValidPanel extends JPanel {
	public ValidPanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new DynamicGridLayout(0, 1, 0, 3));

		// Update account button
		JButton updateAccountButton = new JButton("Update Account");
		updateAccountButton.addActionListener((event) -> {
			try {
				runeProfilePlugin.updateAccount();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});

		add(updateAccountButton);

		// Update model button
		JButton updateModelButton = new JButton("Update Model");
		updateModelButton.addActionListener((event) -> {
			try {
				runeProfilePlugin.updateModel();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		});

		add(updateModelButton);

		// Delete profile
		JButton deleteProfileButton = new JButton("Delete Profile");
		deleteProfileButton.addActionListener((event) -> SwingUtilities.invokeLater(runeProfilePlugin::deleteProfile));

		add(deleteProfileButton);

		// Container
		JPanel container = new JPanel();
		BoxLayout layout = new BoxLayout(container, BoxLayout.Y_AXIS);
		container.setLayout(layout);

		add(container);

		JPanel descriptionContainer = new DescriptionPanel();
		JPanel hiddenProfileContainer = new HiddenProfilePanel(runeProfilePlugin);

		container.add(Box.createRigidArea(new Dimension(0, 10)));
		container.add(descriptionContainer);
		container.add(Box.createRigidArea(new Dimension(0, 10)));
		container.add(hiddenProfileContainer);
	}
}
