package io.github.rookiecuzz.rookieregions.api;

/** Semantic version of the public RookieRegions integration contract. */
public record ApiVersion(int major, int minor, int patch)
        implements Comparable<ApiVersion> {
    public static final ApiVersion CURRENT = new ApiVersion(2, 0, 0);

    public ApiVersion {
        if(major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("API version parts cannot be negative");
        }
    }

    public boolean isCompatibleWith(ApiVersion required) {
        if(required == null) {
            return false;
        }
        return major == required.major && compareTo(required) >= 0;
    }

    @Override
    public int compareTo(ApiVersion other) {
        int comparison = Integer.compare(major, other.major);
        if(comparison == 0) {
            comparison = Integer.compare(minor, other.minor);
        }
        return comparison == 0 ? Integer.compare(patch, other.patch) : comparison;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
