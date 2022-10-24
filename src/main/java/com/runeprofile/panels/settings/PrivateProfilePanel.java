package com.runeprofile.panels.settings;

import com.google.gson.JsonObject;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.DynamicGridLayout;
import net.runelite.client.ui.FontManager;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PrivateProfilePanel extends JPanel {
	private final AtomicReference<String> url = new AtomicReference<>();
	private final JLabel urlLabel = new JLabel();

	public PrivateProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new DynamicGridLayout(0, 1, 0, 4));

		String storedUrl = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.GENERATED_PATH);
		url.set(storedUrl == null ? "None" : storedUrl);
		urlLabel.setText(getHiddenURL(url.get()));

		JLabel titleLabel = new JLabel("Private Profile URL");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		add(titleLabel);

		String isPrivateString = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.IS_PRIVATE);
		System.out.println("isPrivateString: " + isPrivateString);
		boolean storedIsPrivate = Boolean.parseBoolean(isPrivateString);
		System.out.println("storedIsPrivate: " + storedIsPrivate);

		JCheckBox privateCheckbox = new JCheckBox("Private profile");
		privateCheckbox.setToolTipText("Disables the public username URL and generates a hidden URL instead.");
		privateCheckbox.setSelected(storedIsPrivate);
		privateCheckbox.addActionListener((event) -> {
			new Thread(() -> {
				SwingUtilities.invokeLater(() -> privateCheckbox.setEnabled(false));

				try {
					JsonObject response = runeProfilePlugin.updateIsPrivate(privateCheckbox.isSelected());

					// Sync checkbox with server state
					privateCheckbox.setSelected(response.get("isPrivate").getAsBoolean());

					// Sync url with possibly new generated path
					String newURL = "runeprofile.com/u/" + response.get("generatedPath").getAsString();
					setNewURL(newURL);
				} catch (Exception e) {
					privateCheckbox.setSelected(!privateCheckbox.isSelected());
				}

				SwingUtilities.invokeLater(() -> privateCheckbox.setEnabled(true));
			}).start();
		});
		add(privateCheckbox);

		JPanel urlContainer = new JPanel();
		urlContainer.setLayout(new BorderLayout());
		urlContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		urlContainer.setBorder(new EmptyBorder(4, 4, 4, 4));

		urlLabel.setFont(new Font("Courier New", Font.PLAIN, 11));
		urlContainer.add(urlLabel);
		add(urlContainer);

		JButton copyButton = new JButton("Copy");
		copyButton.addActionListener(e -> {
			StringSelection stringSelection = new StringSelection(url.get());
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, null);
		});
		add(copyButton);

		JButton newButton = new JButton("Generate New URL");
		newButton.addActionListener((event) -> {
			new Thread(() -> {
				newButton.setEnabled(false);

				try {
					String newURL = "runeprofile.com/u/" + runeProfilePlugin.updateGeneratedPath();
					setNewURL(newURL);
				} catch (Exception e) {
					e.printStackTrace();
				}

				newButton.setEnabled(true);
			}).start();
		});
		add(newButton);
	}

	private void setNewURL(String newUrl) {
		if (!newUrl.equals(url.get())) {
			url.set(newUrl);
			SwingUtilities.invokeLater(() -> urlLabel.setText(getHiddenURL(url.get())));
		}
	}

	private String getHiddenURL(String url) {
		if (url.equals("None")) return url;

		try {
			int charactersToShow = 4;
			// only include the first 4 characters of the path
			String[] path = url.split("/");

			String generatedPath = path[2];
			String firstCharacters = generatedPath.substring(0, charactersToShow);
			String stars = StringUtils.repeat("*", generatedPath.length() - charactersToShow);
			String fullProfilePath = firstCharacters + stars;

			return path[0] + "/" + path[1] + "/" + fullProfilePath;
		} catch (Exception e) {
			return "None";
		}
	}
}
