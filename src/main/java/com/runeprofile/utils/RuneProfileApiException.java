package com.runeprofile.utils;

import lombok.Getter;

@Getter
public class RuneProfileApiException extends RuntimeException {
    public RuneProfileApiException(String message) {
        super(message);
    }
}