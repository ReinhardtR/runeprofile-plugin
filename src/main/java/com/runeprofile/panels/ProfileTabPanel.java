package com.runeprofile.panels;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.autosync.PlayerDataService;
import com.runeprofile.data.AccountInfo;
import com.runeprofile.events.ProfileCreated;
import com.runeprofile.events.ProfileDeleted;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProfileTabPanel extends JPanel {
    private static final String CARD_LOADING = "LOADING";
    private static final String CARD_INFO = "INFO";
    private static final String CARD_NO_PROFILE = "NO_PROFILE";
    private static final String CARD_ERROR = "ERROR";

    private final RuneProfilePlugin plugin;
    private final RuneProfileApiClient apiClient;
    private final PlayerDataService playerDataService;
    private final EventBus eventBus;

    private final CardLayout cardLayout = new CardLayout();

    private final JLabel usernameValue = createValueLabel();
    private final JLabel accountTypeValue = createValueLabel();
    private final JLabel clanValue = createValueLabel();
    private final JLabel createdValue = createValueLabel();
    private final JLabel updatedValue = createValueLabel();
    private JPanel clanRow;

    private final JTextArea errorText = Utils.createParagraph("");
    private JButton createProfileButton;

    private final AtomicBoolean fetchInFlight = new AtomicBoolean(false);
    private volatile boolean loadedOnce = false;

    @Inject
    public ProfileTabPanel(RuneProfilePlugin plugin, RuneProfileApiClient apiClient, PlayerDataService playerDataService, EventBus eventBus, MainButtonsPanel mainButtonsPanel) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.playerDataService = playerDataService;
        this.eventBus = eventBus;

        setLayout(cardLayout);

        add(buildLoadingCard(), CARD_LOADING);
        add(buildInfoCard(mainButtonsPanel), CARD_INFO);
        add(buildNoProfileCard(), CARD_NO_PROFILE);
        add(buildErrorCard(), CARD_ERROR);

        cardLayout.show(this, CARD_LOADING);
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onProfileCreated(ProfileCreated event) {
        refresh(true);
    }

    @Subscribe
    public void onProfileDeleted(ProfileDeleted event) {
        refresh(true);
    }

    public void refresh(boolean force) {
        if (!force && loadedOnce) return;
        if (!fetchInFlight.compareAndSet(false, true)) return;

        if (!loadedOnce) {
            SwingUtilities.invokeLater(() -> cardLayout.show(this, CARD_LOADING));
        }

        playerDataService.getAccountIdAsync().thenAccept((accountId) -> {
            if (accountId == null) {
                // not logged in yet - stay on the current card,
                // a refresh is triggered again when the login state is entered
                fetchInFlight.set(false);
                log.debug("Account id not available yet, skipping account info fetch");
                return;
            }

            apiClient.getAccountAsync(accountId)
                .whenComplete((account, ex) -> {
                    fetchInFlight.set(false);

                    SwingUtilities.invokeLater(() -> {
                        if (ex == null) {
                            loadedOnce = true;
                            populateInfo(account);
                            cardLayout.show(this, CARD_INFO);
                            return;
                        }

                        if (Utils.isAccountNotFound(ex)) {
                            loadedOnce = true;
                            createProfileButton.setEnabled(true);
                            cardLayout.show(this, CARD_NO_PROFILE);
                            return;
                        }

                        log.warn("Failed to load account info", ex);
                        Utils.setParagraphText(errorText, Utils.getApiErrorMessage(ex, "Failed to load your account info."));
                        cardLayout.show(this, CARD_ERROR);
                    });
                });
        });
    }

    private void populateInfo(AccountInfo account) {
        usernameValue.setText(account.getUsername());
        accountTypeValue.setText(account.getAccountType());

        boolean hasClan = account.getClanName() != null && !account.getClanName().isEmpty();
        clanValue.setText(hasClan ? account.getClanName() : "");
        clanRow.setVisible(hasClan);

        createdValue.setText(Utils.formatTimestamp(account.getCreatedAt()));
        updatedValue.setText(Utils.formatTimestamp(account.getUpdatedAt()));
    }

    private JPanel buildInfoCard(MainButtonsPanel mainButtonsPanel) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel infoContainer = new JPanel(new GridLayout(0, 1, 0, 6));
        infoContainer.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        infoContainer.setBorder(new EmptyBorder(8, 8, 8, 8));

        infoContainer.add(buildInfoRow("Username", usernameValue));
        infoContainer.add(buildInfoRow("Account Type", accountTypeValue));
        clanRow = buildInfoRow("Clan", clanValue);
        infoContainer.add(clanRow);
        infoContainer.add(buildInfoRow("Created", createdValue));
        infoContainer.add(buildInfoRow("Last Updated", updatedValue));

        infoContainer.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoContainer.setMaximumSize(new Dimension(Short.MAX_VALUE, infoContainer.getPreferredSize().height));
        card.add(infoContainer);

        card.add(Box.createVerticalStrut(10));

        mainButtonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainButtonsPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, mainButtonsPanel.getPreferredSize().height));
        card.add(mainButtonsPanel);

        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel buildNoProfileCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea guideText = Utils.createParagraph(
                "You don't have a RuneProfile yet.\n\n"
                        + "Create one to start tracking your progress. "
                        + "After creating it, open your collection log in-game to sync your collection log data, "
                        + "and update your player model from the Profile tab."
        );
        guideText.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(guideText);

        card.add(Box.createVerticalStrut(10));

        createProfileButton = new JButton("Create Profile");
        createProfileButton.setPreferredSize(new Dimension(createProfileButton.getPreferredSize().width, 30));
        createProfileButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        createProfileButton.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        createProfileButton.addActionListener((e) -> createProfile());
        card.add(createProfileButton);

        card.add(Box.createVerticalGlue());
        return card;
    }

    private void createProfile() {
        createProfileButton.setEnabled(false);
        try {
            plugin.updateProfileAsync(false, "manual-create")
                    .whenComplete((result, ex) -> SwingUtilities.invokeLater(() -> {
                        createProfileButton.setEnabled(true);
                        if (ex == null) {
                            // usually already triggered by the ProfileCreated event
                            refresh(true);
                        }
                    }));
        } catch (IllegalStateException ex) {
            log.warn("Could not create profile", ex);
            createProfileButton.setEnabled(true);
        }
    }

    private JPanel buildLoadingCard() {
        return buildMessageCard(createMessageLabel("Loading..."));
    }

    private JPanel buildErrorCard() {
        errorText.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
        errorText.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel card = buildMessageCard(errorText);

        JButton retryButton = new JButton("Retry");
        retryButton.setPreferredSize(new Dimension(retryButton.getPreferredSize().width, 30));
        retryButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        retryButton.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
        retryButton.addActionListener((e) -> refresh(true));
        card.add(Box.createVerticalStrut(10), 1);
        card.add(retryButton, 2);

        return card;
    }

    private JPanel buildMessageCard(JComponent content) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(10, 10, 10, 10));
        card.add(content);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private JPanel buildInfoRow(String labelText, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.GRAY);

        row.add(label, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private static JLabel createValueLabel() {
        JLabel label = new JLabel();
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.WHITE);
        return label;
    }

    private static JLabel createMessageLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FontManager.getRunescapeSmallFont());
        label.setForeground(Color.GRAY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

}
