package com.runeprofile.panels;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.data.DefaultClogPageData;
import com.runeprofile.utils.AccountHash;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.StructComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class DefaultClogPagePanel extends JPanel {
    private final RuneProfileApiClient runeProfileApiClient;
    private final ClientThread clientThread;
    private final Client client;

    private final String DEFAULT_CLOG_PAGE_KEY = "default_clog_page";

    private static final int COLLECTION_LOG_GROUPS = 2102;
    private static final int COLLECTION_LOG_TAB_ENUM = 683;
    private static final int COLLECTION_LOG_PAGE_NAME = 689;

    @Inject
    public DefaultClogPagePanel(ClientThread clientThread, Client client, ConfigManager configManager, RuneProfileApiClient runeProfileApiClient) {
        this.runeProfileApiClient = runeProfileApiClient;
        this.clientThread = clientThread;
        this.client = client;

        clientThread.invokeLater(() -> {
            List<String> clogPageNames = new ArrayList<>();

            int[] tabIds = client.getEnum(COLLECTION_LOG_GROUPS).getIntVals();
            for (int tabId : tabIds) {
                StructComposition tabStruct = client.getStructComposition(tabId);
                int[] pageIds = client.getEnum(tabStruct.getIntValue(COLLECTION_LOG_TAB_ENUM)).getIntVals();
                for (int pageId : pageIds) {
                    StructComposition pageStruct = client.getStructComposition(pageId);
                    String pageName = pageStruct.getStringValue(COLLECTION_LOG_PAGE_NAME);
                    clogPageNames.add(pageName);
                }
            }

            SwingUtilities.invokeLater(() -> {
                JLabel title = new JLabel("Default Collection Log Page");
                title.setAlignmentX(Component.LEFT_ALIGNMENT);


                JComboBox<String> selectMenu = new JComboBox<>(clogPageNames.toArray(new String[0]));
                selectMenu.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
                selectMenu.setMinimumSize(new Dimension(0, 30));
                selectMenu.setAlignmentX(Component.LEFT_ALIGNMENT);
                selectMenu.setAlignmentY(Component.TOP_ALIGNMENT);
                selectMenu.setToolTipText("Select the collection log page that will be displayed by default on your profile when opened.");

                String storedPage = configManager.getRSProfileConfiguration(RuneProfilePlugin.CONFIG_GROUP, DEFAULT_CLOG_PAGE_KEY);
                int storedPageIndex = clogPageNames.indexOf(storedPage);
                selectMenu.setSelectedIndex(storedPageIndex == -1 ? 0 : storedPageIndex);

                selectMenu.addActionListener(e -> {
                    String selectedPage = (String) selectMenu.getSelectedItem();
                    if (selectedPage != null) {
                        configManager.setRSProfileConfiguration(RuneProfilePlugin.CONFIG_GROUP, DEFAULT_CLOG_PAGE_KEY, selectedPage);
                        handlePageSelection(selectedPage);
                    }
                });

                setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
                add(title);
                add(Box.createVerticalStrut(5));
                add(selectMenu);
            });
        });
    }

    private void handlePageSelection(String selectedPage) {
        CompletableFuture<DefaultClogPageData> dataFuture = new CompletableFuture<>();

        clientThread.invokeLater(() -> {
            dataFuture.complete(
                    new DefaultClogPageData(
                            AccountHash.getHashed(client),
                            selectedPage
                    )
            );
        });

        dataFuture.thenCompose(runeProfileApiClient::setDefaultClogPage);
    }
}
