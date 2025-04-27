package com.runeprofile;

import com.runeprofile.panels.InvalidPanel;
import com.runeprofile.panels.LayoutPluginPanel;
import com.runeprofile.panels.HeaderPanel;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;

@Slf4j
public class RuneProfilePanel extends net.runelite.client.ui.PluginPanel {
    private final LayoutPluginPanel layoutPluginPanel;
    private InvalidPanel invalidPanel;

    @Inject
    public RuneProfilePanel(HeaderPanel headerPanel, LayoutPluginPanel layoutPluginPanel) {
        super(false);
        setLayout(new BorderLayout());

        this.layoutPluginPanel = layoutPluginPanel;

        add(headerPanel, BorderLayout.NORTH);
        loadInvalidState();
    }

    public void loadValidState() {
        SwingUtilities.invokeLater(() -> {
            if (invalidPanel != null) {
                remove(invalidPanel);
            }

            add(layoutPluginPanel, BorderLayout.CENTER);
            revalidate();
            repaint();
        });
    }

    public void loadInvalidState() {
        SwingUtilities.invokeLater(() -> {
            if (layoutPluginPanel != null) {
                remove(layoutPluginPanel);
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
            if (layoutPluginPanel != null) {
                remove(layoutPluginPanel);
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
