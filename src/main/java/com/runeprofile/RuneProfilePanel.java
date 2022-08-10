package com.runeprofile;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;

@Slf4j
public class RuneProfilePanel extends PluginPanel {
	public RuneProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		super(false);

		JButton requestButton = new JButton("Send Request");
		requestButton.addActionListener((event) -> {
			log.info("Request button clicked");
			runeProfilePlugin.getClientThread().invokeLater(runeProfilePlugin::updateRuneProfile);
		});

		add(requestButton);

		JButton testButton = new JButton("Test Stuff");
		testButton.addActionListener((event) -> {
			log.info("Test button clicked");
			runeProfilePlugin.getClientThread().invokeLater(runeProfilePlugin::testStuff);
		});

		add(testButton);
	}
}
