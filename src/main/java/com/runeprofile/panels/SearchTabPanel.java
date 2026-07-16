package com.runeprofile.panels;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.data.ProfileSearchResult;
import com.runeprofile.utils.AccountTypeIcon;
import com.runeprofile.utils.UsernameAutocompleter;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class SearchTabPanel extends JPanel {
    private final RuneProfileApiClient apiClient;
    private final UsernameAutocompleter usernameAutocompleter;
    private final AccountTypeIcon accountTypeIcon;

    private final IconTextField searchField = new IconTextField();
    private final JPanel resultsPanel = new JPanel();
    private final JTextArea statusText = Utils.createParagraph("");

    /**
     * Increments per search so responses that arrive after a newer query (or a
     * cleared field) can be dropped instead of overwriting the current results.
     */
    private final AtomicInteger requestSeq = new AtomicInteger();

    @Inject
    public SearchTabPanel(RuneProfileApiClient apiClient, UsernameAutocompleter usernameAutocompleter, AccountTypeIcon accountTypeIcon) {
        this.apiClient = apiClient;
        this.usernameAutocompleter = usernameAutocompleter;
        this.accountTypeIcon = accountTypeIcon;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea description = Utils.createParagraph(
                "Search RuneProfile for a player and open their profile in your browser. "
                        + "Start typing to autocomplete names from your friends, clan and nearby players."
        );
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(description);

        add(Box.createVerticalStrut(10));

        searchField.setIcon(IconTextField.Icon.SEARCH);
        searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchField.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchField.setMinimumSize(new Dimension(0, 30));
        searchField.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);

        searchField.addKeyListener(usernameAutocompleter);
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (Text.sanitize(searchField.getText()).isEmpty()) {
                    clearResults();
                }
            }
        });
        searchField.addClearListener(this::clearResults);
        searchField.addActionListener((e) -> runSearch());

        add(searchField);
        add(Box.createVerticalStrut(10));

        statusText.setForeground(Color.GRAY);
        statusText.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusText.setVisible(false);
        add(statusText);

        resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
        resultsPanel.setOpaque(false);
        resultsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(resultsPanel);

        add(Box.createVerticalGlue());
    }

    public void startUp() {
        // No account-scoped state to manage; kept for symmetry with the other tabs.
    }

    public void shutDown() {
        // No account-scoped state to manage; kept for symmetry with the other tabs.
    }

    public void refresh(boolean force) {
        // Focus the field when the tab is opened so the user can type right away.
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    }

    private void runSearch() {
        String query = Text.sanitize(searchField.getText());
        if (query.isEmpty()) {
            clearResults();
            return;
        }

        final int seq = requestSeq.incrementAndGet();
        setStatus("Searching...", Color.GRAY);
        resultsPanel.removeAll();
        revalidate();
        repaint();

        apiClient.searchProfiles(query).whenComplete((results, ex) -> SwingUtilities.invokeLater(() -> {
            // Ignore responses that have been superseded by a newer search.
            if (seq != requestSeq.get()) {
                return;
            }

            if (ex != null) {
                log.warn("Failed to search profiles", ex);
                setStatus(Utils.getApiErrorMessage(ex, "Search failed. Please try again."), ColorScheme.PROGRESS_ERROR_COLOR);
                resultsPanel.removeAll();
                revalidate();
                repaint();
                return;
            }

            usernameAutocompleter.addToSearchHistory(query);
            updateResults(results == null ? List.of() : List.of(results));
        }));
    }

    private void updateResults(List<ProfileSearchResult> results) {
        resultsPanel.removeAll();

        if (results.isEmpty()) {
            setStatus("No profiles found.", Color.GRAY);
        } else {
            setStatus(null, Color.GRAY);
            for (ProfileSearchResult result : results) {
                resultsPanel.add(buildResultRow(result));
                resultsPanel.add(Box.createVerticalStrut(6));
            }
        }

        revalidate();
        repaint();
    }

    private JPanel buildResultRow(ProfileSearchResult result) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(6, 8, 6, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.setToolTipText("Open " + result.getUsername() + " in browser");

        ImageIcon typeIcon = accountTypeIcon.getByKey(accountTypeKey(result));
        if (typeIcon != null) {
            JLabel iconLabel = new JLabel(typeIcon);
            iconLabel.setVerticalAlignment(SwingConstants.CENTER);
            row.add(iconLabel, BorderLayout.WEST);
        }

        JLabel nameLabel = new JLabel(result.getUsername());
        nameLabel.setFont(FontManager.getRunescapeBoldFont());
        nameLabel.setForeground(Color.WHITE);
        row.add(nameLabel, BorderLayout.CENTER);

        row.setMaximumSize(new Dimension(Short.MAX_VALUE, Math.max(row.getPreferredSize().height, 34)));

        row.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                row.setBackground(ColorScheme.DARK_GRAY_HOVER_COLOR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                Utils.openProfileInBrowser(result.getUsername());
            }
        });

        return row;
    }

    private String accountTypeKey(ProfileSearchResult result) {
        ProfileSearchResult.AccountType accountType = result.getAccountType();
        return accountType != null ? accountType.getKey() : null;
    }

    private void clearResults() {
        // Bump the sequence so any in-flight response for the cleared query is dropped.
        requestSeq.incrementAndGet();
        resultsPanel.removeAll();
        setStatus(null, Color.GRAY);
        revalidate();
        repaint();
    }

    private void setStatus(String text, Color color) {
        statusText.setForeground(color);
        Utils.setParagraphText(statusText, text == null ? "" : text);
        statusText.setVisible(text != null);
    }
}
