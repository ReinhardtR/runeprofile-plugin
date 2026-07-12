package com.runeprofile.data.activities;

import lombok.Data;

import javax.annotation.Nullable;
import java.util.List;

@Data
public class ActivitiesResponse {
    private final List<ActivityRecord> activities;
    @Nullable
    private final String nextCursor;
    private final boolean hasMore;
}
