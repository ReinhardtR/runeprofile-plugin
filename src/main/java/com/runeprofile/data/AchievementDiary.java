package com.runeprofile.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;

@Slf4j
@Getter
@RequiredArgsConstructor
public enum AchievementDiary {
    KARAMJA(0),
    ARDOUGNE(1),
    FALADOR(2),
    FREMENNIK(3),
    KANDARIN(4),
    DESERT(5),
    LUMBRIDGE(6),
    MORYTANIA(7),
    VARROCK(8),
    WILDERNESS(9),
    WESTERN_PROVINCES(10),
    KOUREND(11);

    private final int id;

    public int[] getTiersCompletedCount(Client client) {
        // https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cdiary_completion_info%5D.cs2
        client.runScript(2200, id);
        int[] stack = client.getIntStack();

        int easy = stack[0];
        int medium = stack[3];
        int hard = stack[6];
        int elite = stack[9];

        return new int[]{easy, medium, hard, elite};
    }
}
