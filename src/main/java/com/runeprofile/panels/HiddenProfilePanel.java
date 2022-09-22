package com.runeprofile.panels;

import com.google.gson.JsonObject;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class HiddenProfilePanel extends JPanel {
	private final AtomicReference<String> url = new AtomicReference<>();
	private final JLabel urlLabel = new JLabel();

	public HiddenProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		String storedUrl = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.GENERATED_PATH);
		url.set(storedUrl == null ? "None" : storedUrl);
		urlLabel.setText(getHiddenURL(url.get()));

		JPanel container = new JPanel();
		container.setLayout(new GridLayout(0, 1));

		JLabel titleLabel = new JLabel("Secret URL");
		titleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
		container.add(titleLabel);

		boolean storedIsPrivate = Boolean.parseBoolean(RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.IS_PRIVATE));

		JCheckBox privateCheckbox = new JCheckBox("Private profile");
		privateCheckbox.setSelected(storedIsPrivate);
		privateCheckbox.addActionListener((event) -> SwingUtilities.invokeLater(() -> {
			privateCheckbox.setEnabled(false);

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

			privateCheckbox.setEnabled(true);
		}));
		container.add(privateCheckbox);

		JButton copyButton = new JButton("Copy");
		copyButton.addActionListener(e -> {
			StringSelection stringSelection = new StringSelection(url.get());
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, null);
		});

		JButton newButton = new JButton("Generate New URL");
		newButton.addActionListener((event) -> {
			SwingUtilities.invokeLater(() -> {
				newButton.setEnabled(false);

				try {
					String newURL = "runeprofile.com/u/" + runeProfilePlugin.updateGeneratedPath();
					setNewURL(newURL);
				} catch (Exception e) {
					e.printStackTrace();
				}

				newButton.setEnabled(true);
			});
		});

		JPanel urlContainer = new JPanel();
		urlContainer.setLayout(new BorderLayout());
		urlContainer.setBorder(new EmptyBorder(2, 2, 2, 2));
		urlContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		urlLabel.setFont(new Font("Courier New", Font.PLAIN, 11));
		urlContainer.add(urlLabel);

		container.add(urlContainer);
		container.add(copyButton);
		container.add(newButton);

		add(container);
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
