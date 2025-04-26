package com.runeprofile.data;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;

@RequiredArgsConstructor
@Getter
public class PlayerModelData {
    private final String accountHash;
    private final byte[] model;
    @Nullable
    private final byte[] petModel;
}
