package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;

@Data
public class AccountInfo {
    private final String username;
    private final String accountType;
    @Nullable
    private final String clanName;
    private final String createdAt;
    private final String updatedAt;
}
