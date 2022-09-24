package com.runeprofile.panels;

import com.runeprofile.Icon;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.panels.collectionlog.CollectionLogPanel;
import com.runeprofile.panels.home.HomePanel;
import com.runeprofile.panels.settings.SettingsPanel;
import lombok.Getter;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainPanel extends JPanel {
	private final static int TAB_ICON_SIZE = 20;
	private final JPanel display = new JPanel();
	private final MaterialTabGroup tabGroup = new MaterialTabGroup(display);
	@Getter
	private final CollectionLogPanel collectionLogPanel;

	public MainPanel(RuneProfilePlugin runeProfilePlugin) {
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		display.setBorder(new EmptyBorder(10, 10, 8, 10));

		tabGroup.setLayout(new GridLayout(0, 3, 8, 8));
		tabGroup.setBorder(new EmptyBorder(10, 10, 6, 10));

		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		MaterialTab home = addTab(Icon.HOME.getIcon(TAB_ICON_SIZE, TAB_ICON_SIZE), "Home", new HomePanel(runeProfilePlugin));

		collectionLogPanel = new CollectionLogPanel();
		addTab(Icon.COLLECTION_LOG.getIcon(TAB_ICON_SIZE, TAB_ICON_SIZE), "Collection Log", collectionLogPanel);

		addTab(Icon.SETTINGS.getIcon(TAB_ICON_SIZE, TAB_ICON_SIZE), "Settings", new SettingsPanel(runeProfilePlugin));

		tabGroup.select(home);
	}

	private MaterialTab addTab(ImageIcon icon, String name, JPanel contentPanel) {
		JPanel wrapped = new JPanel(new BorderLayout());
		wrapped.add(contentPanel, BorderLayout.CENTER);
		wrapped.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapped.setBorder(new EmptyBorder(0, 4, 0, 4));

		JScrollPane scroller = new JScrollPane(wrapped);
		scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroller.getVerticalScrollBar().setPreferredSize(new Dimension(16, 0));
		scroller.getVerticalScrollBar().setBorder(new EmptyBorder(0, 9, 0, 0));
		scroller.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTab materialTab = new MaterialTab(new ImageIcon(), tabGroup, scroller);
		materialTab.setPreferredSize(new Dimension(30, 27));
		materialTab.setName(name);
		materialTab.setToolTipText(name);
		materialTab.setIcon(icon);

		tabGroup.addTab(materialTab);

		return materialTab;
	}
}
