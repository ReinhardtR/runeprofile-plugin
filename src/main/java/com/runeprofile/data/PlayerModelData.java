package com.runeprofile.data;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class PlayerModelData {
	private final String accountHash;
	private final byte[] model;
}
