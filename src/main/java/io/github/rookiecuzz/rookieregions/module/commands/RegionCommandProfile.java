package io.github.rookiecuzz.rookieregions.module.commands;

import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable physical enter/leave command attachment for one region. */
public final class RegionCommandProfile {
    private static final RegionCommandProfile EMPTY = new RegionCommandProfile(
            Collections.<String>emptyList(),
            Collections.<String>emptyList(),
            ModuleRegionBinding.nativeSelf()
    );

    private final List<String> enterCommands;
    private final List<String> leaveCommands;
    private final ModuleRegionBinding binding;

    public RegionCommandProfile(List<String> enterCommands,
                                List<String> leaveCommands) {
        this(enterCommands, leaveCommands, ModuleRegionBinding.nativeSelf());
    }

    public RegionCommandProfile(List<String> enterCommands,
                                List<String> leaveCommands,
                                ModuleRegionBinding binding) {
        this.enterCommands = normalizeCommands(enterCommands, "enter command");
        this.leaveCommands = normalizeCommands(leaveCommands, "leave command");
        this.binding = Objects.requireNonNull(
                binding,
                "commands module binding cannot be null"
        );
    }

    public static RegionCommandProfile empty(){
        return EMPTY;
    }

    public List<String> getEnterCommands() {
        return enterCommands;
    }

    public List<String> getLeaveCommands() {
        return leaveCommands;
    }

    public ModuleRegionBinding getBinding() {
        return binding;
    }

    public RegionCommandProfile withBinding(ModuleRegionBinding changed) {
        return new RegionCommandProfile(enterCommands, leaveCommands, changed);
    }

    public boolean isEmpty(){
        return enterCommands.isEmpty() && leaveCommands.isEmpty();
    }

    @Override
    public boolean equals(Object value) {
        if(this == value){
            return true;
        }
        if(!(value instanceof RegionCommandProfile)){
            return false;
        }
        RegionCommandProfile other = (RegionCommandProfile) value;
        return enterCommands.equals(other.enterCommands)
                && leaveCommands.equals(other.leaveCommands)
                && binding.equals(other.binding);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enterCommands, leaveCommands, binding);
    }

    private static List<String> normalizeCommands(List<String> source,
                                                  String label){
        if(source == null || source.isEmpty()){
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for(String command : source){
            if(command == null || command.trim().isEmpty()){
                throw new IllegalArgumentException(label + " must not be blank");
            }
            String normalized = command.trim();
            if(normalized.startsWith("/")){
                normalized = normalized.substring(1).trim();
            }
            if(normalized.isEmpty()){
                throw new IllegalArgumentException(label + " must not be blank");
            }
            result.add(normalized);
        }
        return Collections.unmodifiableList(result);
    }
}
