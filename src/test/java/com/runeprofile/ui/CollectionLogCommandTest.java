package com.runeprofile.ui;

import com.google.gson.Gson;
import com.runeprofile.data.CollectionLogCounter;
import com.runeprofile.data.CollectionLogPage;
import org.junit.Test;

import java.awt.Color;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CollectionLogCommandTest {
    @Test
    public void formatsKnownKillCountsInPageOrderAndSkipsUnknownAndPersonalBests() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            String formatted = CollectionLogCommand.formatCounters(Arrays.asList(
                    new CollectionLogCounter("Corrupted Gauntlet completion count", "CG KC", "kc", 45.0),
                    new CollectionLogCounter("Gauntlet completion count", "KC", "kc", null),
                    new CollectionLogCounter("Personal Best Corrupted", "CG PB", "pb", 372.6),
                    new CollectionLogCounter("Personal Best", "PB", "pb", 1234.0)
            ), ChatSeparator.PLAIN);
            assertEquals(" | CG KC: 45", formatted);

            assertEquals(" | KC: 12,345", CollectionLogCommand.formatCounters(Arrays.asList(
                    new CollectionLogCounter("Zulrah kills", "KC", "kc", 12345.0)), ChatSeparator.PLAIN));
            // Older APIs send no short label
            assertEquals(" | Zulrah kills: 12,345", CollectionLogCommand.formatCounters(Arrays.asList(
                    new CollectionLogCounter("Zulrah kills", null, "kc", 12345.0)), ChatSeparator.PLAIN));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void handlesPagesFromOlderApis() {
        CollectionLogPage page = new Gson().fromJson(
                "{\"name\":\"Zulrah\",\"items\":[{\"id\":1,\"quantity\":0}]}", CollectionLogPage.class);
        assertNull(page.getCounters());
        assertEquals("", CollectionLogCommand.formatCounters(page.getCounters(), ChatSeparator.PLAIN));
    }

    @Test
    public void parsesCountersFromTheApi() {
        CollectionLogPage page = new Gson().fromJson("{\"name\":\"Zulrah\",\"items\":[],\"counters\":["
                + "{\"label\":\"Zulrah kills\",\"type\":\"kc\",\"value\":10},"
                + "{\"label\":\"Personal Best\",\"shortLabel\":\"PB\",\"type\":\"pb\",\"value\":null}]}", CollectionLogPage.class);
        assertEquals(2, page.getCounters().size());
        assertEquals("Zulrah kills", page.getCounters().get(0).getDisplayLabel());
        assertEquals("PB", page.getCounters().get(1).getDisplayLabel());
        assertEquals(Double.valueOf(10), page.getCounters().get(0).getValue());
        assertNull(page.getCounters().get(1).getValue());
    }

    @Test
    public void prefersTheLogOrderOverThePageOrder() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            CollectionLogPage page = new Gson().fromJson("{\"name\":\"Theatre of Blood\",\"items\":[],\"counters\":["
                    + "{\"label\":\"Theatre of Blood (Hard) completions\",\"shortLabel\":\"HMT KC\",\"type\":\"kc\",\"value\":45},"
                    + "{\"label\":\"Theatre of Blood (Entry) completions\",\"shortLabel\":\"Entry KC\",\"type\":\"kc\",\"value\":3},"
                    + "{\"label\":\"Theatre of Blood completions\",\"shortLabel\":\"KC\",\"type\":\"kc\",\"value\":312}],"
                    + "\"logCounters\":["
                    + "{\"label\":\"Theatre of Blood completions\",\"shortLabel\":\"KC\",\"type\":\"kc\",\"value\":312},"
                    + "{\"label\":\"Theatre of Blood (Hard) completions\",\"shortLabel\":\"HMT KC\",\"type\":\"kc\",\"value\":45}]}",
                    CollectionLogPage.class);
            assertEquals(" | KC: 312 | HMT KC: 45", CollectionLogCommand.formatCounters(page.getChatCounters(), ChatSeparator.PLAIN));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void dimsTheSeparatorTowardsTheChatboxBackground() {
        // Opaque public chat's default blue, halfway into the parchment
        assertEquals(new Color(0x64, 0x5C, 0xC9), ChatSeparator.dim(new Color(0x0000FF), false));
        // Transparent: halfway to black
        assertEquals(new Color(0x48, 0x48, 0x7F), ChatSeparator.dim(new Color(0x9090FF), true));
    }

    @Test
    public void colouredSeparatorSwitchesBackToTheMessageColour() {
        assertEquals(" <col=645cc9>|<colNORMAL> ", ChatSeparator.of(new Color(0x645CC9)));
        assertEquals(" | ", ChatSeparator.of(null));
        assertEquals(" <col=645cc9>|<colNORMAL> KC: 312", CollectionLogCommand.formatCounters(Arrays.asList(
                new CollectionLogCounter("Theatre of Blood completions", "KC", "kc", 312.0)),
                ChatSeparator.of(new Color(0x645CC9))));
    }
}
