package io.github.rookiecuzz.rookieregions.module.commands;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, idempotent physical membership resolver. Reusing the returned state
 * with the same current membership emits no commands.
 */
public final class CommandTransitionResolver {
    private static final Comparator<RegionPresence> ENTER_ORDER =
            new Comparator<RegionPresence>() {
                @Override
                public int compare(RegionPresence first, RegionPresence second) {
                    int depth = Integer.compare(first.getDepth(), second.getDepth());
                    return depth != 0
                            ? depth
                            : first.getRegionKey().compareTo(second.getRegionKey());
                }
            };
    private static final Comparator<RegionPresence> LEAVE_ORDER =
            new Comparator<RegionPresence>() {
                @Override
                public int compare(RegionPresence first, RegionPresence second) {
                    int depth = Integer.compare(second.getDepth(), first.getDepth());
                    return depth != 0
                            ? depth
                            : first.getRegionKey().compareTo(second.getRegionKey());
                }
            };

    public CommandTransition resolve(
            CommandTransitionState previous,
            Collection<RegionPresence> current,
            Map<String, RegionCommandProfile> profiles){
        CommandTransitionState safePrevious = previous == null
                ? CommandTransitionState.empty()
                : previous;
        Map<String, Integer> currentRegions = normalizeCurrent(current);
        Map<String, RegionCommandProfile> safeProfiles = normalizeProfiles(profiles);

        List<RegionPresence> left = new ArrayList<>();
        for(Map.Entry<String, Integer> entry
                : safePrevious.getActiveRegions().entrySet()){
            if(!currentRegions.containsKey(entry.getKey())){
                left.add(new RegionPresence(entry.getKey(), entry.getValue()));
            }
        }
        Collections.sort(left, LEAVE_ORDER);

        List<RegionPresence> entered = new ArrayList<>();
        for(Map.Entry<String, Integer> entry : currentRegions.entrySet()){
            if(!safePrevious.getActiveRegions().containsKey(entry.getKey())){
                entered.add(new RegionPresence(entry.getKey(), entry.getValue()));
            }
        }
        Collections.sort(entered, ENTER_ORDER);

        List<RegionCommandAction> actions = new ArrayList<>();
        appendActions(actions, left, safeProfiles, CommandPhase.LEAVE);
        appendActions(actions, entered, safeProfiles, CommandPhase.ENTER);
        return new CommandTransition(
                actions,
                CommandTransitionState.of(currentRegions)
        );
    }

    private void appendActions(List<RegionCommandAction> actions,
                               List<RegionPresence> regions,
                               Map<String, RegionCommandProfile> profiles,
                               CommandPhase phase){
        for(RegionPresence region : regions){
            RegionCommandProfile profile = profiles.get(region.getRegionKey());
            if(profile == null){
                continue;
            }
            List<String> commands = phase == CommandPhase.ENTER
                    ? profile.getEnterCommands()
                    : profile.getLeaveCommands();
            for(String command : commands){
                actions.add(new RegionCommandAction(
                        region.getRegionKey(), phase, command
                ));
            }
        }
    }

    private Map<String, Integer> normalizeCurrent(
            Collection<RegionPresence> current){
        Map<String, Integer> result = new LinkedHashMap<>();
        if(current == null){
            return result;
        }
        for(RegionPresence presence : current){
            if(presence == null){
                throw new IllegalArgumentException(
                        "current region presence must not contain null"
                );
            }
            if(result.put(presence.getRegionKey(), presence.getDepth()) != null){
                throw new IllegalArgumentException(
                        "duplicate current region key: " + presence.getRegionKey()
                );
            }
        }
        return result;
    }

    private Map<String, RegionCommandProfile> normalizeProfiles(
            Map<String, RegionCommandProfile> profiles){
        Map<String, RegionCommandProfile> result = new LinkedHashMap<>();
        if(profiles == null){
            return result;
        }
        for(Map.Entry<String, RegionCommandProfile> entry : profiles.entrySet()){
            String key = entry.getKey();
            if(key == null || key.trim().isEmpty()){
                throw new IllegalArgumentException("command profile key must not be blank");
            }
            if(entry.getValue() == null){
                throw new IllegalArgumentException(
                        "command profile must not be null: " + key
                );
            }
            String normalized = key.trim();
            if(result.put(normalized, entry.getValue()) != null){
                throw new IllegalArgumentException(
                        "duplicate normalized command profile key: " + normalized
                );
            }
        }
        return result;
    }
}
