package com.runeprofile.panels.home;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class HomePanel extends JPanel {
	public HomePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 6));

		Border buttonBorder = new EmptyBorder(8, 16, 8, 16);

		// Update account label
		String updatedAccountDate = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.ACCOUNT_UPDATE_DATE);
		JLabel updatedAccountLabel = new JLabel((updatedAccountDate != null) ? "Last update: " + updatedAccountDate : "Last update: Never");
		updatedAccountLabel.setFont(FontManager.getRunescapeSmallFont());

		wrapper.add(updatedAccountLabel);

		// Update account button
		JButton updateAccountButton = new JButton("Update Profile");
		updateAccountButton.setBorder(buttonBorder);
		updateAccountButton.addActionListener((event) -> {
			new Thread(() -> {
				updateAccountButton.setEnabled(false);

				String lastUpdated = "Failed";

				try {
					lastUpdated = runeProfilePlugin.updateProfile();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				log.info("NEW UPDATE: " + lastUpdated);
				updatedAccountLabel.setText("Last update: " + lastUpdated);
				updateAccountButton.setEnabled(true);
			}).start();
		});

		wrapper.add(updateAccountButton);

		// Update model label
		String updatedModelDate = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.MODEL_UPDATE_DATE);
		JLabel updatedModelLabel = new JLabel((updatedModelDate != null) ? "Last update: " + updatedModelDate : "Last update: Never");
		updatedModelLabel.setFont(FontManager.getRunescapeSmallFont());

		wrapper.add(updatedModelLabel);

		// Update model button
		JButton updateModelButton = new JButton("Update Model");
		updateModelButton.setBorder(buttonBorder);
		updateModelButton.addActionListener((event) -> {
			new Thread(() -> {
				updateModelButton.setEnabled(false);

				String lastUpdated = "Failed";

				try {
					lastUpdated = runeProfilePlugin.updateModel();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				
				updatedModelLabel.setText("Last update: " + lastUpdated);
				updateModelButton.setEnabled(true);
			}).start();
		});

		wrapper.add(updateModelButton);

		add(wrapper, BorderLayout.NORTH);
	}
}
