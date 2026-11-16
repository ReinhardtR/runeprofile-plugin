package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

@Data
public class CollectionLogPage {
    private final String name;
    private final List<CollectionLogItem> items;

    @Nullable
    private final List<CollectionLogCounter> counters;

    // the !log reply's kill counts, in reply order
    @Nullable
    private final List<CollectionLogCounter> logCounters;

    @Nullable
    public List<CollectionLogCounter> getChatCounters() {
        return logCounters != null ? logCounters : counters;
    }
}
