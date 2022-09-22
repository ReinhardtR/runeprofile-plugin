package com.runeprofile;

import com.runeprofile.panels.HeaderPanel;
import com.runeprofile.panels.InvalidPanel;
import com.runeprofile.panels.ValidPanel;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;

@Slf4j
public class RuneProfilePanel extends PluginPanel {
	private final RuneProfilePlugin runeProfilePlugin;
	private ValidPanel validPanel;
	private InvalidPanel invalidPanel;

	public RuneProfilePanel(RuneProfilePlugin runeProfilePlugin) {

		this.runeProfilePlugin = runeProfilePlugin;

		add(new HeaderPanel());
		loadInvalidState();
	}

	private void addHeader() {

	}

	public void loadValidState() {
		SwingUtilities.invokeLater(() -> {
			if (invalidPanel != null) {
				remove(invalidPanel);
			}

			if (validPanel == null) {
				validPanel = new ValidPanel(runeProfilePlugin);
			}

			add(validPanel);
			revalidate();
			repaint();
		});
	}

	public void loadInvalidState() {
		SwingUtilities.invokeLater(() -> {
			if (validPanel != null) {
				remove(validPanel);
			}

			if (invalidPanel == null) {
				invalidPanel = new InvalidPanel();
			}

			invalidPanel.setHintText("Login to use this plugin.");
			add(invalidPanel);
			revalidate();
			repaint();
		});
	}

	public void loadInvalidRequestState() {
		SwingUtilities.invokeLater(() -> {
			if (validPanel != null) {
				remove(validPanel);
			}

			if (invalidPanel == null) {
				invalidPanel = new InvalidPanel();
			}

			invalidPanel.setHintText("Invalid world/mode.");
			add(invalidPanel);
			revalidate();
			repaint();
		});
	}
}
