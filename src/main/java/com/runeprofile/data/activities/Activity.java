package com.runeprofile.data.activities;

import lombok.Getter;

@Getter
public abstract class Activity<T extends ActivityData> {
    private final String type;
    private final T data;

    protected Activity(String type, T data) {
        this.type = type;
        this.data = data;
    }
}
