package com.runeprofile.utils;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class RuneProfileApiException extends RuntimeException {
    public static final String CODE_ACCOUNT_NOT_FOUND = "AccountNotFound";

    @Nullable
    private final String code;
    private final int statusCode;

    public RuneProfileApiException(String message) {
        this(message, null, -1);
    }

    public RuneProfileApiException(String message, @Nullable String code, int statusCode) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }

    public boolean isAccountNotFound() {
        return CODE_ACCOUNT_NOT_FOUND.equals(code) || statusCode == 404;
    }
}
