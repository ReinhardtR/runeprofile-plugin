package com.runeprofile.panels;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LayoutPluginPanel extends JPanel {
    @Inject
    public LayoutPluginPanel(MainButtonsPanel mainButtonsPanel, SearchPanel searchPanel) {
        setLayout(new BorderLayout());

        // Main content panel with vertical (flex-column-like) layout
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBorder(new EmptyBorder(10, 10, 10, 10)); // padding around entire content

        // Create and add components
        mainButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(mainButtonsPanel);
        mainButtonsPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, mainButtonsPanel.getPreferredSize().height)); // Limit vertical growth

        contentPanel.add(Box.createVerticalStrut(32)); // spacing between components

        searchPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(searchPanel);
        searchPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, searchPanel.getPreferredSize().height)); // Limit initial vertical growth

        contentPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(scrollPane, BorderLayout.CENTER);
    }
}