package com.runeprofile.utils;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.config.RuneScapeProfileType;


@Slf4j
public class PlayerState {
    public static boolean isValidPlayerState(Client client) {
        GameState gameState = client.getGameState();
        if (gameState != GameState.LOGGED_IN) {
            log.debug("Invalid game state: {}", gameState);
            return false;
        }

        long accountHash = client.getAccountHash();
        if (accountHash == -1) {
            log.debug("Invalid account hash: {}", accountHash);
            return false;
        }

        Player player = client.getLocalPlayer();
        if (player == null || player.getName() == null) {
            log.debug("Invalid player");
            return false;
        }

        // first layer of world type validation
        RuneScapeProfileType profileType = RuneScapeProfileType.getCurrent(client);
        boolean isValidProfileType = profileType.equals(RuneScapeProfileType.STANDARD);
        if (!isValidProfileType) {
            log.debug("Invalid profile type: {}", profileType);
            return false;
        }

        return true;
    }
}
