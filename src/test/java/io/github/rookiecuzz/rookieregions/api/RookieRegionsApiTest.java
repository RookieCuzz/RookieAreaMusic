package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.provider.NativeRegionProvider;
import io.github.rookiecuzz.rookieregions.provider.UnavailableWorldGuardProvider;
import io.github.rookiecuzz.rookieregions.provider.WorldGuardProvider;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RookieRegionsApiTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000092"),
            "minecraft:api_test"
    );

    @Test
    void apiAndNativeProviderReadTheSamePublishedSnapshot(){
        RegionContainer container = new RegionContainer();
        NativeRegionProvider nativeProvider = new NativeRegionProvider(container);
        RookieRegionsApi api = new DefaultRookieRegionsApi(
                container,
                ProtectionFlags.REGISTRY,
                java.util.Map.of(
                        nativeProvider.id(), nativeProvider,
                        WorldGuardProvider.ID, UnavailableWorldGuardProvider.INSTANCE
                )
        );

        assertEquals(ApiVersion.CURRENT, api.version());
        assertTrue(api.supports(ApiCapability.SNAPSHOT_QUERY));
        assertSame(ProtectionFlags.REGISTRY, api.flagRegistry());
        assertEquals(NativeRegionProvider.ID, api.nativeProvider().id());
        assertTrue(api.nativeProvider().available());
        assertSame(container.snapshot(), api.snapshot());

        Region global = Region.builder(
                RegionKey.global(world),
                GlobalShape.INSTANCE
        ).build();
        Region local = Region.builder(
                        new RegionKey(world, "api-claim"),
                        new CuboidShape(0, 0, 0, 10, 10, 10)
                )
                .parent(global.key())
                .flag(ProtectionFlags.PVP, State.DENY)
                .build();
        container.recordPublication().compareAndPublish(
                0L,
                List.of(
                        io.github.rookiecuzz.rookieregions.runtime.RegionRecord.coreOnly(global),
                        io.github.rookiecuzz.rookieregions.runtime.RegionRecord.coreOnly(local)
                )
        ).orElseThrow();

        assertSame(api.snapshot(), api.nativeProvider().snapshot());
        assertEquals(1L, api.query().snapshot().revision());
        assertTrue(api.nativeProvider().regionsAt(world, 5, 5, 5)
                .containsLocal(local.key()));

        ProtectionDecision denied = api.protection().decide(
                world, 5, 5, 5, ProtectionFlags.PVP,
                Subject.player(UUID.randomUUID()), "pvp"
        );
        ProtectionDecision bypassed = api.protection().decide(
                world, 5, 5, 5, ProtectionFlags.PVP,
                new Subject(UUID.randomUUID(), Set.of(), Set.of(
                        "rookieregions.bypass.*"
                )),
                "pvp"
        );
        assertFalse(denied.allowed());
        assertTrue(denied.resolution().isPresent());
        assertTrue(bypassed.allowed());
        assertTrue(bypassed.bypassed());
        assertTrue(bypassed.resolution().isEmpty());
    }

    @Test
    void unavailableWorldGuardProviderHasAnEmptyReadOnlyView(){
        WorldGuardProvider provider = UnavailableWorldGuardProvider.INSTANCE;

        assertEquals(WorldGuardProvider.ID, provider.id());
        assertFalse(provider.available());
        assertEquals(0L, provider.snapshot().revision());
        assertTrue(provider.snapshot().graph().regions().isEmpty());
        assertTrue(provider.regionsAt(world, 0, 0, 0).localRegions().isEmpty());
    }
}
