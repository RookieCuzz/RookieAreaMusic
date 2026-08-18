package io.github.rookiecuzz.rookieregions.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/** Canonical provider-local region reference; the profile region supplies the world. */
public record ProviderRegionReference(String providerId, String regionId) {
    private static final Pattern PROVIDER_ID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    public ProviderRegionReference {
        providerId = normalizeProviderId(providerId);
        regionId = normalizeRegionId(regionId);
    }

    public static String normalizeProviderId(String value) {
        if(value == null) {
            throw new IllegalArgumentException("provider ID cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if(!PROVIDER_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("invalid provider ID: " + value);
        }
        return normalized;
    }

    public static String normalizeRegionId(String value) {
        if(value == null) {
            throw new IllegalArgumentException("provider region ID cannot be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty()) {
            throw new IllegalArgumentException("provider region ID cannot be blank");
        }
        for(int index = 0; index < normalized.length(); index++) {
            if(Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException(
                        "provider region ID cannot contain control characters"
                );
            }
        }
        return normalized;
    }
}
