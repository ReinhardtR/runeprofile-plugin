package com.runeprofile.data;

import lombok.Data;

@Data
public class UpdateProfileResult {
    private final String message;
    private final boolean created;
}
