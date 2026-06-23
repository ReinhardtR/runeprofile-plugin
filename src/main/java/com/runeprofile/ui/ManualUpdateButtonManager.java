package com.runeprofile.ui;

import com.google.inject.Inject;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.RuneProfilePlugin;
import com.runeprofile.autosync.CollectionLogWidgetSubscriber;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Singleton;

@Slf4j
@Singleton
public class ManualUpdateButtonManager {
    private static final int SEARCH_ICON_CHILD = 9;
    private static final int TEXT_CHILD = 10;
    private static final int TOGGLE_SEARCH_SCRIPT = 4084;
    private static final int COLLECTION_INIT_SCRIPT = 2240;
    private static final int COLLECTION_LOG_SETUP = 7797;
    private static final int ORIGINAL_BUTTON_WIDTH = 71;
    private static final int SYNC_BUTTON_WIDTH = 80;
    private static final int CORNER_SIZE = 9;
    private static final String SYNC_ACTION = "Sync (Right-click Search)";
    private static final String BUTTON_TEXT = "RuneProfile";
    private static final int FONT_COLOR_INACTIVE = 0xd6d6d6;
    private static final int FONT_COLOR_ACTIVE = 0xffffff;
    private static final int RATE_LIMIT_TICKS = 50;

    private static final int[] SPRITE_IDS_INACTIVE = {
            SpriteID.TRADEBACKING,
            SpriteID.V2StoneButtonOut.A_TOP_LEFT,
            SpriteID.V2StoneButtonOut.A_TOP_RIGHT,
            SpriteID.V2StoneButtonOut.A_BOTTOM_LEFT,
            SpriteID.V2StoneButtonOut.A_BOTTOM_RIGHT,
            SpriteID.V2StoneButtonOut.A_MAP_EDGE_LEFT,
            SpriteID.V2StoneButtonOut.A_MAP_EDGE_TOP,
            SpriteID.V2StoneButtonOut.A_MAP_EDGE_RIGHT,
            SpriteID.V2StoneButtonOut.A_MAP_EDGE_BOTTOM,
    };

    private static final int[] SPRITE_IDS_ACTIVE = {
            SpriteID.TRADEBACKING_DARK,
            SpriteID.V2StoneButtonIn.A_TOP_LEFT,
            SpriteID.V2StoneButtonIn.A_TOP_RIGHT,
            SpriteID.V2StoneButtonIn.A_BOTTOM_LEFT,
            SpriteID.V2StoneButtonIn.A_BOTTOM_RIGHT,
            SpriteID.V2StoneButtonIn.A_LEFT,
            SpriteID.V2StoneButtonIn.A_TOP,
            SpriteID.V2StoneButtonIn.A_RIGHT,
            SpriteID.V2StoneButtonIn.A_BOTTOM,
    };

    private final Client client;
    private final EventBus eventBus;
    private final ClientThread clientThread;
    private final CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;
    private final RuneProfileConfig config;

    private int lastAttemptedUpdate = -1;
    private boolean isUndoingSearchToggle = false;
    private boolean isSearchAction = false;
    private boolean isSyncAction = false;

    @Inject
    private ManualUpdateButtonManager(
            Client client,
            EventBus eventBus,
            ClientThread clientThread,
            CollectionLogWidgetSubscriber collectionLogWidgetSubscriber,
            RuneProfileConfig config
    ) {
        this.client = client;
        this.eventBus = eventBus;
        this.clientThread = clientThread;
        this.collectionLogWidgetSubscriber = collectionLogWidgetSubscriber;
        this.config = config;
    }

    public void startUp() {
        eventBus.register(this);
        clientThread.invokeLater(this::setupSyncButton);
    }

    public void shutDown() {
        eventBus.unregister(this);
        clientThread.invokeLater(this::resetButton);
    }

    private void resetButton() {
        Widget searchButton = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
        if (searchButton == null) {
            return;
        }

        // Re-run the collection log setup script to restore original button state
        client.runScript(COLLECTION_LOG_SETUP);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(RuneProfilePlugin.CONFIG_GROUP)) {
            return;
        }
        if (!"show_clog_sync_button".equals(event.getKey())) {
            return;
        }

        clientThread.invokeLater(() -> {
            if (config.showClogSyncButton()) {
                setupSyncButton();
            } else {
                resetButton();
            }
        });
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (event.getGroupId() != InterfaceID.COLLECTION) {
            return;
        }

        // Reset flags
        isUndoingSearchToggle = false;
        isSearchAction = false;
        isSyncAction = false;

