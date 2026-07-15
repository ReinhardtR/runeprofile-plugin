package com.runeprofile.data;

import lombok.Data;

import javax.annotation.Nullable;

@Data
public class ProfileSearchResult {
    private final String username;

    /**
     * Account type returned by the search endpoint. May be null for older API
     * responses, so treat it defensively.
     */
    @Nullable
    private final AccountType accountType;

    @Data
    public static class AccountType {
        private final int id;
        private final String key;
        private final String name;
    }
}
