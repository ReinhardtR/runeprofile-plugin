package com.runeprofile.panels;

import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.panels.home.HomePanel;
import net.runelite.client.ui.ColorScheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class MainPanel extends JPanel {

    public MainPanel(RuneProfilePlugin runeProfilePlugin) {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        HomePanel homePanel = new HomePanel(runeProfilePlugin);
        homePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(homePanel, BorderLayout.NORTH);

        JPanel display = new JPanel();
        display.setLayout(new BoxLayout(display, BoxLayout.Y_AXIS));
        display.setBorder(new EmptyBorder(10, 10, 8, 10));

        add(display, BorderLayout.CENTER);
    }
}