package com.runeprofile.data;

import com.runeprofile.data.activities.ActivityData;
import com.runeprofile.data.activities.Activity;
import lombok.Data;

import java.util.List;

@Data
public class AddActivities {
    private final String id;
    private final List<? extends Activity<? extends ActivityData>> activities;
}
