package io.github.rookiecuzz.rookieregions.protection;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.module.commands.CommandTransition;
import io.github.rookiecuzz.rookieregions.module.commands.CommandTransitionResolver;
import io.github.rookiecuzz.rookieregions.module.commands.CommandTransitionState;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.commands.RegionPresence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure physical-membership and command transition planner. */
final class RegionTransitionPlanner {
    private static final Comparator<Presence> ENTER_ORDER = Comparator
            .comparingInt(Presence::depth)
            .thenComparing(presence -> presence.region().key());
    private static final Comparator<Presence> LEAVE_ORDER = Comparator
            .comparingInt(Presence::depth)
            .reversed()
            .thenComparing(presence -> presence.region().key());

    private final CommandTransitionResolver commands = new CommandTransitionResolver();

    Plan plan(Observation previous, Collection<Presence> currentSource) {
        Observation safePrevious = previous == null ? Observation.empty() : previous;
        Map<RegionKey, Presence> current = normalize(currentSource);

        ArrayList<Presence> left = new ArrayList<>();
        for(Map.Entry<RegionKey, Presence> entry : safePrevious.regions().entrySet()){
            if(!current.containsKey(entry.getKey())){
                left.add(entry.getValue());
            }
        }
        left.sort(LEAVE_ORDER);

        ArrayList<Presence> entered = new ArrayList<>();
        for(Map.Entry<RegionKey, Presence> entry : current.entrySet()){
            if(!safePrevious.regions().containsKey(entry.getKey())){
                entered.add(entry.getValue());
            }
        }
        entered.sort(ENTER_ORDER);

        LinkedHashMap<String, RegionCommandProfile> profiles = new LinkedHashMap<>();
        for(Presence presence : current.values()){
            profiles.put(presence.region().key().toString(), presence.commands());
        }
        // A deleted region is absent from the new snapshot; its leave commands
        // must come from the observation that saw the player inside it.
        for(Presence presence : left){
            profiles.put(presence.region().key().toString(), presence.commands());
        }

        List<RegionPresence> commandPresence = current.values().stream()
                .map(presence -> new RegionPresence(
                        presence.region().key().toString(),
                        presence.depth()
                ))
                .toList();
        CommandTransition commandTransition = commands.resolve(
                safePrevious.commands(),
                commandPresence,
                profiles
        );
        Observation next = new Observation(current, commandTransition.getNextState());
        return new Plan(left, entered, commandTransition, next);
    }

    private static Map<RegionKey, Presence> normalize(Collection<Presence> source) {
        if(source == null){
            throw new IllegalArgumentException("current region presence cannot be null");
        }
        LinkedHashMap<RegionKey, Presence> result = new LinkedHashMap<>();
        for(Presence presence : source){
            if(presence == null){
                throw new IllegalArgumentException(
                        "current region presence cannot contain null"
                );
            }
            RegionKey key = presence.region().key();
            if(result.putIfAbsent(key, presence) != null){
                throw new IllegalArgumentException("duplicate current region " + key);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    record Presence(Region region,
                    int depth,
                    RegionCommandProfile commands) {
        Presence {
            Objects.requireNonNull(region, "observed region cannot be null");
            Objects.requireNonNull(commands, "observed command profile cannot be null");
            if(depth < 0){
                throw new IllegalArgumentException("observed region depth cannot be negative");
            }
        }
    }

    record Observation(Map<RegionKey, Presence> regions,
                       CommandTransitionState commands) {
        Observation {
            regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
            Objects.requireNonNull(commands, "command transition state cannot be null");
        }

        static Observation empty() {
            return new Observation(Map.of(), CommandTransitionState.empty());
        }
    }

    record Plan(List<Presence> left,
                List<Presence> entered,
                CommandTransition commands,
                Observation next) {
        Plan {
            left = List.copyOf(left);
            entered = List.copyOf(entered);
            Objects.requireNonNull(commands, "command transition cannot be null");
            Objects.requireNonNull(next, "next transition observation cannot be null");
        }
    }
}
