package com.runeprofile.utils;

import javax.annotation.Nullable;
import javax.swing.ImageIcon;

/**
 * Account type badges, loaded from the same {@code account-type-icons.json}
 * asset the RuneProfile website uses (base64 PNGs keyed by account type key), so
 * the plugin and site show identical icons. Normal and unknown account types
 * have no badge.
 */
public final class AccountTypeIcon {
    private AccountTypeIcon() {
    }

    private static final IconSheet ICONS = new IconSheet("/account-type-icons.json");

    /**
     * Badge for an account type key (e.g. {@code "hardcore_ironman"}), or
     * {@code null} for normal/unknown account types.
     */
    public static @Nullable ImageIcon getByKey(@Nullable String key) {
        return ICONS.get(key);
    }
}
