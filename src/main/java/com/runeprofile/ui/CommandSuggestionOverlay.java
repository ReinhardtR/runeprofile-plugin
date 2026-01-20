package com.runeprofile.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.runeprofile.RuneProfileConfig;

import com.runeprofile.autosync.ManifestService;
import com.runeprofile.data.Manifest;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * OverlayPanel that suggests commands and possible aliases as the user types a command into the chat.
 */
@Slf4j
@Singleton
public class CommandSuggestionOverlay extends OverlayPanel {
    private static final int MAX_SUGGESTIONS = 5;

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private ManifestService manifestService;

    @Inject
    public CommandSuggestionOverlay() {
        this.setLayer(OverlayLayer.ABOVE_SCENE);
        this.setResizable(false);
    }

    public void startUp() {
        this.overlayManager.add(this);
    }

    public void shutDown() {
        this.overlayManager.remove(this);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!this.config.commandSuggestionOverlay()) {
            return null;
        }

        if (this.client.isMenuOpen()) {
            return null;
        }

        String currentChatInput = this.client.getVarcStrValue(VarClientID.CHATINPUT);

        List<String> suggestions = this.getSuggestions(currentChatInput);
        if (suggestions.isEmpty()) {
            return null;
        }

        String searchTermForHighlight = this.getSearchTermForHighlight(currentChatInput);

        // Reading the panel gets awkward when the text for a suggestion wraps to a new line
        // So we update the panel width to fit the longest suggestion
        this.updatePanelWidth(graphics, suggestions);

        for (int i = suggestions.size() - 1; i >= 0; i--) { // Reverse order to have the nearest suggestions on the bottom
            this.addSuggestionToOverlay(suggestions.get(i), searchTermForHighlight);
        }

        return super.render(graphics);
    }

    // Returns a list of suggestions based on the current chat input
    private List<String> getSuggestions(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }

        if (!this.config.enableLogCommand()) {
            return new ArrayList<>(); // No suggestions if !log command is disabled
        }

        String lowerInput = input.toLowerCase().trim();
        String command = "!log";

        // Only show suggestions after "!log " is typed
        if (lowerInput.startsWith(command.toLowerCase() + " ")) {
            // Show page name/alias suggestions
            return this.getPageSuggestions(input.substring(command.length()).trim());
        }

        return new ArrayList<>();
    }

    // Get page name and alias suggestions from manifest
    private List<String> getPageSuggestions(String searchTerm) {
        String lowerSearch = searchTerm.toLowerCase();

        Manifest manifest = manifestService.getManifest();
        if (manifest == null || manifest.getPages() == null) {
            return new ArrayList<>();
        }

        List<String> matchingPages = new ArrayList<>();

        // Search through all page names and their aliases
        for (Map.Entry<String, List<String>> entry : manifest.getPages().entrySet()) {
            String pageName = entry.getKey();
            List<String> aliases = entry.getValue();

            // Check page name
            if (pageName.toLowerCase().contains(lowerSearch)) {
                matchingPages.add(pageName);
            }

            // Check aliases
            if (aliases != null) {
                for (String alias : aliases) {
                    if (alias.toLowerCase().contains(lowerSearch) && !matchingPages.contains(alias)) {
                        matchingPages.add(alias);
                    }
                }
            }
        }

        this.sortByRelevance(matchingPages, lowerSearch);

        int limit = Math.min(matchingPages.size(), MAX_SUGGESTIONS);
        return matchingPages.subList(0, limit);
    }

    private String getSearchTermForHighlight(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String lowerInput = input.toLowerCase().trim();
        String command = "!log";

        if (lowerInput.startsWith(command.toLowerCase() + " ")) {
            return input.substring(command.length()).trim();
        }

        return input.trim();
    }

    private void updatePanelWidth(Graphics2D graphics, List<String> suggestions) {
        int maxWidth = 0;
        for (String suggestion : suggestions) {
            int width = graphics.getFontMetrics().stringWidth(suggestion);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        int panelWidth = maxWidth + 20;
        this.setPreferredSize(new Dimension(panelWidth, 0));
    }

    private void addSuggestionToOverlay(String command, String searchTerm) {
        String highlightedText = this.createHighlightedText(command, searchTerm);
        LineComponent lineComponent = LineComponent.builder().left(highlightedText).build();

        this.panelComponent.getChildren().add(lineComponent);
    }

    private String createHighlightedText(String text, String searchTerm) {
        if (searchTerm.isEmpty()) {
            return text;
        }

        String lowerText = text.toLowerCase();
        String lowerSearch = searchTerm.toLowerCase();
        int index = lowerText.indexOf(lowerSearch);

        if (index == -1) {
            return text;
        }

        StringBuilder result = new StringBuilder();
        result.append(text.substring(0, index));
        result.append("<col=00ff00>");  // Green highlight
        result.append(text.substring(index, index + searchTerm.length()));
        result.append("<col=ffffff>");  // Reset to white
        result.append(text.substring(index + searchTerm.length()));

        return result.toString();
    }

    // Sorts with exact matches first, then prefix matches, then by position and length
    private void sortByRelevance(List<String> items, String searchTerm) {
        items.sort((a, b) -> {
            String termA = a.toLowerCase();
            String termB = b.toLowerCase();

            boolean exactA = termA.equals(searchTerm);
            boolean exactB = termB.equals(searchTerm);
            if (exactA != exactB) {
                return exactA ? -1 : 1;
            }

            boolean prefixA = termA.startsWith(searchTerm);
            boolean prefixB = termB.startsWith(searchTerm);
            if (prefixA != prefixB) {
                return prefixA ? -1 : 1;
            }

            int posA = termA.indexOf(searchTerm);
            int posB = termB.indexOf(searchTerm);
            if (posA != posB) {
                return Integer.compare(posA, posB);
            }

            return Integer.compare(termA.length(), termB.length());
        });
    }
}