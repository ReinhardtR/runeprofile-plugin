package com.runeprofile.utils;

import net.runelite.api.Client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class AccountHash {
	public static String getHashed(Client client) {
		long accountHashLong = client.getAccountHash();

		if (accountHashLong == -1) {
			return null;
		}

		String accountHashString = String.valueOf(accountHashLong);

		MessageDigest digest = null;

		try {
			digest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
		}

		byte[] hash = digest.digest(accountHashString.getBytes(StandardCharsets.UTF_8));

		return Base64.getEncoder().encodeToString(hash);
	}
}
