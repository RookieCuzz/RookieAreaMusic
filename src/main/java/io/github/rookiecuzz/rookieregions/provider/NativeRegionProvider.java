package io.github.rookiecuzz.rookieregions.provider;

import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

import java.util.Objects;

/** Read-only provider backed by RookieRegions' native snapshot container. */
public final class NativeRegionProvider implements RegionProvider {
    public static final String ID = RegionProviderIds.NATIVE;

    private final RegionContainer container;

    public NativeRegionProvider(RegionContainer container) {
        this.container = Objects.requireNonNull(
                container,
                "native region container cannot be null"
        );
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public RegionSnapshot snapshot() {
        return container.snapshot();
    }

    @Override
    public RegionQuery query() {
        return container.query();
    }
}
