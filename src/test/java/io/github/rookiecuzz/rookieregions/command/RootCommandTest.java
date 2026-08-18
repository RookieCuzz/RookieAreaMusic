package io.github.rookiecuzz.rookieregions.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RootCommandTest {
    @Test
    void tabMatchingIsCaseInsensitiveSortedAndDistinct(){
        assertEquals(
                List.of("region", "reload"),
                RootCommand.matches(
                        "RE", List.of("reload", "region", "reload", "music")
                )
        );
    }

    @Test
    void globalCreateAliasesAreExplicitAndCaseInsensitive(){
        assertTrue(RootCommand.isGlobalAlias("global"));
        assertTrue(RootCommand.isGlobalAlias(" __GLOBAL__ "));
        assertFalse(RootCommand.isGlobalAlias("world"));
        assertFalse(RootCommand.isGlobalAlias(null));
    }
}
