package com.runeprofile.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarbitID;

@Getter
@RequiredArgsConstructor
public enum CombatAchievementTier {
    EASY(1, VarbitID.CA_TOTAL_TASKS_COMPLETED_EASY),
    MEDIUM(2, VarbitID.CA_TOTAL_TASKS_COMPLETED_MEDIUM),
    HARD(3, VarbitID.CA_TOTAL_TASKS_COMPLETED_HARD),
    ELITE(4, VarbitID.CA_TOTAL_TASKS_COMPLETED_ELITE),
    MASTER(5, VarbitID.CA_TOTAL_TASKS_COMPLETED_MASTER),
    GRANDMASTER(6, VarbitID.CA_TOTAL_TASKS_COMPLETED_GRANDMASTER);

    private final int id;
    private final int varbit;

    public int getCompletedCount(Client client) {
        // https://github.com/RuneStar/cs2-scripts/blob/master/scripts/%5Bproc%2Cca_tasks_completed_tier%5D.cs2
        client.runScript(4784, id);
        return client.getIntStack()[0];
    }
}
