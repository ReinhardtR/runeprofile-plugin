package com.runeprofile.panels;

import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LayoutPluginPanel extends JPanel {
    private final ProfileTabPanel profileTabPanel;
    private final DropsTabPanel dropsTabPanel;
    private final SearchTabPanel searchTabPanel;

    @Inject
    public LayoutPluginPanel(ProfileTabPanel profileTabPanel, DropsTabPanel dropsTabPanel, SearchTabPanel searchTabPanel) {
        this.profileTabPanel = profileTabPanel;
        this.dropsTabPanel = dropsTabPanel;
        this.searchTabPanel = searchTabPanel;

        setLayout(new BorderLayout());

        // Holds the currently visible tab content
        JPanel display = new ScrollableDisplayPanel();

        MaterialTabGroup tabGroup = new MaterialTabGroup(display);
        tabGroup.setBorder(new EmptyBorder(8, 10, 0, 10));

        MaterialTab profileTab = new MaterialTab("Profile", tabGroup, profileTabPanel);
        profileTab.setOnSelectEvent(() -> {
            profileTabPanel.refresh(false);
            return true;
        });

        MaterialTab dropsTab = new MaterialTab("Drops", tabGroup, dropsTabPanel);
        dropsTab.setOnSelectEvent(() -> {
            dropsTabPanel.refresh(false);
            return true;
        });

        MaterialTab searchTab = new MaterialTab("Search", tabGroup, searchTabPanel);
        searchTab.setOnSelectEvent(() -> {
            searchTabPanel.refresh(false);
            return true;
        });

        tabGroup.addTab(profileTab);
        tabGroup.addTab(dropsTab);
        tabGroup.addTab(searchTab);
        tabGroup.select(profileTab);

        JScrollPane scrollPane = new JScrollPane(display);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        add(tabGroup, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void startUp() {
        profileTabPanel.startUp();
        dropsTabPanel.startUp();
        searchTabPanel.startUp();
    }

    public void shutDown() {
        profileTabPanel.shutDown();
        dropsTabPanel.shutDown();
        searchTabPanel.shutDown();
    }

    /**
     * Called when the panel enters the valid (logged-in) state,
     * so account data is reloaded on every login and account switch.
     */
    public void onValidStateEntered() {
        profileTabPanel.refresh(true);
        dropsTabPanel.markStale();
    }

    /**
     * A plain JPanel inside a JScrollPane is laid out at its preferred width and
     * gets clipped when that exceeds the viewport (horizontal scrolling is disabled).
     * Tracking the viewport width keeps the tab content within the visible area.
     */
    private static class ScrollableDisplayPanel extends JPanel implements Scrollable {
        ScrollableDisplayPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return visibleRect.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // fill the viewport when the content is shorter, scroll when it is taller
            return getParent() instanceof JViewport
                    && getParent().getHeight() > getPreferredSize().height;
        }
    }
}
