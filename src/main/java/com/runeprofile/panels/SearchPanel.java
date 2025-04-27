package com.runeprofile.panels;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.ProfileSearchResult;
import com.runeprofile.utils.RuneProfileApiException;
import com.runeprofile.utils.UsernameAutocompleter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class SearchPanel extends JPanel {
    private final RuneProfilePlugin plugin;

    private final JPanel resultsPanel;
    private final IconTextField searchField;

    @Inject
    public SearchPanel(RuneProfilePlugin plugin, RuneProfileApiClient runeProfileApiClient, UsernameAutocompleter usernameAutocompleter) {
        this.plugin = plugin;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Search profiles");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(title);

        searchField = new IconTextField();
        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchField.setMinimumSize(new Dimension(0, 30));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setAlignmentY(Component.TOP_ALIGNMENT);

        add(Box.createVerticalStrut(5));
        add(searchField);
        add(Box.createVerticalStrut(5));

        resultsPanel = new JPanel();
        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setAlignmentY(Component.TOP_ALIGNMENT);
        resultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField.addKeyListener(usernameAutocompleter);
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                String query = Text.sanitize(searchField.getText());
                if (query.isEmpty()) resultsPanel.removeAll();
            }
        });
        searchField.addClearListener(resultsPanel::removeAll);
        searchField.addActionListener(
                (e) -> {
                    String query = Text.sanitize(searchField.getText());
                    if (query.isEmpty()) resultsPanel.removeAll();

                    if (!query.isEmpty()) {
                        runeProfileApiClient.searchProfiles(query).whenComplete((results, ex) -> {
                            if (ex != null) {
                                log.error("Error searching profiles: ", ex);

                                if (ex instanceof RuneProfileApiException) {
                                    updateErrors("Error: " + ex.getMessage());
                                } else {
                                    updateErrors("An unexpected error occurred.");
                                }

                                return;
                            }
                            updateResults(Arrays.asList(results));
                        });
                    }
                }
        );

        add(resultsPanel);
    }

    private void updateErrors(String errorMessage) {
        resultsPanel.removeAll();
        JLabel errorLabel = new JLabel(errorMessage);
        errorLabel.setForeground(Color.RED);
        resultsPanel.add(errorLabel);
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void updateResults(List<ProfileSearchResult> results) {
        resultsPanel.removeAll();

        if (results.isEmpty()) {
            JLabel noResults = new JLabel("No results found.");
            noResults.setForeground(Color.LIGHT_GRAY);
            resultsPanel.add(noResults);
        } else {
            for (int i = 0; i < results.size(); i++) {
                JButton resultButton = getResultButton(results, i);
                resultsPanel.add(resultButton);
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private JButton getResultButton(List<ProfileSearchResult> results, int i) {
        ProfileSearchResult result = results.get(i);

        JButton resultButton = new JButton(result.getUsername());
        int buttonHeight = 30;
        resultButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, buttonHeight));
        resultButton.setPreferredSize(new Dimension(resultsPanel.getWidth(), buttonHeight));
        resultButton.setMinimumSize(new Dimension(0, buttonHeight));
        resultButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resultButton.setToolTipText("Open in browser");

        resultButton.addActionListener((e) -> plugin.openProfileInBrowser(result.getUsername()));
        return resultButton;
    }
}
