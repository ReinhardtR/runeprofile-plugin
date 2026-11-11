package com.runeprofile.ui;

import net.runelite.client.util.ColorUtil;

import javax.annotation.Nullable;
import java.awt.Color;

/**
 * The " | " between parts of the !log reply, drawn in a dimmed version of the chat's own text colour
 * so it separates without competing with the text.
 */
final class ChatSeparator {
    // Roughly what's behind the text: the opaque chatbox's parchment, or the (usually dark) game world
    // behind a transparent chatbox. Only used as a blend target, so it needn't be exact.
    static final Color OPAQUE_BACKGROUND = new Color(0xC8B994);
    static final Color TRANSPARENT_BACKGROUND = Color.BLACK;

    static final String PLAIN = " | ";

    private ChatSeparator() {
    }

    /** The text colour blended halfway into the chatbox background. */
    static Color dim(Color text, boolean transparentChatbox) {
        Color background = transparentChatbox ? TRANSPARENT_BACKGROUND : OPAQUE_BACKGROUND;
        return new Color(
                (text.getRed() + background.getRed()) / 2,
                (text.getGreen() + background.getGreen()) / 2,
                (text.getBlue() + background.getBlue()) / 2);
    }

    /**
     * A separator in the given colour. Switches back with RuneLite's {@code <colNORMAL>}, which it
     * resolves to the message's own text colour (a plain {@code </col>} would fall back to the chatbox
     * default instead). Plain when there's no colour to use.
     */
    static String of(@Nullable Color color) {
        return color == null ? PLAIN : " " + ColorUtil.colorTag(color) + "|<colNORMAL> ";
    }
}
