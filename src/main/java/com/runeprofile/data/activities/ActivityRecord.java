package com.runeprofile.data.activities;

import lombok.Data;

@Data
public class ActivityRecord {
    private final String id;
    private final String type;
    // Safe as long as only valuable_drop activities are requested
    private final ValuableDropActivity.Data data;
    private final String createdAt;
}
