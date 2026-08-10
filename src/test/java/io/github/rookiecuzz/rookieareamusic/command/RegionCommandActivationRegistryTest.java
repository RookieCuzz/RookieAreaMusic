package io.github.rookiecuzz.rookieareamusic.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionCommandActivationRegistryTest {
    private static final UUID PLAYER = UUID.fromString(
            "123e4567-e89b-12d3-a456-426614174000"
    );

    @Test
    void matchingExitConsumesExactlyOneAcceptedEntry(){
        RegionCommandActivationRegistry registry =
                new RegionCommandActivationRegistry();

        assertTrue(registry.activate(PLAYER, "boss", 1L));
        assertTrue(registry.hasActivation(PLAYER, "boss"));
        assertTrue(registry.consume(PLAYER, "boss", 1L));
        assertFalse(registry.hasActivation(PLAYER, "boss"));
        assertFalse(registry.consume(PLAYER, "boss", 1L));
    }

    @Test
    void staleTokenCannotConsumeOrReplaceANewerActivation(){
        RegionCommandActivationRegistry registry =
                new RegionCommandActivationRegistry();

        assertTrue(registry.activate(PLAYER, "boss", 2L));
        assertFalse(registry.activate(PLAYER, "boss", 3L));
        assertFalse(registry.consume(PLAYER, "boss", 1L));
        assertTrue(registry.hasActivation(PLAYER, "boss"));
        assertTrue(registry.consume(PLAYER, "boss", 2L));
    }

    @Test
    void playerAndPluginCleanupDropPendingActivations(){
        RegionCommandActivationRegistry registry =
                new RegionCommandActivationRegistry();
        UUID secondPlayer = UUID.fromString(
                "123e4567-e89b-12d3-a456-426614174001"
        );
        registry.activate(PLAYER, "boss", 1L);
        registry.activate(secondPlayer, "boss", 1L);

        registry.clearPlayer(PLAYER);
        assertFalse(registry.hasActivation(PLAYER, "boss"));
        assertTrue(registry.hasActivation(secondPlayer, "boss"));

        registry.clearAll();
        assertFalse(registry.hasActivation(secondPlayer, "boss"));
    }

    @Test
    void invalidKeysAndTokensAreRejected(){
        RegionCommandActivationRegistry registry =
                new RegionCommandActivationRegistry();

        assertFalse(registry.activate(null, "boss", 1L));
        assertFalse(registry.activate(PLAYER, null, 1L));
        assertFalse(registry.activate(PLAYER, "boss", 0L));
        assertFalse(registry.consume(PLAYER, "boss", -1L));
    }
}
