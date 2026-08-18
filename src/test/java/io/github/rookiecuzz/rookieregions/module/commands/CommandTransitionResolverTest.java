package io.github.rookiecuzz.rookieregions.module.commands;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTransitionResolverTest {
    private final CommandTransitionResolver resolver =
            new CommandTransitionResolver();

    @Test
    void physicalEnterRunsGlobalToLeafAndThenBecomesIdempotent(){
        List<RegionPresence> current = Arrays.asList(
                new RegionPresence("leaf", 2),
                new RegionPresence("global", 0),
                new RegionPresence("parent", 1)
        );

        CommandTransition first = resolver.resolve(
                CommandTransitionState.empty(), current, profiles("global", "parent", "leaf")
        );
        CommandTransition repeated = resolver.resolve(
                first.getNextState(), current, profiles("global", "parent", "leaf")
        );

        assertEquals(Arrays.asList("global", "parent", "leaf"), keys(first));
        assertTrue(first.getActions().stream().allMatch(
                action -> action.getPhase() == CommandPhase.ENTER
        ));
        assertTrue(repeated.getActions().isEmpty());
    }

    @Test
    void physicalLeaveRunsLeafToGlobal(){
        List<RegionPresence> occupied = Arrays.asList(
                new RegionPresence("global", 0),
                new RegionPresence("parent", 1),
                new RegionPresence("leaf", 2)
        );
        CommandTransitionState previous = resolver.resolve(
                CommandTransitionState.empty(),
                occupied,
                Collections.<String, RegionCommandProfile>emptyMap()
        ).getNextState();

        CommandTransition left = resolver.resolve(
                previous,
                Collections.<RegionPresence>emptyList(),
                profiles("global", "parent", "leaf")
        );

        assertEquals(Arrays.asList("leaf", "parent", "global"), keys(left));
        assertTrue(left.getActions().stream().allMatch(
                action -> action.getPhase() == CommandPhase.LEAVE
        ));
    }

    @Test
    void branchMoveLeavesOldBeforeEnteringNewAndDoesNotUseMusicSelection(){
        CommandTransitionState previous = resolver.resolve(
                CommandTransitionState.empty(),
                Arrays.asList(
                        new RegionPresence("global", 0),
                        new RegionPresence("old", 1)
                ),
                Collections.<String, RegionCommandProfile>emptyMap()
        ).getNextState();
        Map<String, RegionCommandProfile> profiles = profiles("global", "old", "new");

        CommandTransition moved = resolver.resolve(
                previous,
                Arrays.asList(
                        new RegionPresence("global", 0),
                        new RegionPresence("new", 1)
                ),
                profiles
        );

        assertEquals(Arrays.asList("old", "new"), keys(moved));
        assertEquals(CommandPhase.LEAVE, moved.getActions().get(0).getPhase());
        assertEquals(CommandPhase.ENTER, moved.getActions().get(1).getPhase());
    }

    @Test
    void depthMetadataCanRefreshWithoutCreatingFalseTransitions(){
        CommandTransition initial = resolver.resolve(
                CommandTransitionState.empty(),
                Collections.singletonList(new RegionPresence("same", 1)),
                profiles("same")
        );

        CommandTransition refreshed = resolver.resolve(
                initial.getNextState(),
                Collections.singletonList(new RegionPresence("same", 3)),
                profiles("same")
        );

        assertTrue(refreshed.getActions().isEmpty());
        assertEquals(Integer.valueOf(3),
                refreshed.getNextState().getActiveRegions().get("same"));
    }

    @Test
    void duplicatePhysicalMembershipIsRejected(){
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(
                CommandTransitionState.empty(),
                Arrays.asList(
                        new RegionPresence("same", 1),
                        new RegionPresence("same", 2)
                ),
                profiles("same")
        ));
    }

    private Map<String, RegionCommandProfile> profiles(String... keys){
        Map<String, RegionCommandProfile> result = new LinkedHashMap<>();
        for(String key : keys){
            result.put(key, new RegionCommandProfile(
                    Collections.singletonList("enter " + key),
                    Collections.singletonList("leave " + key)
            ));
        }
        return result;
    }

    private List<String> keys(CommandTransition transition){
        return transition.getActions().stream()
                .map(RegionCommandAction::getRegionKey)
                .collect(Collectors.toList());
    }
}