        // Wait 2 ticks for widgets to fully populate
        clientThread.invokeLater(() -> clientThread.invokeLater(this::setupSyncButton));
    }

    private void setupSyncButton() {
        if (!config.showClogSyncButton()) {
            return;
        }

        if (isSearchOpen() || isOpenedFromAdventureLog()) {
            return;
        }

        Widget searchButton = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
        if (searchButton == null) {
            return;
        }

        // Resize the border/background sprite children (0-8) to match the new width.
        // These follow a 9-sprite pattern: background, 4 corners, 4 edges.
        Widget[] children = searchButton.getChildren();
        if (children != null) {
            for (int i = 0; i < children.length && i < SEARCH_ICON_CHILD; i++) {
                Widget child = children[i];
                if (child == null) {
                    continue;
                }
                int w = child.getOriginalWidth();
                int x = child.getOriginalX();

                // Full-width children (background)
                if (w == ORIGINAL_BUTTON_WIDTH) {
                    child.setOriginalWidth(SYNC_BUTTON_WIDTH);
                }
                // Top/bottom edge sprites (width = buttonWidth - 2*cornerSize)
                else if (w == ORIGINAL_BUTTON_WIDTH - 2 * CORNER_SIZE) {
                    child.setOriginalWidth(SYNC_BUTTON_WIDTH - 2 * CORNER_SIZE);
                }

                // Right-side corners/edges anchored at oldWidth - cornerSize
                if (x == ORIGINAL_BUTTON_WIDTH - CORNER_SIZE) {
                    child.setOriginalX(SYNC_BUTTON_WIDTH - CORNER_SIZE);
                }

                child.revalidate();
            }
        }

        Widget textChild = searchButton.getChild(TEXT_CHILD);
        if (textChild != null) {
            textChild.setText(BUTTON_TEXT);
            textChild.setTextColor(FONT_COLOR_INACTIVE);
            textChild.setOriginalX(0);
            textChild.setWidthMode(0);
            textChild.setOriginalWidth(SYNC_BUTTON_WIDTH);
            textChild.setXTextAlignment(1);
            textChild.revalidate();
        }

        Widget iconChild = searchButton.getChild(SEARCH_ICON_CHILD);
        if (iconChild != null) {
            iconChild.setHidden(true);
        }

        searchButton.setOriginalWidth(SYNC_BUTTON_WIDTH);
        searchButton.setOnMouseOverListener((JavaScriptCallback) ev -> {
            Widget[] sprites = searchButton.getChildren();
            if (sprites != null) {
                for (int i = 0; i < SPRITE_IDS_ACTIVE.length && i < sprites.length; i++) {
                    if (sprites[i] != null) {
                        sprites[i].setSpriteId(SPRITE_IDS_ACTIVE[i]);
                    }
                }
            }
            if (textChild != null) {
                textChild.setTextColor(FONT_COLOR_ACTIVE);
            }
        });
        searchButton.setOnMouseLeaveListener((JavaScriptCallback) ev -> {
            Widget[] sprites = searchButton.getChildren();
            if (sprites != null) {
                for (int i = 0; i < SPRITE_IDS_INACTIVE.length && i < sprites.length; i++) {
                    if (sprites[i] != null) {
                        sprites[i].setSpriteId(SPRITE_IDS_INACTIVE[i]);
                    }
                }
            }
            if (textChild != null) {
                textChild.setTextColor(FONT_COLOR_INACTIVE);
            }
        });
        searchButton.revalidate();
        searchButton.setAction(0, SYNC_ACTION);
    }

    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (!config.showClogSyncButton()) {
            return;
        }

        if (event.getActionParam1() != InterfaceID.Collection.SEARCH_TOGGLE) {
            return;
        }

        if (isOpenedFromAdventureLog() || isSearchOpen()) {
            return;
        }

        client.getMenu().createMenuEntry(-1)
                .setOption("Search")
                .setTarget(event.getTarget())
                .setType(MenuAction.RUNELITE)
                .onClick(this::onSearchClicked);
    }

    private void onSearchClicked(MenuEntry entry) {
        Widget searchButton = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
        if (searchButton == null) {
            return;
        }

        Object[] onOpListener = searchButton.getOnOpListener();
        if (onOpListener == null) {
            return;
        }

        isSearchAction = true;
        client.createScriptEventBuilder(onOpListener)
                .setSource(searchButton)
                .setOp(1)
                .build()
                .run();
    }

    /**
     * Detect when user clicks our "Sync" action.
     * Other plugins calling menuAction("Search") won't fire this event.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (!SYNC_ACTION.equals(event.getMenuOption())) {
            return;
        }
        if (event.getParam1() != InterfaceID.Collection.SEARCH_TOGGLE) {
            return;
        }
        if (isOpenedFromAdventureLog() || isSearchOpen()) {
            return;
        }

        isSyncAction = true;

        if (lastAttemptedUpdate != -1 && lastAttemptedUpdate + RATE_LIMIT_TICKS > client.getTickCount()) {
            int secondsLeft = (int) Math.round((lastAttemptedUpdate + RATE_LIMIT_TICKS - client.getTickCount()) * 0.6);
            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile",
                    "Last update within 30 seconds. You can update again in " + secondsLeft + " seconds.", "RuneProfile");
            return;
        }

        lastAttemptedUpdate = client.getTickCount();
        collectionLogWidgetSubscriber.triggerManualSync();
        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Updating your RuneProfile...", "RuneProfile");
    }

    @Subscribe
    public void onScriptPostFired(ScriptPostFired event) {
        if (event.getScriptId() == COLLECTION_INIT_SCRIPT || event.getScriptId() == COLLECTION_LOG_SETUP) {
            setupSyncButton();
            return;
        }

        if (event.getScriptId() != TOGGLE_SEARCH_SCRIPT) {
            return;
        }

        if (isUndoingSearchToggle) {
            isUndoingSearchToggle = false;
            setupSyncButton();
            return;
        }

        if (isSearchAction) {
            isSearchAction = false;
            return;
        }

        if (isSyncAction) {
            isSyncAction = false;

            Widget searchButton = client.getWidget(InterfaceID.Collection.SEARCH_TOGGLE);
            if (searchButton == null) {
                return;
            }

            Object[] onOpListener = searchButton.getOnOpListener();
            if (onOpListener == null) {
                return;
            }

            isUndoingSearchToggle = true;
            clientThread.invokeLater(() ->
                    client.createScriptEventBuilder(onOpListener)
                            .setSource(searchButton)
                            .setOp(1)
                            .build()
                            .run()
            );
            return;
        }

        setupSyncButton();
    }

    private boolean isSearchOpen() {
        Widget searchContainer = client.getWidget(InterfaceID.Collection.SEARCH_CONTAINER);
        return searchContainer != null && !searchContainer.isHidden();
    }

    private boolean isOpenedFromAdventureLog() {
        return client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
    }
}