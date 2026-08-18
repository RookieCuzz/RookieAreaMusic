package io.github.rookiecuzz.rookieregions.api;

/** Features that integrations can detect instead of parsing plugin versions. */
public enum ApiCapability {
    SNAPSHOT_QUERY,
    TYPED_FLAGS,
    PROTECTION_DECISIONS,
    ATOMIC_MUTATIONS,
    BUKKIT_EVENTS,
    CUSTOM_FLAG_REGISTRATION,
    CUSTOM_PROVIDER_REGISTRATION,
    MODULE_BINDINGS
}
