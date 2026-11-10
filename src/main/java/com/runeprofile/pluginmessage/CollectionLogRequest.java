package com.runeprofile.pluginmessage;

import lombok.Data;

/**
 * The data of a {@code collection-log-request} plugin message.
 * See docs/external-plugin-messaging.md.
 */
@Data
public class CollectionLogRequest {
    /**
     * The name of the plugin making the request. Optional, only used in debug logs.
     */
    private String sourcePlugin;

    /**
     * A collection log page name or alias, the same as the !log command accepts. Required.
     */
    private String page;
}
