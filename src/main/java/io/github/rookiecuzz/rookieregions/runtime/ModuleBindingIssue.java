package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.RegionKey;

import java.util.Objects;

/** External provider resolution problem; native publication remains usable. */
public record ModuleBindingIssue(Code code,
                                 ModuleKind module,
                                 RegionKey profileRegion,
                                 String message) {
    public ModuleBindingIssue {
        Objects.requireNonNull(code, "binding issue code cannot be null");
        Objects.requireNonNull(module, "binding issue module cannot be null");
        Objects.requireNonNull(
                profileRegion,
                "binding issue profile region cannot be null"
        );
        if(message == null || message.isBlank()) {
            throw new IllegalArgumentException("binding issue message cannot be blank");
        }
    }

    public enum Code {
        UNKNOWN_PROVIDER,
        PROVIDER_UNAVAILABLE,
        TARGET_NOT_FOUND,
        TARGET_ALIAS
    }
}
