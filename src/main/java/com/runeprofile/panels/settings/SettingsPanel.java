package com.runeprofile.panels.settings;

import com.runeprofile.RuneProfilePlugin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class SettingsPanel extends JPanel {
	public SettingsPanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 16));

		wrapper.add(new PrivateProfilePanel(runeProfilePlugin));
		wrapper.add(new DescriptionPanel(runeProfilePlugin));

		DeleteProfilePanel deleteProfilePanel = new DeleteProfilePanel(runeProfilePlugin);
		deleteProfilePanel.setBorder(new EmptyBorder(32, 0, 0, 0));
		wrapper.add(deleteProfilePanel);

		add(wrapper, BorderLayout.NORTH);
	}
}
