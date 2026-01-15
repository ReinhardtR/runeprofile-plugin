package com.runeprofile.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runeprofile.RuneProfileConfig;

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
public class CommandSuggestionOverlay extends OverlayPanel {
    private static final int MAX_SUGGESTIONS = 10; // Controls the number of suggestions shown, could use a config option if we want

    @Inject
    private Gson gson;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private OverlayManager overlayManager;

    private Map<String, List<String>> commandAliases;

    @Inject
    public CommandSuggestionOverlay() {
        this.setLayer(OverlayLayer.ABOVE_SCENE);
        this.setResizable(false);
    }

    public void startUp() {
        this.commandAliases = this.loadCommandAliases();
        this.overlayManager.add(this);
    }

    public void shutDown() {
        this.overlayManager.remove(this);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!this.config.showSuggestionOverlay()) {
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

        for (int i = suggestions.size() - 1; i >= 0; i--) { // Reverse order to have nearest suggestions on the bottom
            this.addSuggestionToOverlay(suggestions.get(i), searchTermForHighlight);
        }

        return super.render(graphics);
    }

    // Loads the command aliases from the JSON file in the resources
    private Map<String, List<String>> loadCommandAliases() {
        try (InputStream inputStream = this.getClass().getResourceAsStream("/command-aliases.json")) {
            if (inputStream == null) {
                return Collections.emptyMap();
            }

            InputStreamReader reader = new InputStreamReader(inputStream);
            JsonObject jsonObject = this.gson.fromJson(reader, JsonObject.class);

            Map<String, List<String>> result = new HashMap<>();

            for (String key : jsonObject.keySet()) {
                JsonArray array = jsonObject.getAsJsonArray(key);
                List<String> aliases = new ArrayList<>();

                for (JsonElement element : array) {
                    aliases.add(element.getAsString());
                }

                result.put(key, aliases);
            }

            return result;
        } catch (Exception e) {
            log.error("Failed to load command aliases", e);
            return Collections.emptyMap();
        }
    }

    // Returns a list of suggestions based on the current chat input
    private List<String> getSuggestions(String input) {
        if (input == null || input.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String lowerInput = input.toLowerCase().trim();

        // Check if full command + space is typed in 
        String matchedCommand = null;
        for (String command : this.commandAliases.keySet()) {
            if (lowerInput.startsWith(command.toLowerCase() + " ")) {
                matchedCommand = command;
                break;
            }
        }

        if (matchedCommand != null && matchedCommand.equals("!log") && !this.config.enableLogCommand()) {
            return new ArrayList<>(); // No suggestions if !log command is disabled
        }

        // If a full command + space is typed in, show aliases specific to that command
        if (matchedCommand != null) {
            return this.getAliasSuggestions(matchedCommand, input.substring(matchedCommand.length()).trim());
        }

        // Otherwise, show just the command suggestions
        return this.getCommandSuggestions(lowerInput);
    }

    // Command = !log, !kc, etc.
    private List<String> getCommandSuggestions(String searchTerm) {
        List<String> matchingCommands = new ArrayList<>();

        for (String command : this.commandAliases.keySet()) {

            if (command.equals("!log") && !this.config.enableLogCommand()) {
                continue; // Skip !log command if disabled
            }

            boolean commandMatches = command.toLowerCase().startsWith(searchTerm);
            if (commandMatches) {
                matchingCommands.add(command);
            }
        }

        this.sortByRelevance(matchingCommands, searchTerm);

        int limit = Math.min(matchingCommands.size(), MAX_SUGGESTIONS);
        return matchingCommands.subList(0, limit);
    }

    // Alias = cerb, vork, etc.
    private List<String> getAliasSuggestions(String command, String searchTerm) {
        String lowerSearch = searchTerm.toLowerCase();

        List<String> aliases = this.commandAliases.get(command);

        if (aliases == null || aliases.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> matchingAliases = new ArrayList<>();

        for (String alias : aliases) {
            boolean aliasMatches = alias.toLowerCase().contains(lowerSearch);
            if (aliasMatches) {
                matchingAliases.add(alias);
            }
        }

        this.sortByRelevance(matchingAliases, lowerSearch);

        int limit = Math.min(matchingAliases.size(), MAX_SUGGESTIONS);
        List<String> results = new ArrayList<>();

        for (int i = 0; i < limit; i++) {
            String alias = matchingAliases.get(i);
            results.add(alias);
        }

        return results;
    }

    private String getSearchTermForHighlight(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "";
        }

        String lowerInput = input.toLowerCase().trim();

        for (String command : this.commandAliases.keySet()) {
            if (lowerInput.startsWith(command.toLowerCase() + " ")) {
                return input.substring(command.length()).trim();
            }
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