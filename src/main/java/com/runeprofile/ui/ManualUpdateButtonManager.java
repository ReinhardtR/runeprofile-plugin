package com.runeprofile.ui;

import com.google.inject.Inject;
import com.runeprofile.RuneProfileConfig;
import com.runeprofile.autosync.CollectionLogWidgetSubscriber;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import java.util.*;

import static java.lang.Math.round;

@Slf4j
public class ManualUpdateButtonManager {
    private static final int DRAW_BURGER_MENU = 7812;
    private static final int FONT_COLOR = 0xFF981F;
    private static final int FONT_COLOR_ACTIVE = 0xFFFFFF;
    private static final String BUTTON_TEXT = "RuneProfile";

    private final Client client;
    private final EventBus eventBus;
    private final RuneProfileConfig config;
    private final CollectionLogWidgetSubscriber collectionLogWidgetSubscriber;

    private int baseMenuHeight = -1;
    private int lastAttemptedUpdate = -1;

    @Inject
    private ManualUpdateButtonManager(
            Client client,
            EventBus eventBus,
            RuneProfileConfig config,
            CollectionLogWidgetSubscriber collectionLogWidgetSubscriber
    ) {
        this.client = client;
        this.eventBus = eventBus;
        this.config = config;
        this.collectionLogWidgetSubscriber = collectionLogWidgetSubscriber;
    }

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
    }

    @Subscribe
    public void onScriptPreFired(ScriptPreFired event) {
        if (!config.showClogSyncButton() || event.getScriptId() != DRAW_BURGER_MENU) {
            return;
        }

        Object[] args = event.getScriptEvent().getArguments();
        int menuId = (int) args[3];

        try {
            log.debug("Adding RuneProfile button to menu with ID: {}", menuId);
            addButton(menuId, this::onButtonClick);
        } catch (Exception e) {
            log.debug("Failed to add RuneProfile button to menu: {}", e.getMessage());
        }
    }

    private void onButtonClick() {
        if (lastAttemptedUpdate != -1 && lastAttemptedUpdate + 50 > client.getTickCount()) {
            client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Last update within 30 seconds. You can update again in " + round((lastAttemptedUpdate + 50 - client.getTickCount()) * 0.6) + " seconds.", "RuneProfile");
            return;
        }
        lastAttemptedUpdate = client.getTickCount();

        collectionLogWidgetSubscriber.setManualSync(true);
        client.menuAction(-1, 40697932, MenuAction.CC_OP, 1, -1, "Search", null);
        client.runScript(2240);

        client.addChatMessage(ChatMessageType.CONSOLE, "RuneProfile", "Updating your profile...", "RuneProfile");
    }

    private void addButton(int menuId, Runnable onClick) throws NullPointerException, NoSuchElementException {
        // disallow updating from the adventure log, to avoid players updating their profile
        // while viewing other players collection logs using the POH adventure log.
        boolean isOpenedFromAdventureLog = client.getVarbitValue(VarbitID.COLLECTION_POH_HOST_BOOK_OPEN) == 1;
        if (isOpenedFromAdventureLog) return;

        Widget menu = Objects.requireNonNull(client.getWidget(menuId));
        Widget[] menuChildren = Objects.requireNonNull(menu.getChildren());

        if (baseMenuHeight == -1) {
            baseMenuHeight = menu.getOriginalHeight();
        }

        List<Widget> reversedMenuChildren = new ArrayList<>(Arrays.asList(menuChildren));
        Collections.reverse(reversedMenuChildren);
        Widget lastRectangle = reversedMenuChildren.stream()
                .filter(w -> w.getType() == WidgetType.RECTANGLE)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No RECTANGLE widget found in menu"));
        Widget lastText = reversedMenuChildren.stream()
                .filter(w -> w.getType() == WidgetType.TEXT)
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No TEXT widget found in menu"));

        final int buttonHeight = lastRectangle.getHeight();
        final int buttonY = lastRectangle.getOriginalY() + buttonHeight;

        final boolean existingButton = Arrays.stream(menuChildren)
                .anyMatch(w -> w.getText().equals(BUTTON_TEXT));

        log.debug("Existing button found: {}", existingButton);
        if (!existingButton) {
            final Widget background = menu.createChild(WidgetType.RECTANGLE)
                    .setOriginalWidth(lastRectangle.getOriginalWidth())
                    .setOriginalHeight(lastRectangle.getOriginalHeight())
                    .setOriginalX(lastRectangle.getOriginalX())
                    .setOriginalY(buttonY)
                    .setOpacity(lastRectangle.getOpacity())
                    .setFilled(lastRectangle.isFilled());
            background.revalidate();

            final Widget text = menu.createChild(WidgetType.TEXT)
                    .setText(BUTTON_TEXT)
                    .setTextColor(FONT_COLOR)
                    .setFontId(lastText.getFontId())
                    .setTextShadowed(lastText.getTextShadowed())
                    .setOriginalWidth(lastText.getOriginalWidth())
                    .setOriginalHeight(lastText.getOriginalHeight())
                    .setOriginalX(lastText.getOriginalX())
                    .setOriginalY(buttonY)
                    .setXTextAlignment(lastText.getXTextAlignment())
                    .setYTextAlignment(lastText.getYTextAlignment());
            text.setHasListener(true);
            text.setOnMouseOverListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR_ACTIVE));
            text.setOnMouseLeaveListener((JavaScriptCallback) ev -> text.setTextColor(FONT_COLOR));
            text.setAction(0, "Update your RuneProfile");
            text.setOnOpListener((JavaScriptCallback) ev -> onClick.run());
            text.revalidate();
        }

        if (menu.getOriginalHeight() <= baseMenuHeight) {
            menu.setOriginalHeight((menu.getOriginalHeight() + buttonHeight));
        }

        menu.revalidate();
        for (Widget child : menuChildren) {
            child.revalidate();
        }
    }
}