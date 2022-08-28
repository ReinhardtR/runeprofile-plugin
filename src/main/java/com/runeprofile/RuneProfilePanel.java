package com.runeprofile;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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

		String storedDescription = RuneProfilePlugin.getConfigManager().getConfiguration(
						RuneProfileConfig.CONFIG_GROUP,
						RuneProfileConfig.ACCOUNT_DESCRIPTION_KEY
		);

		JTextArea textArea = new JTextArea(storedDescription);
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				RuneProfilePlugin.getConfigManager().setConfiguration(
								RuneProfileConfig.CONFIG_GROUP,
								RuneProfileConfig.ACCOUNT_DESCRIPTION_KEY,
								textArea.getText()
				);
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				RuneProfilePlugin.getConfigManager().setConfiguration(
								RuneProfileConfig.CONFIG_GROUP,
								RuneProfileConfig.ACCOUNT_DESCRIPTION_KEY,
								textArea.getText()
				);
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				RuneProfilePlugin.getConfigManager().setConfiguration(
								RuneProfileConfig.CONFIG_GROUP,
								RuneProfileConfig.ACCOUNT_DESCRIPTION_KEY,
								textArea.getText()
				);
			}
		});

		add(textArea);
	}
}
