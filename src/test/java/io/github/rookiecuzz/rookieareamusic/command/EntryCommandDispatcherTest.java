package io.github.rookiecuzz.rookieareamusic.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntryCommandDispatcherTest {
    @Test
    void expandsBuiltInsInOnePass(){
        EntryCommandDispatcher.PlaceholderValues values = values("Alex");

        String expanded = EntryCommandDispatcher.expandBuiltIns(
                "demo {player} %player_name% {player_uuid} {world}/{area_world} "
                        + "{area}/{area_uuid} {x} {y} {z} "
                        + "{block_x} {block_y} {block_z}",
                values
        );

        assertEquals(
                "demo Alex Alex 123e4567-e89b-12d3-a456-426614174000 world/region_world "
                        + "boss_gate/area-uuid 12.25 70.5 -8.75 12 70 -9",
                expanded
        );
    }

    @Test
    void insertedValuesAreNotExpandedRecursively(){
        String expanded = EntryCommandDispatcher.expandBuiltIns(
                "demo {area} {player}",
                new EntryCommandDispatcher.PlaceholderValues(
                        "Alex",
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                        "world",
                        "region_world",
                        "{player}",
                        "area-uuid",
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );

        assertEquals("demo {player} Alex", expanded);
    }

    @Test
    void rejectsUnsafeOrUnresolvedExpandedCommands(){
        assertTrue(EntryCommandDispatcher.isSafeExpandedCommand("effect give Alex speed"));
        assertFalse(EntryCommandDispatcher.isSafeExpandedCommand(""));
        assertFalse(EntryCommandDispatcher.isSafeExpandedCommand("say first\nsay second"));
        assertFalse(EntryCommandDispatcher.isSafeExpandedCommand("demo %vault_rank%"));
        assertFalse(EntryCommandDispatcher.isSafeExpandedCommand(
                "demo %checkitem_mat:STONE%"
        ));
        assertFalse(EntryCommandDispatcher.isSafeExpandedCommand(
                "demo %math_[2]:HALF_UP_1/3%"
        ));
        assertFalse(EntryCommandDispatcher.isSafeExpandedCommand(
                repeat('x', EntryCommandDispatcher.MAX_EXPANDED_COMMAND_LENGTH + 1)
        ));
    }

    private EntryCommandDispatcher.PlaceholderValues values(String playerName){
        return new EntryCommandDispatcher.PlaceholderValues(
                playerName,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                "world",
                "region_world",
                "boss_gate",
                "area-uuid",
                12.25,
                70.5,
                -8.75,
                12,
                70,
                -9
        );
    }

    private String repeat(char value, int count){
        StringBuilder result = new StringBuilder(count);
        for(int index = 0; index < count; index++){
            result.append(value);
        }
        return result.toString();
    }
}
