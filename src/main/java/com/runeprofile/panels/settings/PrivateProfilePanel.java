package com.runeprofile.panels.settings;

import com.google.gson.JsonObject;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class PrivateProfilePanel extends JPanel {
	private final AtomicReference<String> privateUrl = new AtomicReference<>();
	private final JLabel urlLabel = new JLabel();

	public PrivateProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 4));

		Border buttonBorder = new EmptyBorder(8, 16, 8, 16);

		String storedUrl = RuneProfilePlugin.getConfigManager().getRSProfileConfiguration(RuneProfileConfig.CONFIG_GROUP, RuneProfileConfig.GENERATED_PATH);
		privateUrl.set(storedUrl == null ? "None" : storedUrl);
		urlLabel.setText(getHiddenURL(privateUrl.get()));

		JLabel titleLabel = new JLabel("Private Profile URL");
		titleLabel.setFont(FontManager.getRunescapeBoldFont());
		titleLabel.setForeground(Color.WHITE);
		wrapper.add(titleLabel);

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
					String path = response.get("generatedPath").getAsString();
					setNewURL(path);
				} catch (Exception e) {
					privateCheckbox.setSelected(!privateCheckbox.isSelected());
				}

				SwingUtilities.invokeLater(() -> privateCheckbox.setEnabled(true));
			}).start();
		});
		wrapper.add(privateCheckbox);

		JPanel urlContainer = new JPanel();
		urlContainer.setLayout(new BorderLayout());
		urlContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		urlContainer.setBorder(new EmptyBorder(4, 4, 4, 4));

		urlLabel.setFont(new Font("Courier New", Font.PLAIN, 11));
		urlContainer.add(urlLabel);
		wrapper.add(urlContainer);

		JButton copyButton = new JButton("Copy");
		copyButton.addActionListener(e -> {
			StringSelection stringSelection = new StringSelection(privateUrl.get());
			Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
			clipboard.setContents(stringSelection, null);
		});
		copyButton.setBorder(buttonBorder);
		wrapper.add(copyButton);

		JButton newButton = new JButton("Generate New URL");
		newButton.addActionListener((event) -> {
			new Thread(() -> {
				newButton.setEnabled(false);

				try {
					String path = runeProfilePlugin.updateGeneratedPath();
					setNewURL(path);
				} catch (Exception e) {
					e.printStackTrace();
				}

				newButton.setEnabled(true);
			}).start();
		});
		newButton.setBorder(buttonBorder);
		wrapper.add(newButton);

		add(wrapper, BorderLayout.NORTH);
	}

	private void setNewURL(String path) {
		String newUrl = path.equals("None") ? path : "runeprofile.com/" + path;

		privateUrl.set(newUrl);
		SwingUtilities.invokeLater(() -> urlLabel.setText(getHiddenURL(privateUrl.get())));
	}

	private String getHiddenURL(String url) {
		if (url.equals("None")) return url;

		try {
			int charactersToShow = 4;

			String[] path = url.split("/");

			String generatedPath = path[1];
			String firstCharacters = generatedPath.substring(0, charactersToShow);
			String stars = StringUtils.repeat("*", generatedPath.length() - charactersToShow);
			String fullProfilePath = firstCharacters + stars;

			return path[0] + "/" + fullProfilePath;
		} catch (Exception e) {
			log.error("Error getting hidden URL", e);
			return "None";
		}
	}
}
