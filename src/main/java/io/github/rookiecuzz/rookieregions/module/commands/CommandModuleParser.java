package io.github.rookiecuzz.rookieregions.module.commands;

import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Pure parser from JSON/YAML-style maps into an immutable command profile. */
public final class CommandModuleParser {
    public RegionCommandProfile parse(Map<String, ?> raw){
        if(raw == null || raw.isEmpty()){
            return RegionCommandProfile.empty();
        }
        return new RegionCommandProfile(
                parseCommands(raw.get("enter"), "commands.enter"),
                parseCommands(raw.get("leave"), "commands.leave"),
                parseBinding(raw.get("binding"))
        );
    }

    private List<String> parseCommands(Object value, String path){
        if(value == null){
            return Collections.emptyList();
        }
        if(!(value instanceof List<?>)){
            throw new IllegalArgumentException(path + " must be a list");
        }
        List<String> result = new ArrayList<>();
        List<?> source = (List<?>) value;
        for(int index = 0; index < source.size(); index++){
            Object command = source.get(index);
            if(!(command instanceof String)){
                throw new IllegalArgumentException(
                        path + "[" + index + "] must be a string"
                );
            }
            result.add((String) command);
        }
        return result;
    }

    private ModuleRegionBinding parseBinding(Object value) {
        if(value == null) {
            return ModuleRegionBinding.nativeSelf();
        }
        if(!(value instanceof Map<?, ?> raw)) {
            throw new IllegalArgumentException("commands.binding must be an object");
        }
        Object provider = raw.get("provider");
        Object region = raw.get("region");
        if(!(provider instanceof String providerId)
                || !(region instanceof String regionId)) {
            throw new IllegalArgumentException(
                    "commands.binding provider and region must be strings"
            );
        }
        return ModuleRegionBinding.toProvider(providerId, regionId);
    }
}
