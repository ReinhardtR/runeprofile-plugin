package com.runeprofile.panels;

import net.runelite.client.ui.FontManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class InvalidPanel extends JPanel {
	private final JLabel hintText = new JLabel();

	public InvalidPanel() {
		setBorder(new EmptyBorder(50, 10, 0, 10));
		setLayout(new BorderLayout());

		JPanel wrapper = new JPanel(new BorderLayout());

		JLabel titleText = new JLabel("RuneProfile");
		titleText.setFont(FontManager.getRunescapeFont());
		titleText.setForeground(Color.WHITE);
		titleText.setHorizontalAlignment(SwingConstants.CENTER);
		wrapper.add(titleText, BorderLayout.NORTH);

		hintText.setFont(FontManager.getRunescapeSmallFont());
		hintText.setForeground(Color.GRAY);
		hintText.setHorizontalAlignment(SwingConstants.CENTER);
		wrapper.add(hintText, BorderLayout.CENTER);

		add(wrapper, BorderLayout.NORTH);
	}

	public void setHintText(String text) {
		SwingUtilities.invokeLater(() -> hintText.setText(text));
	}
}
