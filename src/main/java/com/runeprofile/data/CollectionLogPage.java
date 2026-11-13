package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

@Data
public class CollectionLogPage {
    private final String name;
    private final List<CollectionLogItem> items;

    // Absent from API versions that predate kill counts on pages.
    @Nullable
    private final List<CollectionLogCounter> counters;

    // The kill counts for the !log reply, in its order (normal mode first, entry modes left out).
    // Absent from older API versions; fall back to counters then.
    @Nullable
    private final List<CollectionLogCounter> logCounters;

    @Nullable
    public List<CollectionLogCounter> getChatCounters() {
        return logCounters != null ? logCounters : counters;
    }
}
