package com.runeprofile.utils;

/**
 * Whether the plugin is running in a development client.
 * <p>
 * RuneLite requires {@code -ea} to side-load a plugin, so enabled assertions
 * mean a dev client; they are always off in a released build.
 */
public class DevMode {
    public static final boolean ENABLED = assertionsEnabled();

    @SuppressWarnings("AssertWithSideEffects")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        //noinspection ConstantConditions
        assert enabled = true;
        return enabled;
    }
}
