package com.runeprofile.dataobjects;


import com.google.gson.JsonObject;
import com.runeprofile.playermodel.PlayerModelExporter;
import com.runeprofile.utils.AccountHash;
import lombok.Getter;
import net.runelite.api.Client;

import java.io.IOException;

public class PlayerModelData {
	@Getter
	private final JsonObject json;

	public PlayerModelData(Client client) throws IllegalArgumentException, IOException {
		json = new JsonObject();

		json.addProperty("accountHash", AccountHash.getHashed(client));
		json.addProperty(
						"model",
						PlayerModelExporter.export(
										client.getLocalPlayer().getModel(),
										client.getLocalPlayer().getName()
						)
		);
	}
}
