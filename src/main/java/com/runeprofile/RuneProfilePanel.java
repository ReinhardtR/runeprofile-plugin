package com.runeprofile;

import com.runeprofile.panels.InvalidPanel;
import com.runeprofile.panels.MainPanel;
import com.runeprofile.panels.misc.HeaderPanel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import java.awt.*;

@Slf4j
public class RuneProfilePanel extends PluginPanel {
	private final RuneProfilePlugin runeProfilePlugin;

	@Getter
	private MainPanel mainPanel;
	private InvalidPanel invalidPanel;

	public RuneProfilePanel(RuneProfilePlugin runeProfilePlugin) {
		super(false);
		setLayout(new BorderLayout());

		this.runeProfilePlugin = runeProfilePlugin;

		add(new HeaderPanel(), BorderLayout.NORTH);
		loadInvalidState();
	}

	public void loadValidState() {
		SwingUtilities.invokeLater(() -> {
			if (invalidPanel != null) {
				remove(invalidPanel);
			}

			mainPanel = new MainPanel(runeProfilePlugin);

			add(mainPanel, BorderLayout.CENTER);
			revalidate();
			repaint();
		});
	}

	public void loadInvalidState() {
		SwingUtilities.invokeLater(() -> {
			if (mainPanel != null) {
				remove(mainPanel);
			}

			if (invalidPanel == null) {
				invalidPanel = new InvalidPanel();
			}

			invalidPanel.setHintText("Login to use this plugin.");
			add(invalidPanel, BorderLayout.CENTER);
			revalidate();
			repaint();
		});
	}

	public void loadInvalidRequestState() {
		SwingUtilities.invokeLater(() -> {
			if (mainPanel != null) {
				remove(mainPanel);
			}

			if (invalidPanel == null) {
				invalidPanel = new InvalidPanel();
			}

			invalidPanel.setHintText("Invalid world/mode.");
			add(invalidPanel, BorderLayout.CENTER);
			revalidate();
			repaint();
		});
	}
}
