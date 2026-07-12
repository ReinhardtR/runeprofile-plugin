package com.runeprofile.panels;

import com.runeprofile.RuneProfileApiClient;
import com.runeprofile.autosync.PlayerDataService;
import com.runeprofile.data.activities.ActivityRecord;
import com.runeprofile.events.ProfileCreated;
import com.runeprofile.events.ProfileDeleted;
import com.runeprofile.utils.Utils;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.SwingUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DropsTabPanel extends JPanel {
    private static final int DROPS_LIMIT = 30;

    private final RuneProfileApiClient apiClient;
    private final PlayerDataService playerDataService;
    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final EventBus eventBus;

    private final JTextArea statusText = Utils.createParagraph("");
    private final JPanel listPanel = new JPanel();

    private final AtomicBoolean fetchInFlight = new AtomicBoolean(false);
    private volatile boolean stale = true;
    private volatile String accountId;

    private final List<ActivityRecord> records = new ArrayList<>();
    private final Map<Integer, String> itemNames = new HashMap<>();

    @Inject
    public DropsTabPanel(RuneProfileApiClient apiClient, PlayerDataService playerDataService, ItemManager itemManager, ClientThread clientThread, EventBus eventBus) {
        this.apiClient = apiClient;
        this.playerDataService = playerDataService;
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.eventBus = eventBus;

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        JTextArea description = Utils.createParagraph(
                "Recent valuable drops on your profile. "
                        + "Use this to remove duplicate or wrongly tracked drops. "
                        + "Deleting a drop is permanent and cannot be undone."
        );
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(description);

        add(Box.createVerticalStrut(10));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);

        JLabel headerLabel = new JLabel("Recent Drops");
        headerLabel.setFont(FontManager.getRunescapeBoldFont());
        headerLabel.setForeground(Color.WHITE);
        headerRow.add(headerLabel, BorderLayout.WEST);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.setFont(FontManager.getRunescapeSmallFont());
        refreshButton.addActionListener((e) -> refresh(true));
        headerRow.add(refreshButton, BorderLayout.EAST);

        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerRow.setMaximumSize(new Dimension(Short.MAX_VALUE, headerRow.getPreferredSize().height));
        add(headerRow);

        add(Box.createVerticalStrut(8));

        statusText.setForeground(Color.GRAY);
        statusText.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(statusText);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setOpaque(false);
        listPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        add(listPanel);

        add(Box.createVerticalGlue());
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onProfileCreated(ProfileCreated event) {
        markStale();
    }

    @Subscribe
    public void onProfileDeleted(ProfileDeleted event) {
        markStale();
    }

    public void markStale() {
        stale = true;
    }

    public void refresh(boolean force) {
        if (!force && !stale) return;
        fetchDrops();
    }

    private void fetchDrops() {
        if (!fetchInFlight.compareAndSet(false, true)) return;

        SwingUtilities.invokeLater(() -> {
            listPanel.removeAll();
            setStatus("Loading...", Color.GRAY);
            revalidate();
            repaint();
        });

        playerDataService.getAccountIdAsync().thenAccept((id) -> {
            if (id == null) {
                // not logged in yet - a refresh is triggered again on the next tab open
                fetchInFlight.set(false);
                log.debug("Account id not available yet, skipping drops fetch");
                SwingUtilities.invokeLater(() -> setStatus(null, Color.GRAY));
                return;
            }

            this.accountId = id;
            apiClient.getValuableDropsAsync(id, DROPS_LIMIT)
                .whenComplete((result, ex) -> {
                    fetchInFlight.set(false);

                    if (ex != null) {
                        log.warn("Failed to load valuable drops", ex);
                        SwingUtilities.invokeLater(() -> {
                            if (Utils.isAccountNotFound(ex)) {
                                setStatus("Create your profile first on the Profile tab.", Color.GRAY);
                            } else {
                                setStatus(Utils.getApiErrorMessage(ex, "Failed to load drops."), ColorScheme.PROGRESS_ERROR_COLOR);
                            }
                        });
                        return;
                    }

                    stale = false;
                    resolveItemNames(result);
                });
        });
    }

    /**
     * Item names are resolved on the client thread, then the list is rebuilt on the EDT.
     */
    private void resolveItemNames(List<ActivityRecord> fetchedRecords) {
        clientThread.invokeLater(() -> {
            Map<Integer, String> names = new HashMap<>();
            for (ActivityRecord record : fetchedRecords) {
                int itemId = record.getData().getItemId();
                if (!names.containsKey(itemId)) {
                    names.put(itemId, itemManager.getItemComposition(itemId).getName());
                }
            }

            SwingUtilities.invokeLater(() -> {
                records.clear();
                records.addAll(fetchedRecords);
                itemNames.clear();
                itemNames.putAll(names);
                rebuildList();
            });
        });
    }

    private void rebuildList() {
        listPanel.removeAll();

        if (records.isEmpty()) {
            setStatus("No valuable drops tracked yet.", Color.GRAY);
        } else {
            setStatus(null, Color.GRAY);
            for (ActivityRecord record : records) {
                listPanel.add(buildDropRow(record));
                listPanel.add(Box.createVerticalStrut(6));
            }
        }

        revalidate();
        repaint();
    }

    private JPanel buildDropRow(ActivityRecord record) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        row.setBorder(new EmptyBorder(4, 6, 4, 6));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        int itemId = record.getData().getItemId();

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(36, 32));
        AsyncBufferedImage itemImage = itemManager.getImage(itemId);
        itemImage.addTo(iconLabel);
        row.add(iconLabel, BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.setBorder(new EmptyBorder(0, 6, 0, 0));

        JLabel nameLabel = new JLabel(itemNames.getOrDefault(itemId, "Item " + itemId));
        nameLabel.setFont(FontManager.getRunescapeSmallFont());
        nameLabel.setForeground(Color.WHITE);
        textPanel.add(nameLabel);

        String value = QuantityFormatter.quantityToRSDecimalStack(record.getData().getValue(), true) + " gp";
        JLabel detailsLabel = new JLabel(value + " · " + Utils.formatTimestamp(record.getCreatedAt()));
        detailsLabel.setFont(FontManager.getRunescapeSmallFont());
        detailsLabel.setForeground(Color.GRAY);
        textPanel.add(detailsLabel);

        row.add(textPanel, BorderLayout.CENTER);

        JButton deleteButton = buildDeleteButton(record);
        row.add(deleteButton, BorderLayout.EAST);

        row.setMaximumSize(new Dimension(Short.MAX_VALUE, Math.max(row.getPreferredSize().height, 40)));
        return row;
    }

    private static final ImageIcon DELETE_ICON = createXIcon(Color.GRAY);
    private static final ImageIcon DELETE_ICON_HOVER = createXIcon(ColorScheme.PROGRESS_ERROR_COLOR);

    private JButton buildDeleteButton(ActivityRecord record) {
        JButton deleteButton = new JButton(DELETE_ICON);
        SwingUtil.removeButtonDecorations(deleteButton);
        deleteButton.setUI(new BasicButtonUI());
        deleteButton.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        deleteButton.setPreferredSize(new Dimension(24, 24));
        deleteButton.setToolTipText("Delete this drop");
        deleteButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                deleteButton.setIcon(DELETE_ICON_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                deleteButton.setIcon(DELETE_ICON);
            }
        });
        deleteButton.addActionListener((e) -> confirmAndDelete(record, deleteButton));
        return deleteButton;
    }

    /**
     * The RuneScape fonts have no glyph for an "✕" character, so the icon is drawn instead.
     */
    private static ImageIcon createXIcon(Color color) {
        int size = 16;
        int inset = 4;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine(inset, inset, size - inset - 1, size - inset - 1);
        g.drawLine(size - inset - 1, inset, inset, size - inset - 1);
        g.dispose();
        return new ImageIcon(image);
    }

    private void confirmAndDelete(ActivityRecord record, JButton deleteButton) {
        int itemId = record.getData().getItemId();
        String itemName = itemNames.getOrDefault(itemId, "this drop");

        final int confirmed = JOptionPane.showConfirmDialog(
                this,
                "Delete " + itemName + " from your profile?\nThis cannot be undone.",
                "Confirm Drop Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirmed != JOptionPane.YES_OPTION) return;

        deleteButton.setEnabled(false);

        apiClient.deleteActivityAsync(accountId, record.getId())
                .whenComplete((result, ex) -> SwingUtilities.invokeLater(() -> {
                    if (ex != null) {
                        log.warn("Failed to delete activity", ex);
                        deleteButton.setEnabled(true);
                        JOptionPane.showMessageDialog(
                                this,
                                Utils.getApiErrorMessage(ex, "Failed to delete the drop."),
                                "RuneProfile",
                                JOptionPane.ERROR_MESSAGE
                        );
                        return;
                    }

                    records.removeIf((r) -> r.getId().equals(record.getId()));
                    rebuildList();
                }));
    }

    private void setStatus(String text, Color color) {
        statusText.setForeground(color);
        Utils.setParagraphText(statusText, text == null ? "" : text);
        statusText.setVisible(text != null);
    }
}
