package com.runeprofile.panels.settings;

import com.runeprofile.RuneProfilePlugin;

import javax.swing.*;
import java.awt.*;

public class SettingsPanel extends JPanel {
	public SettingsPanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new GridLayout(0, 1, 0, 32));

		wrapper.add(new PrivateProfilePanel(runeProfilePlugin));
		wrapper.add(new DescriptionPanel(runeProfilePlugin));

		add(wrapper, BorderLayout.NORTH);
	}
}
