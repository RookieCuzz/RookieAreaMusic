package io.github.rookiecuzz.rookieareamusic.command;

import io.github.rookiecuzz.rookieareamusic.RookieAreaMusic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainTabCompleter implements TabCompleter {
    private final RookieAreaMusic plugin;

    public MainTabCompleter(RookieAreaMusic plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {
        if(!sender.hasPermission("rookieareamusic.admin")){
            return new ArrayList<>();
        }
        if(args.length == 1){
            return filterMatching(
                    Arrays.asList("music", "area", "reload", "help"),
                    args[0]
            );
        }
        if(args.length == 2){
            if("music".equals(args[0])){
                return filterMatching(Arrays.asList("add", "list", "del"), args[1]);
            }
            if("area".equals(args[0])){
                return filterMatching(
                        Arrays.asList("create", "edit", "show", "editor", "del", "list"),
                        args[1]
                );
            }
            return new ArrayList<>();
        }
        if(args.length == 3
                && "area".equals(args[0])
                && "editor".equals(args[1])){
            return filterMatching(Arrays.asList("finish", "cancel"), args[2]);
        }
        if(args.length == 3 && usesRegionPath(args)){
            return filterMatching(plugin.getConfigManager().getAreas().keySet(), args[2]);
        }
        if(args.length == 4 && usesRegionPath(args)){
            Map<String, io.github.rookiecuzz.rookieareamusic.config.AreaDto> worldAreas =
                    plugin.getConfigManager().getAreas().get(args[2]);
            if(worldAreas == null){
                return new ArrayList<>();
            }
            List<String> areaIds = worldAreas.values().stream()
                    .map(io.github.rookiecuzz.rookieareamusic.config.AreaDto::getAreaId)
                    .collect(Collectors.toList());
            return filterMatching(areaIds, args[3]);
        }
        return new ArrayList<>();
    }

    private boolean usesRegionPath(String[] args){
        if("music".equals(args[0])){
            return "add".equals(args[1])
                    || "del".equals(args[1])
                    || "list".equals(args[1]);
        }
        return "area".equals(args[0])
                && ("edit".equals(args[1])
                || "show".equals(args[1])
                || "del".equals(args[1]));
    }

    private List<String> filterMatching(Collection<String> candidates, String prefix){
        List<String> result = new ArrayList<>();
        for(String candidate : candidates){
            if(candidate.startsWith(prefix)){
                result.add(candidate);
            }
        }
        return result;
    }
}
