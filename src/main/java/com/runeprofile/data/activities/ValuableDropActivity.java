package com.runeprofile.data.activities;

import lombok.Getter;

@Getter
public class ValuableDropActivity extends Activity<ValuableDropActivity.Data> {
    public ValuableDropActivity(ValuableDropActivity.Data data) {
        super("valuable_drop", data);
    }

    @lombok.Data
    public static class Data implements ActivityData {
        private final int itemId;
        private final int value;
    }
}
