package com.runeprofile.autosync;

import com.runeprofile.RuneProfileConfig;
import com.runeprofile.utils.PlayerState;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.Experience;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.EnumMap;
import java.util.Map;

@Slf4j
@Singleton
public class SkillSubscriber {
    @Inject
    private EventBus eventBus;

    @Inject
    private Client client;

    @Inject
    private RuneProfileConfig config;

    @Inject
    private AutoSyncScheduler autoSyncScheduler;

    private final Map<Skill, Integer> previousLevels = new EnumMap<>(Skill.class);
    private final Map<Skill, Integer> previousXp = new EnumMap<>(Skill.class);

    private boolean initialized = false;
    private int initTicksRemaining = 0;

    /**
     * Wait after login before initializing skill baselines.
     */
    private static final int INIT_GAME_TICKS = 16;

    private static final int[] XP_MILESTONES = {
            15_000_000, 20_000_000, 25_000_000, 30_000_000, 35_000_000, 40_000_000,
            45_000_000, 50_000_000, 55_000_000, 60_000_000, 65_000_000, 70_000_000,
            75_000_000, 80_000_000, 85_000_000, 90_000_000, 95_000_000, 100_000_000,
            105_000_000, 110_000_000, 115_000_000, 120_000_000, 125_000_000, 130_000_000,
            135_000_000, 140_000_000, 145_000_000, 150_000_000, 155_000_000, 160_000_000,
            165_000_000, 170_000_000, 175_000_000, 180_000_000, 185_000_000, 190_000_000,
            195_000_000, 200_000_000,
    };

    private static final int MAX_REAL_LEVEL = 99;

    public void startUp() {
        eventBus.register(this);
    }

    public void shutDown() {
        eventBus.unregister(this);
        reset();
    }

    private void reset() {
        previousLevels.clear();
        previousXp.clear();
        initialized = false;
        initTicksRemaining = 0;
    }

    private void initialize() {
        for (Skill skill : Skill.values()) {
            if (skill == Skill.OVERALL) continue;
            int xp = client.getSkillExperience(skill);
            int level = Experience.getLevelForXp(xp);
            previousXp.put(skill, xp);
            previousLevels.put(skill, level);
        }
        initialized = true;
        initTicksRemaining = 0;
        log.debug("SkillSubscriber initialized with current skill levels");
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            initTicksRemaining = INIT_GAME_TICKS;
        } else if (event.getGameState() != GameState.HOPPING) {
            reset();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (initTicksRemaining > 0) {
            initTicksRemaining--;
            if (initTicksRemaining == 0) {
                initialize();
            }
        }
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!initialized || !config.autosyncProfile() || !PlayerState.isValidPlayerState(client)) {
            return;
        }

        Skill skill = event.getSkill();
        if (skill == Skill.OVERALL) return;

        int newXp = event.getXp();
        int newLevel = Experience.getLevelForXp(newXp);

        Integer oldLevel = previousLevels.get(skill);
        Integer oldXp = previousXp.get(skill);

        if (oldLevel == null || oldXp == null) {
            previousLevels.put(skill, newLevel);
            previousXp.put(skill, newXp);
            return;
        }

        // Level up
        if (newLevel > oldLevel && newLevel <= MAX_REAL_LEVEL) {
            log.debug("Level up detected: {} {} -> {}", skill.getName(), oldLevel, newLevel);
            autoSyncScheduler.startRapidSync("level-up");
        }

        // XP milestones
        if (newLevel >= MAX_REAL_LEVEL && newXp > oldXp) {
            for (int milestone : XP_MILESTONES) {
                if (oldXp < milestone && newXp >= milestone) {
                    log.debug("XP milestone detected: {} crossed {}xp", skill.getName(), milestone);
                    autoSyncScheduler.startRapidSync("xp-milestone");
                    break;
                }
            }
        }

        previousLevels.put(skill, newLevel);
        previousXp.put(skill, newXp);
    }
}
