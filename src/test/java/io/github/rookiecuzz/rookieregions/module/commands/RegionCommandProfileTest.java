package io.github.rookiecuzz.rookieregions.module.commands;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCommandProfileTest {
    @Test
    void profileIsImmutableAndNormalizesConsoleCommands(){
        List<String> enter = new ArrayList<>(Arrays.asList(
                " /say entered ",
                "effect give {player} glowing"
        ));
        RegionCommandProfile profile = new RegionCommandProfile(
                enter,
                Collections.singletonList(" /say left ")
        );

        enter.clear();

        assertEquals(Arrays.asList(
                "say entered",
                "effect give {player} glowing"
        ), profile.getEnterCommands());
        assertEquals(Collections.singletonList("say left"),
                profile.getLeaveCommands());
        assertThrows(UnsupportedOperationException.class, () ->
                profile.getEnterCommands().clear());
        assertThrows(IllegalArgumentException.class, () ->
                new RegionCommandProfile(
                        Collections.singletonList("/"),
                        Collections.<String>emptyList()
                ));
    }

    @Test
    void pureParserKeepsEnterAndLeaveSeparate(){
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("enter", Collections.singletonList("/tag {player} add inside"));
        raw.put("leave", Collections.singletonList("/tag {player} remove inside"));

        RegionCommandProfile parsed = new CommandModuleParser().parse(raw);

        assertEquals(Collections.singletonList("tag {player} add inside"),
                parsed.getEnterCommands());
        assertEquals(Collections.singletonList("tag {player} remove inside"),
                parsed.getLeaveCommands());
        assertTrue(new CommandModuleParser().parse(null).isEmpty());

        raw.put("enter", "say invalid");
        assertThrows(IllegalArgumentException.class, () ->
                new CommandModuleParser().parse(raw));
    }
}
