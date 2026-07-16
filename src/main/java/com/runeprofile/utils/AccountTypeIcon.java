package com.runeprofile.utils;

import com.google.gson.Gson;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;

/**
 * Account type badges, loaded from the same {@code account-type-icons.json}
 * asset the RuneProfile website uses (base64 PNGs keyed by account type key), so
 * the plugin and site show identical icons. Normal and unknown account types
 * have no badge.
 */
@Singleton
public final class AccountTypeIcon {
    private final IconSheet icons;

    @Inject
    AccountTypeIcon(Gson gson) {
        this.icons = new IconSheet(gson, "/account-type-icons.json");
    }

    /**
     * Badge for an account type key (e.g. {@code "hardcore_ironman"}), or
     * {@code null} for normal/unknown account types.
     */
    public @Nullable ImageIcon getByKey(@Nullable String key) {
        return icons.get(key);
    }
}
